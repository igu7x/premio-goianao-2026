package br.jus.tjgo.goianao.certificado;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.certificado.dto.EmitirRequisicao;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Emissao do certificado de servidor (feature 006)")
class EmissaoServidorIT extends TesteDeIntegracao {

    @Test
    @DisplayName("CA-1: unidade com Bronze e Ouro emite pelo maior selo")
    void aplicaMaiorSelo() throws Exception {
        Edicao edicao = edicaoComLayouts(2090);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.BRONZE);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO_2, "Helena", UNIDADE_A, Selo.OURO);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        habilitarServidor(edicao.getId(), unidade.getId(), EMAIL_SERVIDOR, "Marcos de Paula");
        publicarEVigorar(edicao);

        mvc.perform(get("/api/servidor/certificados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].selo").value("OURO"));

        mvc.perform(post("/api/servidor/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("servidor-ouro")));
    }

    @Test
    @DisplayName("CA-2: quem nao esta na lista nao ve opcoes e tem a emissao negada")
    void naoHabilitado() throws Exception {
        Edicao edicao = cenarioComServidor(2091);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);

        mvc.perform(get("/api/servidor/certificados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ESTRANHO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/servidor/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ESTRANHO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-3 e CA-3b: o PDF traz o nome do SSO, a unidade e o codigo")
    void nomeVemDoSso() throws Exception {
        Edicao edicao = cenarioComServidor(2092);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);

        // A lista foi semeada com outra grafia; o SSO e que fornece o texto.
        String tokenSso = token(EMAIL_SERVIDOR, "Marcos Vinicius de Paula");

        MvcResult resultado = mvc.perform(post("/api/servidor/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isOk())
                .andReturn();

        String codigo = resultado.getResponse().getHeader("X-Codigo-Validacao");
        try (PDDocument pdf = Loader.loadPDF(resultado.getResponse().getContentAsByteArray())) {
            String texto = new PDFTextStripper().getText(pdf);
            Assertions.assertThat(texto)
                    .contains("Marcos Vinicius de Paula")
                    .doesNotContain("M. V. PAULA")
                    .contains(codigo);
        }
    }

    @Test
    @DisplayName("CA-4: habilitado em duas unidades, ve uma opcao por unidade")
    void umaOpcaoPorUnidade() throws Exception {
        Edicao edicao = edicaoComLayouts(2093);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO_2, "Helena", UNIDADE_C, Selo.DIAMANTE);
        habilitarServidor(edicao.getId(), unidade(UNIDADE_A).getId(), EMAIL_SERVIDOR, "Marcos");
        habilitarServidor(edicao.getId(), unidade(UNIDADE_C).getId(), EMAIL_SERVIDOR, "Marcos");
        publicarEVigorar(edicao);

        mvc.perform(get("/api/servidor/certificados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].selo").value("OURO"))
                .andExpect(jsonPath("$[1].selo").value("DIAMANTE"));
    }

    @Test
    @DisplayName("CA-5: sem layout para o maior selo, a emissao informa indisponibilidade")
    void semLayout() throws Exception {
        Edicao edicao = cenarioComServidor(2094);
        layouts.findByEdicaoIdAndSeloAndTipo(edicao.getId(), Selo.OURO, TipoCertificado.SERVIDOR)
                .ifPresent(layouts::delete);
        layouts.flush();

        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        mvc.perform(post("/api/servidor/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CA-6: edicao em rascunho bloqueia a emissao")
    void rascunhoBloqueia() throws Exception {
        Edicao edicao = edicaoComLayouts(2095);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        habilitarServidor(edicao.getId(), unidade.getId(), EMAIL_SERVIDOR, "Marcos");

        mvc.perform(post("/api/servidor/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CA-7: reemite edicao anterior usando a lista e o selo daquela epoca")
    void reemissaoDeEdicaoAnterior() throws Exception {
        Edicao anterior = cenarioComServidor(2096);

        // Nova edicao vigente, e o servidor nao esta habilitado nela.
        Edicao nova = edicaoComLayouts(2097);
        cadastrarMagistrado(nova.getId(), EMAIL_MAGISTRADO_2, "Helena", UNIDADE_C, Selo.PRATA);
        publicarEVigorar(nova);

        mvc.perform(get("/api/servidor/certificados/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ano").value(2096));

        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        mvc.perform(post("/api/servidor/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(anterior.getId(), unidade.getId()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("2096")));
    }

    @Test
    @DisplayName("removido da lista, o servidor deixa de conseguir emitir")
    void remocaoDaListaBloqueia() throws Exception {
        Edicao edicao = cenarioComServidor(2098);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);

        atuandoComo(EMAIL_ADMIN);
        Long servidorId = servidores.listar(edicao.getId(), unidade.getId()).stream()
                .filter(s -> s.getEmail().equals(EMAIL_SERVIDOR))
                .findFirst().orElseThrow().getId();
        servidores.remover(edicao.getId(), unidade.getId(), servidorId);

        mvc.perform(post("/api/servidor/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isForbidden());
    }

    /** Edicao vigente, UNIDADE_A com Ouro e o servidor habilitado nela. */
    private Edicao cenarioComServidor(int ano) {
        Edicao edicao = edicaoComLayouts(ano);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        habilitarServidor(edicao.getId(), unidade(UNIDADE_A).getId(), EMAIL_SERVIDOR, "M. V. PAULA");
        return publicarEVigorar(edicao);
    }

    private Edicao publicarEVigorar(Edicao edicao) {
        edicoes.publicar(edicao.getId());
        return edicoes.tornarVigente(edicao.getId());
    }
}
