package br.jus.tjgo.goianao.edicao.base;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

/**
 * Tira o catalogo de edicoes da validacao de esquema do Hibernate (feature 011).
 *
 * <p>Na subida, {@code ddl-auto: validate} confere se cada tabela mapeada existe
 * no banco. A conferencia usa os metadados do <b>schema corrente</b> da conexao —
 * o da edicao padrao —, e nao o caminho de busca. Todas as tabelas do dominio
 * estao la e passam; {@code edicao} nao, porque ela e a unica que mora no
 * esquema compartilhado, visivel em tempo de consulta pelo caminho de busca mas
 * invisivel para essa inspecao.
 *
 * <p>Sem este filtro, a aplicacao nao subiria — reclamando de uma tabela que
 * existe. A alternativa seria desligar a validacao inteira, o que custaria a
 * protecao sobre as outras dez tabelas. O catalogo continua conferido de outro
 * jeito: ele e criado e versionado pelo changelog do esquema compartilhado, que
 * roda antes de tudo.
 */
public class ValidacaoForaDoCatalogo implements SchemaFilterProvider {

    private static final String CATALOGO = "edicao";

    private static final SchemaFilter SEM_O_CATALOGO = new SchemaFilter() {
        @Override
        public boolean includeNamespace(Namespace namespace) {
            return true;
        }

        @Override
        public boolean includeTable(Table tabela) {
            return !CATALOGO.equalsIgnoreCase(tabela.getName());
        }

        @Override
        public boolean includeSequence(Sequence sequencia) {
            return true;
        }
    };

    @Override
    public SchemaFilter getCreateFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getDropFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getTruncatorFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return SEM_O_CATALOGO;
    }
}
