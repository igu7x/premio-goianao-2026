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
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import java.util.List;
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

@DisplayName("Emissao do certificado de magistrado (feature 005)")
class EmissaoMagistradoIT extends TesteDeIntegracao {

    @Test
    @DisplayName("CA-1: as opcoes refletem as unidades reconhecidas, cada uma com seu selo")
    void opcoesRefletemReconhecimentos() throws Exception {
        Edicao edicao = edicaoComLayouts(2080);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael Siqueira Bittencourt",
                List.of(new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.OURO),
                        new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.BRONZE)));
        edicoes.tornarVigente(edicoes.publicar(edicao.getId()).getId());

        mvc.perform(get("/api/magistrado/certificados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].selo").value("OURO"))
                .andExpect(jsonPath("$[1].selo").value("BRONZE"))
                .andExpect(jsonPath("$[0].layoutDisponivel").value(true))
                .andExpect(jsonPath("$[0].jaEmitido").value(false));
    }

    @Test
    @DisplayName("CA-2: o PDF sai com nome, unidade e codigo de validacao")
    void emiteComOsDadosCorretos() throws Exception {
        Edicao edicao = cenarioVigente(2081);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);

        MvcResult resultado = mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Codigo-Validacao"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("certificado-goianao-2081-magistrado-ouro")))
                .andReturn();

        String codigo = resultado.getResponse().getHeader("X-Codigo-Validacao");
        try (PDDocument pdf = Loader.loadPDF(resultado.getResponse().getContentAsByteArray())) {
            String texto = new PDFTextStripper().getText(pdf);
            Assertions.assertThat(texto)
                    .contains("Rafael Siqueira Bittencourt")
                    .contains(codigo);
        }
    }

    @Test
    @DisplayName("CA-2b: o nome impresso vem do cadastro da edicao, nao do SSO")
    void nomeVemDoCadastro() throws Exception {
        Edicao edicao = cenarioVigente(2082);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);

        // O token traz outra grafia; o cadastro e que manda (005/RF-4).
        String tokenComOutroNome = token(EMAIL_MAGISTRADO, "R. S. BITTENCOURT");

        MvcResult resultado = mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComOutroNome)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isOk())
                .andReturn();

        try (PDDocument pdf = Loader.loadPDF(resultado.getResponse().getContentAsByteArray())) {
            String texto = new PDFTextStripper().getText(pdf);
            Assertions.assertThat(texto)
                    .contains("Rafael Siqueira Bittencourt")
                    .doesNotContain("R. S. BITTENCOURT");
        }
    }

    @Test
    @DisplayName("CA-3: emitir por unidade nao reconhecida e negado")
    void unidadeAlheia() throws Exception {
        Edicao edicao = cenarioVigente(2083);
        UnidadeJudiciaria outra = unidade(UNIDADE_C);

        mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), outra.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-4: sem layout para o selo/tipo, a emissao informa indisponibilidade")
    void semLayout() throws Exception {
        Edicao edicao = novaEdicao(2084);
        criarTodosOsLayouts(edicao);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());
        edicoes.tornarVigente(edicao.getId());

        // Remove justamente a combinacao usada por este magistrado.
        layouts.findByEdicaoIdAndSeloAndTipo(edicao.getId(), Selo.OURO, TipoCertificado.MAGISTRADO)
                .ifPresent(layouts::delete);
        layouts.flush();

        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("layout")));

        mvc.perform(get("/api/magistrado/certificados")
                        .param("edicaoId", edicao.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(jsonPath("$[0].layoutDisponivel").value(false));
    }

    @Test
    @DisplayName("CA-5: edicao em rascunho bloqueia a emissao")
    void rascunhoBloqueia() throws Exception {
        Edicao edicao = novaEdicao(2085);
        criarTodosOsLayouts(edicao);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);

        mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("rascunho")));
    }

    @Test
    @DisplayName("CA-6 e RF-10: reemitir gera novo PDF e mantem o mesmo codigo")
    void reemissaoMantemCodigo() throws Exception {
        Edicao edicao = cenarioVigente(2086);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        String corpoRequisicao = corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()));

        String primeiro = mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON).content(corpoRequisicao))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Codigo-Validacao");

        String segundo = mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON).content(corpoRequisicao))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Codigo-Validacao");

        Assertions.assertThat(segundo).isEqualTo(primeiro);

        mvc.perform(get("/api/magistrado/certificados")
                        .param("edicaoId", edicao.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(jsonPath("$[0].jaEmitido").value(true))
                .andExpect(jsonPath("$[0].totalEmissoes").value(2));
    }

    @Test
    @DisplayName("CA-1b: edicao anterior publicada continua disponivel para reemissao")
    void reemissaoDeEdicaoAnterior() throws Exception {
        Edicao anterior = cenarioVigente(2087);
        edicaoVigente(2088);

        mvc.perform(get("/api/magistrado/certificados/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ano").value(2087));

        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(anterior.getId(), unidade.getId()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("2087")));
    }

    /** Edicao publicada e vigente, com o magistrado reconhecido em UNIDADE_A/Ouro. */
    private Edicao cenarioVigente(int ano) {
        Edicao edicao = edicaoComLayouts(ano);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael Siqueira Bittencourt",
                UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());
        return edicoes.tornarVigente(edicao.getId());
    }
}
