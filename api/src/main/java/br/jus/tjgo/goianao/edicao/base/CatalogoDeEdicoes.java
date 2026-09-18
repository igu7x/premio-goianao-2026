package br.jus.tjgo.goianao.edicao.base;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * O catalogo de edicoes, lido por JDBC (feature 011).
 *
 * <p>A mesma informacao esta na entidade {@code Edicao}, e e por ela que o
 * dominio trabalha. Este acesso existe para os dois momentos em que o JPA nao
 * serve: na subida, antes de o {@code EntityManagerFactory} existir — e ele so
 * pode ser criado depois que os schemas estiverem no lugar —, e no filtro da
 * requisicao, que precisa saber para qual schema apontar antes de abrir
 * qualquer sessao.
 *
 * <p>A traducao de id para schema fica em memoria porque nao muda: o schema de
 * uma edicao e definido quando ela nasce e nunca mais e alterado.
 */
@Component
public class CatalogoDeEdicoes {

    private final JdbcTemplate jdbc;
    private final String compartilhado;
    private final Map<Long, String> schemaPorEdicao = new ConcurrentHashMap<>();

    public CatalogoDeEdicoes(DataSource dataSource,
                             ConfiguracaoBasePorEdicao.SchemaCompartilhado compartilhado) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.compartilhado = compartilhado.nome();
    }

    /** Uma linha do catalogo. */
    public record EdicaoNoCatalogo(Long id, int ano, String schemaDados, boolean vigente) {}

    public List<EdicaoNoCatalogo> todas() {
        return jdbc.query(
                "SELECT id, ano, schema_dados, vigente FROM " + compartilhado
                        + ".edicao ORDER BY ano DESC",
                (rs, linha) -> new EdicaoNoCatalogo(
                        rs.getLong("id"), rs.getInt("ano"),
                        rs.getString("schema_dados"), rs.getBoolean("vigente")));
    }

    public Optional<EdicaoNoCatalogo> porId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return todas().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public Optional<EdicaoNoCatalogo> vigente() {
        return todas().stream().filter(EdicaoNoCatalogo::vigente).findFirst();
    }

    /** A vigente ou, na falta dela, a mais recente: a base padrao do sistema. */
    public Optional<EdicaoNoCatalogo> padrao() {
        List<EdicaoNoCatalogo> todas = todas();
        return todas.stream().filter(EdicaoNoCatalogo::vigente).findFirst()
                .or(() -> todas.stream().findFirst());
    }

    /** O schema de uma edicao, sem ir ao banco depois da primeira vez. */
    public Optional<String> schemaDe(Long edicaoId) {
        if (edicaoId == null) {
            return Optional.empty();
        }
        String conhecido = schemaPorEdicao.get(edicaoId);
        if (conhecido != null) {
            return Optional.of(conhecido);
        }
        return porId(edicaoId)
                .map(EdicaoNoCatalogo::schemaDados)
                .filter(NomeDeSchema::ehSeguro)
                .map(schema -> {
                    schemaPorEdicao.put(edicaoId, schema);
                    return schema;
                });
    }

    public void gravarSchema(Long edicaoId, String schema) {
        jdbc.update("UPDATE " + compartilhado + ".edicao SET schema_dados = ? WHERE id = ?",
                NomeDeSchema.exigirSeguro(schema), edicaoId);
        schemaPorEdicao.put(edicaoId, schema);
    }

    /**
     * Cria a primeira edicao, quando o sistema sobe sem nenhuma (011/RF-13).
     *
     * <p>Nasce em rascunho, como qualquer outra: publicar e tornar vigente
     * continuam sendo atos do administrador. O que ela resolve e o problema de
     * partida — sem edicao nao ha base, e sem base ninguem entra para criar a
     * primeira edicao.
     */
    public Long criarEdicaoDoAno(int ano) {
        jdbc.update("INSERT INTO " + compartilhado
                        + ".edicao (ano, descricao, status, vigente, criado_em) "
                        + "VALUES (?, ?, 'RASCUNHO', FALSE, ?)",
                ano, "Edição " + ano, LocalDateTime.now());
        return jdbc.queryForObject(
                "SELECT id FROM " + compartilhado + ".edicao WHERE ano = ?", Long.class, ano);
    }

    public String compartilhado() {
        return compartilhado;
    }
}
