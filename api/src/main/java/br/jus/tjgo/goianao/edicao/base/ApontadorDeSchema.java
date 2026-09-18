package br.jus.tjgo.goianao.edicao.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Aponta uma conexao para a base de uma edicao (feature 011).
 *
 * <p>Dois comandos, e nao um, porque PostgreSQL e H2 separam coisas diferentes:
 *
 * <ul>
 *   <li>No PostgreSQL, {@code SET search_path} resolve tudo — o primeiro schema
 *       da lista e onde uma tabela nova e criada e onde uma consulta procura
 *       primeiro.</li>
 *   <li>No H2, {@code SET SCHEMA} decide onde o DDL cria, e
 *       {@code SET SCHEMA_SEARCH_PATH} decide onde a consulta procura. Definir
 *       so o segundo faz o {@code CREATE TABLE} da migracao cair no schema
 *       errado, calado — foi exatamente isso que aconteceu na primeira tentativa
 *       desta feature.</li>
 * </ul>
 *
 * <p>O schema compartilhado vai junto, ao fim do caminho de busca: e onde mora o
 * catalogo de edicoes, que precisa ser visivel de dentro de qualquer edicao.
 */
final class ApontadorDeSchema {

    private ApontadorDeSchema() {}

    /**
     * @param schemaDaEdicao para onde apontar; {@code null} volta ao compartilhado
     */
    static void apontar(Connection conexao, String schemaDaEdicao, String compartilhado)
            throws SQLException {
        String alvo = schemaDaEdicao == null
                ? compartilhado
                : NomeDeSchema.exigirSeguro(schemaDaEdicao);
        String caminho = alvo.equals(compartilhado) ? alvo : alvo + ", " + compartilhado;

        try (Statement comando = conexao.createStatement()) {
            if (ehH2(conexao)) {
                comando.execute("SET SCHEMA " + alvo);
                comando.execute("SET SCHEMA_SEARCH_PATH " + caminho);
            } else {
                comando.execute("SET search_path TO " + caminho);
            }
        }
    }

    static boolean ehH2(Connection conexao) throws SQLException {
        return "H2".equalsIgnoreCase(conexao.getMetaData().getDatabaseProductName());
    }
}
