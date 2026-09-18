package br.jus.tjgo.goianao.edicao.base;

import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes.EdicaoNoCatalogo;
import jakarta.annotation.PostConstruct;
import java.time.Year;
import java.util.List;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Garante que cada edicao tenha a sua base pronta (011/RF-1, RF-13).
 *
 * <p>Roda na subida, <b>antes</b> do {@code EntityManagerFactory}: com
 * {@code ddl-auto: validate}, o Hibernate confere o mapeamento contra o banco
 * assim que sobe, e as tabelas do dominio agora vivem dentro de um schema de
 * edicao que pode ainda nao existir. A dependencia esta declarada em
 * {@link ConfiguracaoDaSubida}.
 *
 * <p>O que ela faz e idempotente, e proposital: a mesma sequencia roda na
 * primeira subida de um banco vazio, na subida que migra o banco de homologacao
 * e em toda subida seguinte, sem efeito nenhum quando nao ha o que fazer. Duas
 * replicas subindo juntas nao concorrem — o Liquibase toma o lock de cada
 * schema antes de comecar.
 */
// O changelog do esquema compartilhado precisa ter rodado: e ele que cria o
// catalogo de edicoes e a coluna que diz onde fica a base de cada uma.
@Component
@DependsOn("liquibase")
public class BaseDaEdicao {

    private static final Logger log = LoggerFactory.getLogger(BaseDaEdicao.class);

    private static final String CHANGELOG = "classpath:db/edicao/changelog-edicao.yaml";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ResourceLoader recursos;
    private final CatalogoDeEdicoes catalogo;
    private final MigracaoDosDadosAnteriores migracao;
    private final String compartilhado;
    private final String contextos;

    public BaseDaEdicao(DataSource dataSource,
                        ResourceLoader recursos,
                        CatalogoDeEdicoes catalogo,
                        MigracaoDosDadosAnteriores migracao,
                        ConfiguracaoBasePorEdicao.SchemaCompartilhado compartilhado,
                        @Value("${spring.liquibase.contexts:}") String contextos) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.recursos = recursos;
        this.catalogo = catalogo;
        this.migracao = migracao;
        this.compartilhado = compartilhado.nome();
        this.contextos = contextos;
    }

    /** Ponto de entrada da subida: catalogo, schemas, migracao e edicao padrao. */
    @PostConstruct
    public void preparar() {
        List<EdicaoNoCatalogo> edicoes = catalogo.todas();
        if (edicoes.isEmpty()) {
            int ano = Year.now().getValue();
            log.info("Nenhuma edição cadastrada: criando a de {} para o sistema ter uma base.", ano);
            catalogo.criarEdicaoDoAno(ano);
            edicoes = catalogo.todas();
        }

        // Quem chegou sem base e quem recebe a copia do banco anterior. Edicao que
        // ja tinha base nao e tocada: copiar de novo por cima duplicaria tudo.
        List<Long> semBase = edicoes.stream()
                .filter(edicao -> edicao.schemaDados() == null)
                .map(EdicaoNoCatalogo::id)
                .toList();

        for (EdicaoNoCatalogo edicao : edicoes) {
            prepararSchemaDe(edicao);
        }

        migracao.migrarSeNecessario(catalogo.todas().stream()
                .filter(edicao -> semBase.contains(edicao.id()))
                .toList());

        String padrao = catalogo.padrao()
                .map(EdicaoNoCatalogo::schemaDados)
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhuma edição disponível depois de preparar a base."));
        EdicaoCorrente.definirPadrao(padrao);
        log.info("Base por edição pronta. Edição padrão: {}.", padrao);
    }

    /** Cria o schema da edicao, se preciso, e aplica nele o changelog da edicao. */
    public String prepararSchemaDe(EdicaoNoCatalogo edicao) {
        String schema = edicao.schemaDados() != null
                ? NomeDeSchema.exigirSeguro(edicao.schemaDados())
                : NomeDeSchema.paraAno(edicao.ano());

        prepararSchema(schema);

        if (edicao.schemaDados() == null) {
            catalogo.gravarSchema(edicao.id(), schema);
        }
        return schema;
    }

    /**
     * Cria o schema, se ainda nao existir, e aplica nele o changelog da edicao.
     *
     * <p>Idempotente de ponta a ponta: o {@code IF NOT EXISTS} e o proprio
     * controle do Liquibase garantem que chamar duas vezes nao faz diferenca.
     * E o que permite usa-la tanto na subida quanto na criacao de uma edicao.
     */
    public void prepararSchema(String schema) {
        criarSchema(schema);
        aplicarChangelog(schema);
    }

    private void criarSchema(String schema) {
        // Identificador nao se parametriza em SQL; a seguranca vem de exigirSeguro.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + NomeDeSchema.exigirSeguro(schema));
    }

    /**
     * Aplica o changelog da edicao dentro do schema dela.
     *
     * <p>O controle do Liquibase (databasechangelog) tambem vai para la: cada
     * edicao tem o proprio historico de migracoes, e uma criada daqui a tres
     * anos recebe o esquema em vigor naquele momento sem tocar nas anteriores.
     */
    private void aplicarChangelog(String schema) {
        SpringLiquibase liquibase = new SpringLiquibase();
        // A conexao e que decide o schema, nao o defaultSchemaName: as migracoes
        // sao SQL puro, e o Liquibase nao reescreve um CREATE TABLE escrito a mao.
        liquibase.setDataSource(new DataSourceDaEdicao(dataSource, schema, compartilhado));
        liquibase.setResourceLoader(recursos);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setDefaultSchema(schema);
        liquibase.setLiquibaseSchema(schema);
        if (contextos != null && !contextos.isBlank()) {
            liquibase.setContexts(contextos);
        }
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Falha ao preparar a base da edição no schema " + schema + ".", e);
        }
    }
}
