package br.jus.tjgo.goianao.edicao.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.dto.CriarEdicaoRequisicao;
import br.jus.tjgo.goianao.magistrado.MagistradoRepository;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeRepository;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * O teste central da feature 011: cada edicao so enxerga a propria base.
 *
 * <p>Monta duas edicoes com dados diferentes e confere, pelos dois caminhos que
 * importam — o repositorio, que e o que o dominio usa, e a API, que e o que a
 * tela usa —, que nada de uma aparece na outra.
 */
@DisplayName("Base independente por edicao (feature 011)")
class BasePorEdicaoIT extends TesteDeIntegracao {

    private static final String EMAIL_SUPER = "super.base@tjgo.example";

    @Autowired private UsuarioRepository usuarios;
    @Autowired private UnidadeRepository unidadesRepo;
    @Autowired private MagistradoRepository magistradosRepo;

    /** Edicao com unidade, usuarios e magistrado — uma base "cheia". */
    private Edicao edicaoPovoada(int ano) {
        Edicao edicao = novaEdicao(ano);
        usuarios.save(new Usuario(EMAIL_SUPER, "Super da Base", null,
                EnumSet.of(Papel.SUPERADMIN)));
        usuarios.save(new Usuario(EMAIL_SERVIDOR, "Marcos", null, EnumSet.of(Papel.SERVIDOR)));
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        return edicao;
    }

    @Test
    @DisplayName("CA-1: a edicao criada nasce vazia, so com os superadministradores")
    void edicaoNovaNasceVazia() throws Exception {
        edicaoPovoada(2081);
        atuandoComo(EMAIL_SUPER);

        String resposta = mvc.perform(post("/api/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new CriarEdicaoRequisicao(2082, "Edicao seguinte"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long novaId = json.readTree(resposta).get("id").asLong();
        Edicao nova = edicoesRepo.findById(novaId).orElseThrow();

        naEdicao(nova, () -> {
            assertThat(unidadesRepo.count()).as("unidades").isZero();
            assertThat(magistradosRepo.count()).as("magistrados").isZero();
            assertThat(usuarios.findAll())
                    .as("so quem administra atravessa para a edicao nova")
                    .extracting(Usuario::getEmail)
                    .containsExactly(EMAIL_SUPER);
        });
    }

    @Test
    @DisplayName("CA-2: o que se faz numa edicao nao alcanca a outra")
    void edicoesNaoSeMisturam() {
        Edicao a = edicaoPovoada(2303);
        Edicao b = novaEdicao(2304);

        // Na edicao B, uma unidade nova e um magistrado na mesma unidade de A.
        cadastrarMagistrado(b.getId(), EMAIL_MAGISTRADO_2, "Helena", UNIDADE_C, Selo.PRATA);
        unidade(UNIDADE_A);

        naEdicao(a, () -> {
            assertThat(unidadesRepo.findAll()).extracting(u -> u.getNome())
                    .containsExactly(UNIDADE_A);
            assertThat(magistradosRepo.findAll()).extracting(m -> m.getEmail())
                    .containsExactly(EMAIL_MAGISTRADO);
            assertThat(usuarios.existsByEmailIgnoreCase(EMAIL_SERVIDOR)).isTrue();
        });
        naEdicao(b, () -> {
            assertThat(unidadesRepo.findAll()).extracting(u -> u.getNome())
                    .containsExactlyInAnyOrder(UNIDADE_A, UNIDADE_C);
            assertThat(magistradosRepo.findAll()).extracting(m -> m.getEmail())
                    .containsExactly(EMAIL_MAGISTRADO_2);
            assertThat(usuarios.existsByEmailIgnoreCase(EMAIL_SERVIDOR))
                    .as("o usuario de A nao existe em B").isFalse();
        });
    }

    @Test
    @DisplayName("a API responde com a base da edicao da sessao")
    void apiLeABaseDaSessao() throws Exception {
        Edicao a = edicaoPovoada(2305);
        Edicao b = novaEdicao(2306);

        usando(a);
        mvc.perform(get("/api/edicoes/" + a.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        usando(b);
        mvc.perform(get("/api/edicoes/" + b.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("rota que fala de outra edicao que nao a da sessao e recusada")
    void rotaDeOutraEdicaoERecusada() throws Exception {
        Edicao a = edicaoPovoada(2307);
        novaEdicao(2308); // a sessao do teste passa a ser a de 2308

        mvc.perform(get("/api/edicoes/" + a.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("outra edição")));
    }

    @Test
    @DisplayName("publicar confere os layouts na base da edicao publicada, nao na da sessao")
    void publicarConfereABaseCerta() {
        Edicao comLayouts = edicaoComLayouts(2309);
        novaEdicao(2310); // sessao em outra edicao, sem layout nenhum

        Edicao publicada = edicoes.publicar(comLayouts.getId());

        assertThat(publicada.estaPublicada()).isTrue();
    }
}
