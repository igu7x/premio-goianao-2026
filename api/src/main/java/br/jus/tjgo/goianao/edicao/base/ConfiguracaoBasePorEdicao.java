package br.jus.tjgo.goianao.edicao.base;

import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga o isolamento por edicao ao Hibernate (feature 011).
 *
 * <p>Basta o provedor de conexoes e o resolvedor estarem nas propriedades: a
 * partir dai o Hibernate abre toda sessao ja apontada para o schema da edicao
 * corrente, e nenhuma consulta do projeto precisa saber que isso existe.
 */
@Configuration
public class ConfiguracaoBasePorEdicao {

    /**
     * Onde ficam o catalogo de edicoes e o indice de verificacao publica.
     *
     * <p>Reaproveita a propriedade que o Liquibase ja usa — no OpenShift ela vem
     * de {@code OPENSHIFT_POSTGRESQL_DB_SCHEMA}. Uma variavel de ambiente nova
     * so para isto seria um pedido a mais a infraestrutura, e diria a mesma
     * coisa (011/RNF-2).
     */
    @Bean
    public SchemaCompartilhado schemaCompartilhado(
            @Value("${spring.liquibase.default-schema:public}") String nome) {
        return new SchemaCompartilhado(NomeDeSchema.exigirSeguro(nome));
    }

    @Bean
    public ConexaoPorEdicao conexaoPorEdicao(DataSource dataSource, SchemaCompartilhado compartilhado) {
        return new ConexaoPorEdicao(dataSource, compartilhado.nome());
    }

    @Bean
    public ResolvedorDeEdicao resolvedorDeEdicao() {
        return new ResolvedorDeEdicao();
    }

    @Bean
    public HibernatePropertiesCustomizer isolamentoPorEdicao(ConexaoPorEdicao conexoes,
                                                            ResolvedorDeEdicao resolvedor) {
        return propriedades -> {
            propriedades.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, conexoes);
            propriedades.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolvedor);
            propriedades.put(AvailableSettings.HBM2DDL_FILTER_PROVIDER,
                    new ValidacaoForaDoCatalogo());
        };
    }

    /** O nome do schema compartilhado, como valor injetavel. */
    public record SchemaCompartilhado(String nome) {}
}
