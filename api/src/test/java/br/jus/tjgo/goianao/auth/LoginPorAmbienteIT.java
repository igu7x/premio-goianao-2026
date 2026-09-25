package br.jus.tjgo.goianao.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Cada ambiente abre as portas de entrada que precisa, e so essas.
 *
 * <p>Producao vale so SSO. Homologacao ganha o login por e-mail e senha, porque
 * nem todos tem conta no Keycloak de teste. O login mockado nao sai do
 * desenvolvimento: ele <b>dispensa credencial</b>, e
 * {@code /api/auth/usuarios-mock} publica as identidades que podem ser
 * assumidas — uma delas administrador.
 *
 * <p>As duas propriedades chegam desligadas por padrao. Este teste as desliga
 * explicitamente porque o perfil de teste as liga, para exercitar os dois
 * caminhos no resto da suite.
 */
@DisplayName("Portas de entrada por ambiente")
@TestPropertySource(properties = {
        "goianao.login.senha=false",
        "goianao.login.mock=false",
})
class LoginPorAmbienteIT extends TesteDeIntegracao {

    @Test
    @DisplayName("login mockado desligado responde 404 — nao 403, que confirmaria a existencia")
    void mockDesligadoNaoExiste() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencial\":\"" + EMAIL_ADMIN + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("o catalogo de identidades de teste some junto")
    void catalogoDeIdentidadesSome() throws Exception {
        mvc.perform(get("/api/auth/usuarios-mock")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("login por senha desligado responde 404")
    void senhaDesligadaNaoExiste() throws Exception {
        mvc.perform(post("/api/auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguem@tjgo.jus.br\",\"senha\":\"qualquer\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a situacao informa ao frontend o que desenhar")
    void situacaoDescreveOAmbiente() throws Exception {
        mvc.perform(get("/api/auth/situacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senha").value(false))
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.sso").value(false))
                // O rodape da tela avisa "dados de RH mockados" a partir daqui:
                // em teste o RH e o mockado, como em desenvolvimento.
                .andExpect(jsonPath("$.rhReal").value(false));
    }
}
