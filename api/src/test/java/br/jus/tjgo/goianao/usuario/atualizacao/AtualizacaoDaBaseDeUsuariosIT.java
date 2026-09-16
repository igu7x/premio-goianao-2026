package br.jus.tjgo.goianao.usuario.atualizacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import br.jus.tjgo.goianao.integracao.egesp.UnidadeEgesp;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Atualizacao da base de usuarios pelo RH, contra o RH mockado.
 *
 * <p>O que estes testes protegem: a base inteira vira usuarios com a lotacao
 * certa, rodar de novo nao duplica ninguem, e quem ja tinha papel de
 * administrador continua administrador.
 */
@DisplayName("Atualizacao da base de usuarios pelo RH")
class AtualizacaoDaBaseDeUsuariosIT extends TesteDeIntegracao {

    private static final String EMAIL_SUPER = "super.atualizacao@tjgo.example";

    @Autowired private UsuarioRepository usuarios;
    @Autowired private EgespClient egesp;
    @Autowired private AtualizacaoDaBaseDeUsuarios atualizacao;

    @BeforeEach
    void criarSuperadministrador() {
        if (usuarios.findByEmailIgnoreCase(EMAIL_SUPER).isEmpty()) {
            usuarios.save(new Usuario(EMAIL_SUPER, "Super da Atualizacao", null,
                    Set.of(Papel.SUPERADMIN)));
        }
    }

    @Test
    @DisplayName("a base completa vira usuarios, cada um lotado na sua unidade")
    void baseCompleta() throws Exception {
        disparar("COMPLETA").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EM_ANDAMENTO"));
        esperarTerminar();

        mvc.perform(get("/api/usuarios/atualizacao-rh")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCLUIDA"))
                .andExpect(jsonPath("$.unidadesTotal").value(egesp.organogramaCompleto().size()))
                .andExpect(jsonPath("$.unidadesComFalha").value(0));

        // Cada pessoa do RH existe como usuario, lotada na unidade de onde veio.
        UnidadeEgesp unidade = egesp.organogramaCompleto().get(0);
        for (ServidorEgesp pessoa : egesp.servidoresPorCodigo(unidade.codigo())) {
            assertThat(usuarios.findByEmailIgnoreCase(pessoa.email()))
                    .as("%s precisa existir depois da atualizacao", pessoa.email())
                    .isPresent()
                    .get()
                    .satisfies(u -> assertThat(u.getUnidadeLotacao()).isEqualTo(unidade.nome()));
        }
    }

    @Test
    @DisplayName("rodar de novo nao duplica: quem ja existe e so atualizado")
    void idempotente() throws Exception {
        disparar("COMPLETA").andExpect(status().isAccepted());
        esperarTerminar();
        long depoisDaPrimeira = usuarios.count();

        disparar("COMPLETA").andExpect(status().isAccepted());
        esperarTerminar();

        assertThat(usuarios.count()).isEqualTo(depoisDaPrimeira);
        assertThat(atualizacao.situacao().criados()).isZero();
        assertThat(atualizacao.situacao().atualizados()).isPositive();
    }

    /**
     * O RH sabe onde a pessoa trabalha, nao o que ela pode fazer no premio: um
     * administrador que tambem esta lotado numa vara nao pode ser rebaixado.
     */
    @Test
    @DisplayName("nao mexe no papel de quem ja estava cadastrado")
    void preservaPapeis() throws Exception {
        UnidadeEgesp unidade = egesp.organogramaCompleto().get(0);
        ServidorEgesp pessoa = egesp.servidoresPorCodigo(unidade.codigo()).get(0);
        usuarios.findByEmailIgnoreCase(pessoa.email()).ifPresentOrElse(
                u -> { },
                () -> usuarios.save(new Usuario(pessoa.email(), pessoa.nome(), null,
                        Set.of(Papel.ADMINISTRADOR))));

        disparar("COMPLETA").andExpect(status().isAccepted());
        esperarTerminar();

        assertThat(usuarios.findByEmailIgnoreCase(pessoa.email()).orElseThrow().getPapeis())
                .contains(Papel.ADMINISTRADOR);
    }

    @Test
    @DisplayName("a atualizacao e exclusiva do superadministrador")
    void exigeSuperadmin() throws Exception {
        mvc.perform(post("/api/usuarios/atualizacao-rh")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("escopo", "TJGO"))))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions disparar(String escopo)
            throws Exception {
        return mvc.perform(post("/api/usuarios/atualizacao-rh")
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("escopo", escopo))));
    }

    private void esperarTerminar() {
        await().atMost(Duration.ofSeconds(30)).until(() -> !atualizacao.emAndamento());
    }
}
