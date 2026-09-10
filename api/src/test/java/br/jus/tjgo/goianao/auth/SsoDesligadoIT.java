package br.jus.tjgo.goianao.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comportamento enquanto o client do Keycloak nao existe.
 *
 * E o estado em que o sistema vai subir na primeira vez, entao precisa ser
 * previsivel: sem configuracao, o SSO se declara desligado e recusa o fluxo,
 * em vez de a aplicacao nao iniciar ou de o usuario ser levado a um Keycloak
 * inexistente.
 */
@DisplayName("SSO desligado (sem configuracao do Keycloak)")
class SsoDesligadoIT extends TesteDeIntegracao {

    @Test
    @DisplayName("a situacao informa que o SSO nao esta disponivel")
    void situacao() throws Exception {
        mvc.perform(get("/api/auth/sso/situacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(false));
    }

    @Test
    @DisplayName("iniciar o login devolve 503, nao redireciona para lugar nenhum")
    void loginIndisponivel() throws Exception {
        mvc.perform(get("/api/auth/sso/login"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("o callback tambem recusa, mesmo com codigo")
    void callbackIndisponivel() throws Exception {
        mvc.perform(get("/api/auth/sso/callback").param("code", "qualquer"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("nao ha URL de logout a oferecer")
    void semLogout() throws Exception {
        mvc.perform(get("/api/auth/sso/logout-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    @Test
    @DisplayName("o login mockado continua funcionando enquanto o SSO nao entra")
    void mockContinuaAtivo() throws Exception {
        mvc.perform(get("/api/auth/usuarios-mock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").isNotEmpty());
    }
}
