package br.jus.tjgo.goianao.edicao.base;

import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes.EdicaoNoCatalogo;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Leva para dentro do schema de cada edicao os dados que hoje estao no esquema
 * compartilhado (011/RF-12).
 *
 * <p>Roda para cada edicao que ainda nao tinha base — {@code schema_dados} nulo
 * no inicio da subida. Na primeira subida da feature 011 sobre homologacao, sao
 * todas; depois, so a edicao que o sistema cria para si num banco novo. Edicao
 * criada pela tela ja nasce com base e nunca passa por aqui.
 *
 * <p>O que e hoje compartilhado — usuarios, papeis, administradores, unidades e
 * artes — vai para <b>todas</b> as edicoes: ate aqui essas linhas valiam para
 * todas, e tirar isso de alguma delas seria perder dado que estava em uso. O que
 * ja era por edicao vai so para a sua.
 *
 * <p><b>As tabelas originais ficam intactas.</b> A primeira versao as renomeava
 * para {@code legado_*}; mudou porque, em homologacao, ninguem do projeto roda
 * comando no banco. Com o original no lugar, a versao anterior da aplicacao
 * continua funcionando sobre ele — durante a troca de pods e, se for preciso
 * voltar atras, depois dela: reimplantar a versao anterior basta.
 *
 * <p>O preco e que as tabelas antigas continuam no caminho de busca, depois do
 * schema da edicao. Nunca sao alcancadas, porque o schema da edicao e criado
 * completo pelo changelog dela; so seriam se uma tabela faltasse la.
 */
@Component
public class MigracaoDosDadosAnteriores {

    private static final Logger log = LoggerFactory.getLogger(MigracaoDosDadosAnteriores.class);


    /** Tabelas cuja identidade precisa ser reposicionada depois da copia de ids. */
    private static final List<String> COM_IDENTIDADE = List.of(
            "usuario", "unidade_judiciaria", "magistrado_reconhecido", "reconhecimento",
            "layout_certificado", "servidor_habilitado", "certificado_emitido");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final String compartilhado;

    public MigracaoDosDadosAnteriores(DataSource dataSource,
                                      ConfiguracaoBasePorEdicao.SchemaCompartilhado compartilhado) {
        this.jdbc = new JdbcTemplate(dataSource);
        // Gerenciador proprio: o do JPA depende do EntityManagerFactory, que so
        // pode ser criado depois que esta migracao terminar.
        this.transacao = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.compartilhado = compartilhado.nome();
    }

    /**
     * @param edicoesSemBase as edicoes que chegaram a esta subida sem base propria
     */
    public void migrarSeNecessario(List<EdicaoNoCatalogo> edicoesSemBase) {
        if (edicoesSemBase.isEmpty() || !haTabelasAnteriores()) {
            return;
        }
        log.info("Copiando a base compartilhada para o schema de cada edição ({} edições).",
                edicoesSemBase.size());
        transacao.executeWithoutResult(status -> {
            for (EdicaoNoCatalogo edicao : edicoesSemBase) {
                migrar(edicao);
                preencherIndiceDeVerificacao(edicao.id());
            }
        });
        log.info("Migração concluída. As tabelas originais em {} ficaram intactas.",
                compartilhado);
    }

