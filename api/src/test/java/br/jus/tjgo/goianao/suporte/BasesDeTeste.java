package br.jus.tjgo.goianao.suporte;

import br.jus.tjgo.goianao.edicao.base.BaseDaEdicao;
import br.jus.tjgo.goianao.edicao.base.EdicaoCorrente;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Devolve o banco de teste ao estado de um sistema recem-instalado.
 *
 * <p>Substitui o rollback que os testes de integracao usavam antes da feature
 * 011. Com uma base por edicao, o rollback deixou de bastar: os schemas criados
 * durante o teste sao estrutura, e estrutura nao volta atras num rollback. Aqui
 * eles sao derrubados e reconstruidos — mais caro que um rollback, e ainda assim
 * na casa dos milissegundos em H2 na memoria.
 *
 * <p>Depois de {@link #recomecar()}, o sistema esta como sobe pela primeira vez:
 * catalogo vazio, uma edicao do ano corrente e a base dela pronta.
 */
@Component
public class BasesDeTeste {

    private final JdbcTemplate jdbc;
    private final BaseDaEdicao base;

    public BasesDeTeste(DataSource dataSource, BaseDaEdicao base) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.base = base;
    }

    public void recomecar() {
        EdicaoCorrente.limpar();

        List<String> schemas = jdbc.queryForList(
                "SELECT schema_name FROM information_schema.schemata "
                        + "WHERE LOWER(schema_name) LIKE 'edicao\\_%' ESCAPE '\\'",
                String.class);
        for (String schema : schemas) {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }

        jdbc.update("DELETE FROM certificado_indice");
        jdbc.update("DELETE FROM edicao");

        base.preparar();
    }
}
