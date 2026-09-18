package br.jus.tjgo.goianao.edicao.base;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * O mesmo banco, visto de dentro do schema de uma edicao (feature 011).
 *
 * <p>Existe para o Liquibase. As migracoes do projeto sao SQL puro — um
 * {@code CREATE TABLE usuario} sem qualificacao nenhuma —, e o
 * {@code defaultSchemaName} do Liquibase nao reescreve SQL cru: ele cuida das
 * tabelas de controle e dos changesets declarativos, mas o comando escrito a mao
 * vai para o banco como esta e cai no schema corrente da conexao.
 *
 * <p>Por isso quem decide o schema aqui e a conexao, e nao o Liquibase. O mesmo
 * arquivo de migracao serve, sem alteracao, a todas as edicoes.
 */
class DataSourceDaEdicao extends AbstractDataSource {

    private final DataSource origem;
    private final String schema;
    private final String compartilhado;

    DataSourceDaEdicao(DataSource origem, String schema, String compartilhado) {
        this.origem = origem;
        this.schema = NomeDeSchema.exigirSeguro(schema);
        this.compartilhado = NomeDeSchema.exigirSeguro(compartilhado);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return apontada(origem.getConnection());
    }

    @Override
    public Connection getConnection(String usuario, String senha) throws SQLException {
        return apontada(origem.getConnection(usuario, senha));
    }

    private Connection apontada(Connection conexao) throws SQLException {
        try {
            ApontadorDeSchema.apontar(conexao, schema, compartilhado);
            return conexao;
        } catch (SQLException e) {
            conexao.close();
            throw e;
        }
    }
}
