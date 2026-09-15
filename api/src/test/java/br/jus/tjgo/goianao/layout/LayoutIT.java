package br.jus.tjgo.goianao.layout;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.layout.dto.AreasEmLoteRequisicao;
import br.jus.tjgo.goianao.layout.dto.LayoutRequisicao;
import br.jus.tjgo.goianao.layout.dto.PreviewRequisicao;
import br.jus.tjgo.goianao.suporte.ArteDeTeste;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Configuracao de layouts (feature 003)")
class LayoutIT extends TesteDeIntegracao {

    @Test
    @DisplayName("CA-1: cadastra o layout Ouro/Servidor com arte e areas")
    void cadastraLayout() throws Exception {
        Edicao edicao = novaEdicao(2060);

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, false))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selo").value("OURO"))
                .andExpect(jsonPath("$.tipo").value("SERVIDOR"))
                .andExpect(jsonPath("$.imagemLargura").value(ArteDeTeste.LARGURA))
                .andExpect(jsonPath("$.areaNome.alinhamento").value("CENTRO"));
    }

    @Test
    @DisplayName("CA-2: combinacao repetida exige substituicao explicita")
    void combinacaoRepetida() throws Exception {
        Edicao edicao = novaEdicao(2061);
        criarLayout(edicao, Selo.OURO, TipoCertificado.SERVIDOR);

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, false))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isConflict());

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, true))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CA-3: o preview usa o mesmo motor da emissao e escreve os tres campos")
    void preview() throws Exception {
        Edicao edicao = novaEdicao(2062);
        LayoutCertificado layout = criarLayout(edicao, Selo.PRATA, TipoCertificado.MAGISTRADO);

        MvcResult resultado = mvc.perform(
                        post("/api/edicoes/" + edicao.getId() + "/layouts/" + layout.getId()
                                + "/preview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(new PreviewRequisicao(
                                        "Fulano de Tal", "Vara Unica de Teste", "TEST-0000-0001"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        try (PDDocument pdf = Loader.loadPDF(resultado.getResponse().getContentAsByteArray())) {
            String texto = new PDFTextStripper().getText(pdf);
            Assertions.assertThat(texto)
                    .contains("Fulano de Tal")
                    .contains("Vara Unica de Teste")
                    .contains("TEST-0000-0001");
        }
    }

    @Test
    @DisplayName("CA-4: a listagem aponta as combinacoes ainda sem layout")
    void pendencias() throws Exception {
        Edicao edicao = novaEdicao(2063);
        criarLayout(edicao, Selo.DIAMANTE, TipoCertificado.SERVIDOR);

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layouts.length()").value(1))
                .andExpect(jsonPath("$.pendencias.length()").value(7))
                .andExpect(jsonPath("$.pendencias", org.hamcrest.Matchers.hasItem(
                        "Diamante / Magistrado")))
                .andExpect(jsonPath("$.editavel").value(true));
    }

    @Test
    @DisplayName("CA-5: nao administrador nao configura layout")
    void naoAdminBloqueado() throws Exception {
        Edicao edicao = novaEdicao(2064);

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ESTRANHO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF-8: publicada e sem emissao, o layout ainda pode ser ajustado")
    void publicadaSemEmissaoContinuaEditavel() throws Exception {
        Edicao edicao = edicaoPublicada(2065);

        // O caso real: a arte definitiva chega depois da publicação. Travar aqui
        // não protegeria nada — não há certificado emitido do qual divergir.
        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, true))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(jsonPath("$.editavel").value(true));
    }

    /** Registra uma emissao direto no repositorio: o que interessa aqui e o
     *  efeito dela sobre a trava, nao o fluxo de emissao (coberto em 005/006). */
    private void registrarEmissao(Edicao edicao) {
        certificados.save(new br.jus.tjgo.goianao.certificado.CertificadoEmitido(
                edicao, TipoCertificado.MAGISTRADO, "10120230100", "Fulano de Teste",
                unidade("1ª Vara Cível da Comarca de Goiânia"), Selo.OURO, "TEST-0000-0001"));
    }

    @Test
    @DisplayName("RF-8: emitido um certificado, os layouts ficam travados para sempre")
    void travaNaPrimeiraEmissao() throws Exception {
        Edicao edicao = edicaoPublicada(2066);
        registrarEmissao(edicao);

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, true))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("travados")));

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(jsonPath("$.editavel").value(false));
    }

    @Test
    @DisplayName("arte em retrato e recusada no upload")
    void arteForaDoPadrao() throws Exception {
        Edicao edicao = novaEdicao(2066);

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(new MockMultipartFile("imagem", "arte.png",
                                MediaType.IMAGE_PNG_VALUE, ArteDeTeste.retrato()))
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, false))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("area que ultrapassa a arte e recusada")
    void areaForaDaArte() throws Exception {
        Edicao edicao = novaEdicao(2067);

        LayoutRequisicao requisicao = new LayoutRequisicao(
                Selo.OURO, TipoCertificado.SERVIDOR,
                new AreaTexto(3000, 1150, 2600, 150, Alinhamento.CENTRO),
                new AreaTexto(454, 1495, 2600, 110, Alinhamento.CENTRO),
                new AreaCodigo(520, 2230, 1000, 50, Alinhamento.ESQUERDA, null),
                false);

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                                corpo(requisicao).getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isUnprocessableEntity());
    }

    private MockMultipartFile arte() {
        return new MockMultipartFile("imagem", "arte.png", MediaType.IMAGE_PNG_VALUE,
                ArteDeTeste.valida());
    }

    private MockMultipartFile dados(Selo selo, TipoCertificado tipo, boolean substituir) {
        LayoutRequisicao requisicao = new LayoutRequisicao(selo, tipo,
                new AreaTexto(454, 1150, 2600, 150, Alinhamento.CENTRO),
                new AreaTexto(454, 1495, 2600, 110, Alinhamento.CENTRO),
                new AreaCodigo(520, 2230, 1000, 50, Alinhamento.ESQUERDA,
                        new AreaQr(280, 2080, 200)),
                substituir);
        return new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                corpo(requisicao).getBytes());
    }

    @Test
    @DisplayName("aplica as posicoes de uma vez em todos os layouts da edicao")
    void aplicaAreasEmTodos() throws Exception {
        Edicao edicao = novaEdicao(2067);
        criarTodosOsLayouts(edicao);

        String corpo = corpo(new AreasEmLoteRequisicao(
                new AreaTexto(100, 200, 900, 120, Alinhamento.ESQUERDA),
                new AreaTexto(100, 400, 900, 90, Alinhamento.ESQUERDA),
                new AreaCodigo(100, 700, 500, 40, Alinhamento.CENTRO, null)));

        mvc.perform(put("/api/edicoes/" + edicao.getId() + "/layouts/areas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutsAtualizados").value(8));

        // As oito passam a compartilhar exatamente a mesma posicao: e o ponto.
        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(jsonPath("$.layouts[0].areaNome.y").value(200))
                .andExpect(jsonPath("$.layouts[7].areaNome.y").value(200))
                .andExpect(jsonPath("$.layouts[7].areaCodigo.alinhamento").value("CENTRO"));
    }

    @Test
    @DisplayName("posicao que nao cabe na arte nao altera nenhum layout")
    void loteNaoAplicaParcialmente() throws Exception {
        Edicao edicao = novaEdicao(2068);
        criarTodosOsLayouts(edicao);

        String corpo = corpo(new AreasEmLoteRequisicao(
                new AreaTexto(100, 999_000, 900, 120, Alinhamento.ESQUERDA),
                new AreaTexto(100, 400, 900, 90, Alinhamento.ESQUERDA),
                new AreaCodigo(100, 700, 500, 40, Alinhamento.CENTRO, null)));

        mvc.perform(put("/api/edicoes/" + edicao.getId() + "/layouts/areas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("Nenhum layout foi alterado")));

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(jsonPath("$.layouts[0].areaNome.y").value(org.hamcrest.Matchers.not(999000)));
    }

    @Test
    @DisplayName("as artes padrao preenchem so o que falta, sem tocar no que ja existe")
    void aplicaArtesPadrao() throws Exception {
        Edicao edicao = novaEdicao(2069);
        LayoutCertificado jaConfigurado = criarLayout(edicao, Selo.OURO, TipoCertificado.SERVIDOR);
        String arteOriginal = jaConfigurado.getImagemRef();

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/layouts/padrao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criados").value(7))
                .andExpect(jsonPath("$.jaExistentes").value(1));

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(jsonPath("$.layouts.length()").value(8))
                .andExpect(jsonPath("$.pendencias.length()").value(0));

        // A arte que o administrador ja tinha subido continua sendo a dele: o
        // padrao preenche o vazio, nao substitui escolha de ninguem.
        Assertions.assertThat(layouts.findById(jaConfigurado.getId()).orElseThrow().getImagemRef())
                .isEqualTo(arteOriginal);

        // Rodar de novo nao duplica nem altera nada.
        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/layouts/padrao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criados").value(0))
                .andExpect(jsonPath("$.jaExistentes").value(8));
    }

    @Test
    @DisplayName("as artes padrao sao exclusivas do administrador")
    void artesPadraoExigemAdministrador() throws Exception {
        Edicao edicao = novaEdicao(2070);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/layouts/padrao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(status().isForbidden());
    }
}
