package br.jus.tjgo.goianao.publico;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.certificado.EmissaoService;
import br.jus.tjgo.goianao.certificado.dto.EmitirRequisicao;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Validacao publica de certificado (feature 007)")
class VerificacaoPublicaIT extends TesteDeIntegracao {

    @Autowired private EmissaoService emissao;

    @Test
    @DisplayName("CA-1 e CA-5: codigo valido devolve os dados, e nenhum CPF aparece")
    void codigoValido() throws Exception {
        String codigo = emitirCertificado(2100);

        mvc.perform(get("/api/public/certificados/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.nome").value("Rafael Siqueira Bittencourt"))
                .andExpect(jsonPath("$.unidade").value(UNIDADE_A))
                .andExpect(jsonPath("$.edicaoAno").value(2100))
                .andExpect(jsonPath("$.selo").value("OURO"))
                .andExpect(jsonPath("$.tipo").value("MAGISTRADO"))
                .andExpect(jsonPath("$.emitidoEm").exists())
                // Nenhum campo de CPF, em nenhuma forma (007/RNF-2).
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(CPF_MAGISTRADO))));
    }

    @Test
    @DisplayName("a verificacao nao exige autenticacao")
    void semLogin() throws Exception {
        String codigo = emitirCertificado(2101);

        // Sem cabecalho Authorization de proposito.
        mvc.perform(get("/api/public/certificados/" + codigo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA-2: codigo inexistente responde 404 uniforme, sem vazar nada")
    void codigoInexistente() throws Exception {
        mvc.perform(get("/api/public/certificados/ZZZZ-9999-YYYY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andExpect(jsonPath("$.unidade").doesNotExist());
    }

    @Test
    @DisplayName("aceita o codigo digitado sem hifens ou em minusculas")
    void codigoTolerante() throws Exception {
        String codigo = emitirCertificado(2102);

        mvc.perform(get("/api/public/certificados/" + codigo.replace("-", "").toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo));
    }

    @Test
    @DisplayName("CA-3: o QR aponta para a pagina publica com o codigo")
    void urlDoQr() {
        String url = emissao.urlDeVerificacao("47RR-CTCH-321N");

        Assertions.assertThat(url)
                .isEqualTo("https://goianao.tjgo.jus.br/verificar/47RR-CTCH-321N");
    }

    @Test
    @DisplayName("CA-4: reemitir nao invalida — o mesmo codigo continua valendo")
    void reemissaoNaoInvalida() throws Exception {
        Edicao edicao = cenario(2103);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        String requisicao = corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()));

        String codigo = mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON).content(requisicao))
                .andReturn().getResponse().getHeader("X-Codigo-Validacao");

        mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON).content(requisicao))
                .andExpect(status().isOk());

        mvc.perform(get("/api/public/certificados/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.nome").value("Rafael Siqueira Bittencourt"));
    }

    private String emitirCertificado(int ano) throws Exception {
        Edicao edicao = cenario(ano);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);

        return mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Codigo-Validacao");
    }

    private Edicao cenario(int ano) {
        Edicao edicao = edicaoComLayouts(ano);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO, "Rafael Siqueira Bittencourt",
                UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());
        return edicoes.tornarVigente(edicao.getId());
    }
}
