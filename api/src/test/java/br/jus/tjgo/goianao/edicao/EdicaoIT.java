package br.jus.tjgo.goianao.edicao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.dto.CriarEdicaoRequisicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Gestao de edicoes (feature 002)")
class EdicaoIT extends TesteDeIntegracao {

    @Test
    @DisplayName("CA-1: a edicao criada nasce em RASCUNHO")
    void criaEmRascunho() throws Exception {
        mvc.perform(post("/api/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new CriarEdicaoRequisicao(2031, "Edicao de teste"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ano").value(2031))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.vigente").value(false))
                .andExpect(jsonPath("$.emitivel").value(false));
    }

    @Test
    @DisplayName("CA-2: ano duplicado e rejeitado")
    void anoDuplicado() throws Exception {
        novaEdicao(2032);

        mvc.perform(post("/api/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new CriarEdicaoRequisicao(2032, "Outra"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("conflito"));
    }

    @Test
    @DisplayName("CA-3: tornar vigente desmarca a anterior, que continua publicada")
    void trocaDeVigente() throws Exception {
        Edicao anterior = edicaoVigente(2033);
        Edicao nova = edicaoPublicada(2034);

        mvc.perform(post("/api/edicoes/" + nova.getId() + "/vigente")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(true));

        Edicao anteriorRecarregada = edicoes.buscar(anterior.getId());
        assertThat(anteriorRecarregada.isVigente()).isFalse();
        // Continua publicada: e o que sustenta a reemissao (002/CA-4).
        assertThat(anteriorRecarregada.estaPublicada()).isTrue();
    }

    @Test
    @DisplayName("no maximo uma edicao vigente por vez")
    void vigenteUnica() {
        edicaoVigente(2035);
        edicaoVigente(2036);

        assertThat(edicoesRepo.findAll().stream().filter(Edicao::isVigente)).hasSize(1);
    }

    @Test
    @DisplayName("CA-5: edicao em rascunho nao e emitivel")
    void rascunhoNaoEmite() {
        Edicao rascunho = novaEdicao(2037);

        assertThat(rascunho.estaPublicada()).isFalse();
        assertThat(edicoes.listarPublicadas()).doesNotContain(rascunho);
    }

    @Test
    @DisplayName("CA-6: nao administrador nao cria nem publica")
    void naoAdminBloqueado() throws Exception {
        Edicao edicao = novaEdicao(2038);

        mvc.perform(post("/api/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ESTRANHO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new CriarEdicaoRequisicao(2039, null))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/publicar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ESTRANHO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem token, a API responde 401")
    void semTokenNaoAcessa() throws Exception {
        mvc.perform(get("/api/edicoes")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA-7: publicar sem os 8 layouts e bloqueado e lista as pendencias")
    void publicarSemLayouts() throws Exception {
        Edicao edicao = novaEdicao(2040);
        criarLayout(edicao, Selo.OURO, TipoCertificado.MAGISTRADO);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/publicar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalhes.length()").value(7))
                .andExpect(jsonPath("$.detalhes[0]").value("Bronze / Magistrado"));
    }

    @Test
    @DisplayName("com os 8 layouts, a publicacao passa")
    void publicarComLayouts() throws Exception {
        Edicao edicao = edicaoComLayouts(2041);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/publicar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADA"))
                .andExpect(jsonPath("$.emitivel").value(true));
    }

    @Test
    @DisplayName("somente edicao publicada pode virar vigente")
    void rascunhoNaoViraVigente() throws Exception {
        Edicao rascunho = novaEdicao(2042);

        mvc.perform(post("/api/edicoes/" + rascunho.getId() + "/vigente")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isConflict());
    }
}
