package br.jus.tjgo.goianao.layout;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.Edicao;
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isConflict());

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, true))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
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
                                .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ESTRANHO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF-8: publicada a edicao, os layouts ficam travados")
    void travaAoPublicar() throws Exception {
        Edicao edicao = edicaoPublicada(2065);

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(arte())
                        .file(dados(Selo.OURO, TipoCertificado.SERVIDOR, true))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("travados")));

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/layouts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
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
}