    /** O banco veio de antes da feature 011: as tabelas do dominio estao no compartilhado. */
    private boolean haTabelasAnteriores() {
        Integer quantas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = 'usuario'",
                Integer.class, compartilhado);
        return quantas != null && quantas > 0;
    }

    private void migrar(EdicaoNoCatalogo edicao) {
        String destino = NomeDeSchema.exigirSeguro(edicao.schemaDados());
        Long id = edicao.id();

        // Compartilhadas ate aqui: vao inteiras para cada edicao.
        copiar(destino, "usuario",
                "id, cpf, nome, email, senha_hash, unidade_lotacao, area_atuacao, matricula, "
                        + "login_ad, ativo, criado_em, atualizado_em", null);
        copiar(destino, "usuario_papel", "usuario_id, papel", null);
        copiar(destino, "administrador", "email, cpf, nome, criado_em", null);
        copiar(destino, "unidade_judiciaria",
                "id, nome, nome_canonico, codigo_siedos, comarca, ativo, responsavel_id, criado_em",
                null);

        // Ja eram por edicao: cada uma leva o que e seu.
        copiar(destino, "magistrado_reconhecido",
                "id, edicao_id, email, cpf, nome, criado_em, atualizado_em",
                "edicao_id = " + id);
        copiar(destino, "reconhecimento", "id, magistrado_id, unidade_id, selo, criado_em",
                "magistrado_id IN (SELECT m.id FROM " + compartilhado
                        + ".magistrado_reconhecido m WHERE m.edicao_id = " + id + ")");
        copiar(destino, "layout_certificado",
                "id, edicao_id, selo, tipo, imagem_ref, imagem_largura, imagem_altura, "
                        + "area_nome, area_unidade, area_codigo, criado_em, atualizado_em",
                "edicao_id = " + id);
        // A arte segue o layout que a usa. As que nao pertencem a layout nenhum
        // sao as artes padrao do premio, e essas vao para todas as edicoes.
        copiar(destino, "arte_layout", "referencia, conteudo, extensao, tamanho, criado_em",
                "referencia IN (SELECT l.imagem_ref FROM " + compartilhado
                        + ".layout_certificado l WHERE l.edicao_id = " + id + ") "
                        + "OR NOT EXISTS (SELECT 1 FROM " + compartilhado
                        + ".layout_certificado l2 WHERE l2.imagem_ref = arte_layout.referencia)");
        copiar(destino, "servidor_habilitado",
                "id, edicao_id, unidade_id, email, cpf, matricula, nome, origem, ativo, "
                        + "criado_por, criado_em, atualizado_por, atualizado_em",
                "edicao_id = " + id);
        copiar(destino, "certificado_emitido",
                "id, edicao_id, tipo, email_emissor, cpf_emissor, nome_emissor, unidade_id, selo, "
                        + "codigo_validacao, emitido_em, reemitido_em, total_emissoes",
                "edicao_id = " + id);

        COM_IDENTIDADE.forEach(tabela -> reposicionarIdentidade(destino, tabela));
        log.info("Edição {} migrada para {}.", edicao.ano(), destino);
    }

    private void copiar(String destino, String tabela, String colunas, String filtro) {
        String sql = "INSERT INTO " + destino + "." + tabela + " (" + colunas + ") "
                + "SELECT " + colunas + " FROM " + compartilhado + "." + tabela
                + (filtro == null ? "" : " WHERE " + filtro);
        int linhas = jdbc.update(sql);
        log.debug("{}.{}: {} linha(s).", destino, tabela, linhas);
    }

    /**
     * Os ids vieram copiados, mas a coluna de identidade do schema novo continua
     * no 1: sem isto, o proximo cadastro colidiria com uma linha migrada.
     */
    private void reposicionarIdentidade(String destino, String tabela) {
        Long proximo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM " + destino + "." + tabela, Long.class);
        jdbc.execute("ALTER TABLE " + destino + "." + tabela
                + " ALTER COLUMN id RESTART WITH " + proximo);
    }

    /** O indice que a verificacao publica usa para achar a edicao de um codigo. */
    private void preencherIndiceDeVerificacao(Long edicaoId) {
        int linhas = jdbc.update(
                "INSERT INTO " + compartilhado + ".certificado_indice "
                        + "(codigo_validacao, edicao_id, criado_em) "
                        + "SELECT c.codigo_validacao, c.edicao_id, c.emitido_em FROM "
                        + compartilhado + ".certificado_emitido c "
                        + "WHERE c.edicao_id = ? AND NOT EXISTS (SELECT 1 FROM "
                        + compartilhado + ".certificado_indice i "
                        + "WHERE i.codigo_validacao = c.codigo_validacao)", edicaoId);
        log.info("Índice de verificação pública: {} certificado(s) da edição {}.",
                linhas, edicaoId);
    }
}
