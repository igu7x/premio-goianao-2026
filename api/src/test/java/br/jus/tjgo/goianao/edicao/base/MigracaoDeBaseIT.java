package br.jus.tjgo.goianao.edicao.base;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A subida que leva o banco de antes da feature 011 para uma base por edicao
 * (011/RF-12, CA-8).
 *
 * <p>As tabelas de antes da feature 011 existem, vazias, no esquema compartilhado
 * do banco de teste. O teste as povoa como estaria um banco de homologacao com
 * duas edicoes sem base propria e sobe a base de novo, conferindo que cada
 * edicao recebeu o que era dela e que o original ficou onde estava.
 */
@DisplayName("Migracao do banco anterior para a base por edicao (feature 011)")
class MigracaoDeBaseIT extends TesteDeIntegracao {


    @Autowired private JdbcTemplate jdbc;
    @Autowired private BaseDaEdicao base;
    @Autowired private UsuarioRepository usuarios;

    @Test
    @DisplayName("CA-8: cada edicao recebe os seus dados e os compartilhados; o original fica intacto")
    void migraCadaEdicaoParaOSeuSchema() {
        montarBancoAnterior();

        base.preparar();

        // O que era compartilhado vai para as duas.
        for (String schema : List.of("edicao_2501", "edicao_2502")) {
            assertThat(contar(schema + ".usuario")).as(schema + " usuarios").isEqualTo(1);
            assertThat(contar(schema + ".usuario_papel")).as(schema + " papeis").isEqualTo(1);
            assertThat(contar(schema + ".unidade_judiciaria")).as(schema + " unidades").isEqualTo(1);
        }

        // O que era de uma edicao vai so para ela.
        assertThat(contar("edicao_2501.magistrado_reconhecido")).isEqualTo(1);
        assertThat(contar("edicao_2501.reconhecimento")).isEqualTo(1);
        assertThat(contar("edicao_2501.certificado_emitido")).isEqualTo(1);
        assertThat(contar("edicao_2502.magistrado_reconhecido")).isZero();
        assertThat(contar("edicao_2502.reconhecimento")).isZero();
        assertThat(contar("edicao_2502.certificado_emitido")).isZero();

        // O codigo emitido continua conferivel pela pagina publica.
        assertThat(jdbc.queryForObject(
                "SELECT e.ano FROM certificado_indice i JOIN edicao e ON e.id = i.edicao_id "
                        + "WHERE i.codigo_validacao = 'AAAA-BBBB-CCCC'", Integer.class))
                .isEqualTo(2501);

        // O original ficou onde estava, com o mesmo nome: e o que deixa a versao
        // anterior da aplicacao funcionar se for preciso voltar atras.
        assertThat(contar("public.usuario")).isEqualTo(1);
        assertThat(contar("public.certificado_emitido")).isEqualTo(1);

        // Uma segunda subida nao copia de novo: as edicoes ja tem base.
        base.preparar();
        assertThat(contar("edicao_2501.usuario")).isEqualTo(1);
        assertThat(contar("edicao_2501.certificado_emitido")).isEqualTo(1);

        // A numeracao continua depois dos ids migrados: cadastrar nao colide.
        Long novo = EdicaoCorrente.executarEm("edicao_2501", () -> usuarios.save(new Usuario(
                "novo@tjgo.example", "Novo", null, EnumSet.of(Papel.SERVIDOR))).getId());
        assertThat(novo).isGreaterThan(10L);
    }

    /** Estado de um banco de homologacao antes da 011: tudo em public, duas edicoes. */
    private void montarBancoAnterior() {
        jdbc.queryForList("SELECT schema_name FROM information_schema.schemata "
                        + "WHERE LOWER(schema_name) LIKE 'edicao\\_%' ESCAPE '\\'", String.class)
                .forEach(schema -> jdbc.execute("DROP SCHEMA " + schema + " CASCADE"));
        jdbc.update("DELETE FROM certificado_indice");
        jdbc.update("DELETE FROM edicao");

        LocalDateTime agora = LocalDateTime.now();
        jdbc.update("INSERT INTO edicao (id, ano, status, vigente, criado_em) "
                + "VALUES (901, 2501, 'PUBLICADA', FALSE, ?)", agora);
        jdbc.update("INSERT INTO edicao (id, ano, status, vigente, criado_em) "
                + "VALUES (902, 2502, 'RASCUNHO', FALSE, ?)", agora);

        jdbc.update("INSERT INTO usuario (id, nome, email, ativo, criado_em) "
                + "VALUES (10, 'Rafael', 'rafael.legado@tjgo.example', TRUE, ?)", agora);
        jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (10, 'MAGISTRADO')");
        jdbc.update("INSERT INTO unidade_judiciaria (id, nome, nome_canonico, ativo, "
                + "responsavel_id, criado_em) VALUES (20, 'Vara Legada', 'vara legada', TRUE, 10, ?)",
                agora);
        jdbc.update("INSERT INTO magistrado_reconhecido (id, edicao_id, email, nome, criado_em) "
                + "VALUES (30, 901, 'rafael.legado@tjgo.example', 'Rafael', ?)", agora);
        jdbc.update("INSERT INTO reconhecimento (id, magistrado_id, unidade_id, selo, criado_em) "
                + "VALUES (40, 30, 20, 'OURO', ?)", agora);
        jdbc.update("INSERT INTO certificado_emitido (id, edicao_id, tipo, email_emissor, "
                + "nome_emissor, unidade_id, selo, codigo_validacao, emitido_em, total_emissoes) "
                + "VALUES (50, 901, 'MAGISTRADO', 'rafael.legado@tjgo.example', 'Rafael', 20, "
                + "'OURO', 'AAAA-BBBB-CCCC', ?, 1)", agora);
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
    }

    /**
     * As tabelas antigas sao compartilhadas entre os testes: esvaziadas aqui para
     * o proximo teste — e a limpeza da base, que apaga as edicoes — nao esbarrar
     * nelas.
     */
    @AfterEach
    void esvaziarTabelasAntigas() {
        List.of("certificado_emitido", "servidor_habilitado", "arte_layout",
                        "layout_certificado", "reconhecimento", "magistrado_reconhecido",
                        "unidade_judiciaria", "administrador", "usuario_papel", "usuario")
                .forEach(t -> jdbc.update("DELETE FROM public." + t));
    }
}
