package br.jus.tjgo.goianao.comum.erro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Requisição malformada é erro <b>do cliente</b>, não do servidor.
 *
 * <p>Antes destes testes, qualquer entrada torta — id não numérico, JSON quebrado,
 * selo inexistente, rota errada — caía no tratador genérico e voltava como 500,
 * dizendo que o servidor falhou quando quem errou foi a requisição. Além de
 * enganar quem consome a API, enchia o log de stack trace por erro de digitação.
 */
@DisplayName("Tratamento de requisições malformadas")
class TratamentoDeErrosIT extends TesteDeIntegracao {

    @Test
    @DisplayName("id de rota não numérico → 400")
    void idNaoNumerico() throws Exception {
        mvc.perform(get("/api/edicoes/abc").header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("dados_invalidos"));
    }

    @Test
    @DisplayName("JSON quebrado → 400")
    void jsonQuebrado() throws Exception {
        mvc.perform(post("/api/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ano\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("dados_invalidos"));
    }

    @Test
    @DisplayName("corpo ausente → 400")
    void corpoAusente() throws Exception {
        mvc.perform(post("/api/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("valor fora do enum → 400 dizendo o campo e os valores aceitos")
    void enumInvalido() throws Exception {
        Edicao edicao = novaEdicao(2140);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cpf":"20450670252","nome":"Rafael","reconhecimentos":
                                 [{"unidadeNome":"1ª Vara Cível da Comarca de Goiânia","selo":"PLATINA"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("selo")))
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("DIAMANTE")));
    }

    @Test
    @DisplayName("método não aceito → 405")
    void metodoNaoAceito() throws Exception {
        mvc.perform(delete("/api/edicoes").header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("content-type não suportado → 415")
    void tipoNaoSuportado() throws Exception {
        mvc.perform(post("/api/edicoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("oi"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("parâmetro de query com tipo errado → 400")
    void queryComTipoErrado() throws Exception {
        Edicao edicao = edicaoComLayouts(2141);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO, "Rafael", UNIDADE_A,
                br.jus.tjgo.goianao.comum.Selo.OURO);

        mvc.perform(get("/api/magistrado/certificados")
                        .param("edicaoId", "abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("multipart sem a parte obrigatória → 400")
    void multipartSemParte() throws Exception {
        Edicao edicao = novaEdicao(2142);

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/layouts")
                        .file(new MockMultipartFile("outro", "x", "text/plain", "x".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("todo erro sai no mesmo formato de corpo")
    void formatoUniforme() throws Exception {
        mvc.perform(get("/api/edicoes/abc").header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.erro").exists())
                .andExpect(jsonPath("$.mensagem").exists())
                .andExpect(jsonPath("$.detalhes").isArray())
                .andExpect(jsonPath("$.momento").exists());
    }
}
