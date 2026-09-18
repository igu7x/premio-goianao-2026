package br.jus.tjgo.goianao.edicao.base;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Entrega, para cada edicao, uma conexao apontada para o schema dela (011/RF-1).
 *
 * <p>E aqui que o isolamento acontece. Todo o resto do sistema — repositorios,
 * servicos, consultas — continua escrito como se houvesse uma base so; quem
 * decide de qual base ele esta falando e esta classe, ao ajustar o
 * {@code search_path} da conexao antes de entrega-la ao Hibernate. Uma consulta
 * que esqueca de filtrar por edicao nao encontra o ano errado: ele nao esta ao
 * alcance da conexao.
 *
 * <p>O caminho de busca tem dois schemas, nesta ordem: o da edicao e o
 * compartilhado. O segundo existe por causa da tabela {@code edicao} — o
 * catalogo e um so, e precisa ser visivel de dentro de qualquer edicao. Como
 * nenhuma tabela do dominio existe nos dois (as antigas viraram {@code legado_*}
 * na migracao), a ordem nunca decide empate: ou a tabela e da edicao, ou e do
 * catalogo.
 *
 * <p>A conexao volta ao pool com o caminho restaurado para o compartilhado. Sem
 * isso, quem pegasse a conexao por fora do Hibernate — um {@code JdbcTemplate},
 * o Liquibase — herdaria o schema da ultima requisicao que passou por ela.
 */
public class ConexaoPorEdicao implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final String compartilhado;

    public ConexaoPorEdicao(DataSource dataSource, String compartilhado) {
        this.dataSource = dataSource;
        this.compartilhado = NomeDeSchema.exigirSeguro(compartilhado);
    }

    /**
     * Conexao sem edicao declarada.
     *
     * <p>Quem a pede e o proprio Hibernate, na subida, para validar o mapeamento
     * contra o banco — e a validacao precisa enxergar as tabelas do dominio, que
     * agora so existem dentro de um schema de edicao. Por isso ela vai para a
     * edicao padrao (a vigente), e nao para o compartilhado.
     */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return getConnection(EdicaoCorrente.padrao());
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        releaseConnection(null, connection);
    }

    @Override
    public Connection getConnection(String schemaDaEdicao) throws SQLException {
        Connection conexao = dataSource.getConnection();
        apontar(conexao, schemaDaEdicao);
        return conexao;
    }

    @Override
    public void releaseConnection(String schemaDaEdicao, Connection conexao) throws SQLException {
        try {
            apontar(conexao, null);
        } finally {
            conexao.close();
        }
    }

    /** Aponta a conexao para a edicao; {@code null} volta ao compartilhado. */
    private void apontar(Connection conexao, String schemaDaEdicao) throws SQLException {
        ApontadorDeSchema.apontar(conexao, schemaDaEdicao, compartilhado);
    }

    /**
     * Nao. A liberacao agressiva devolveria a conexao ao pool entre duas
     * operacoes da mesma transacao, e a seguinte poderia vir de outra conexao —
     * apontada para outro schema no meio do caminho.
     */
    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> tipo) {
        return tipo.isAssignableFrom(getClass()) || tipo.isAssignableFrom(DataSource.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> tipo) {
        if (tipo.isAssignableFrom(getClass())) {
            return (T) this;
        }
        if (tipo.isAssignableFrom(DataSource.class)) {
            return (T) dataSource;
        }
        throw new UnsupportedOperationException("Não é possível desembrulhar como " + tipo);
    }
}
