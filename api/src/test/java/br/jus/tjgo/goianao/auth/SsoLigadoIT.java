package br.jus.tjgo.goianao.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import jakarta.servlet.http.Cookie;
import java.security.MessageDigest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

/**
 * O que da para verificar do fluxo OIDC sem um Keycloak de verdade: a montagem
 * da URL de autorizacao, o tratamento de erro devolvido pelo provedor e o
 * transporte do destino pos-login.
 *
 * A troca do codigo por token exige o servidor e fica para a homologacao — mas
 * essas tres partes sao justamente as que se erra escrevendo e so se descobre
 * no ambiente.
 */
@DisplayName("SSO configurado (feature 001, substituicao do mock)")
@TestPropertySource(properties = {
        // Com /auth no fim, como no Keycloak do TJGO (anterior a v17).
        "goianao.sso.url=https://sso.exemplo.jus.br/auth",
        "goianao.sso.realm=tjgo",
        "goianao.sso.client-id=premio-goianao",
        "goianao.sso.client-secret=segredo-de-teste",
        "goianao.sso.redirect-uri=https://goianao.exemplo.jus.br/api/auth/sso/callback",
        "goianao.sso.url-frontend=https://goianao.exemplo.jus.br",
})
class SsoLigadoIT extends TesteDeIntegracao {

    @Test
    @DisplayName("a situacao informa que o SSO esta disponivel")
    void situacao() throws Exception {
        mvc.perform(get("/api/auth/sso/situacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(true));
    }

    @Test
    @DisplayName("o login redireciona ao Keycloak com client_id, redirect_uri e escopo openid")
    void redirecionaParaOKeycloak() throws Exception {
        String local = mvc.perform(get("/api/auth/sso/login"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(local)
                .startsWith("https://sso.exemplo.jus.br/auth/realms/tjgo"
                        + "/protocol/openid-connect/auth")
                .contains("client_id=premio-goianao")
                .contains("response_type=code")
                .contains("scope=openid")
                .contains("redirect_uri=" + enc(
                        "https://goianao.exemplo.jus.br/api/auth/sso/callback"))
                .contains("state=");

        // O segredo do client nunca sai para o navegador: ele so aparece na
        // chamada servidor-a-servidor de troca do codigo.
        assertThat(local).doesNotContain("segredo-de-teste");
    }

    @Test
    @DisplayName("o /auth da URL e preservado — sem ele o Keycloak do TJGO responde 404")
    void preservaSufixoAuth() throws Exception {
        String local = mvc.perform(get("/api/auth/sso/login"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(local).contains("/auth/realms/");
    }

    @Test
    @DisplayName("recusa do provedor volta ao frontend com a mensagem, nao com JSON de erro")
    void erroDoProvedorVoltaAoFrontend() throws Exception {
        mvc.perform(get("/api/auth/sso/callback")
                        .param("error", "access_denied")
                        .param("error_description", "usuario cancelou"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith(
                                "https://goianao.exemplo.jus.br/entrar#erro=")));
    }

    @Test
    @DisplayName("callback sem codigo tambem volta ao frontend explicando")
    void semCodigo() throws Exception {
        String local = mvc.perform(get("/api/auth/sso/callback"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(local).contains("/entrar#erro=");
    }

    @Test
    @DisplayName("o destino pedido no login volta no retorno")
    void destinoSobreviveAoFluxo() throws Exception {
        String local = mvc.perform(get("/api/auth/sso/login")
                        .param("destino", "/meus-certificados"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        String state = extrairState(local);
        String volta = mvc.perform(get("/api/auth/sso/callback")
                        .param("error", "access_denied")
                        .param("state", state))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(volta).contains("destino=" + enc("/meus-certificados"));
    }

    @Test
    @DisplayName("destino externo no state e descartado: nao vira redirecionamento aberto")
    void naoAceitaDestinoExterno() throws Exception {
        String stateMalicioso = "ruido|" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("//evil.example.com".getBytes(StandardCharsets.UTF_8));

        String volta = mvc.perform(get("/api/auth/sso/callback")
                        .param("error", "access_denied")
                        .param("state", stateMalicioso))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(volta)
                .startsWith("https://goianao.exemplo.jus.br/entrar#")
                .contains("destino=" + enc("/"))
                .doesNotContain("evil.example.com");
    }

    @Test
    @DisplayName("a URL de logout manda os dois nomes do parametro de retorno")
    void logoutServeAsDuasVersoesDoKeycloak() throws Exception {
        mvc.perform(get("/api/auth/sso/logout-url"))
                .andExpect(status().isOk())
                // post_logout_redirect_uri e o nome novo (Keycloak 19+);
                // redirect_uri e o antigo, que o Keycloak do TJGO exige.
                .andExpect(jsonPath("$.url").value(
                        org.hamcrest.Matchers.containsString("post_logout_redirect_uri=")))
                .andExpect(jsonPath("$.url").value(
                        org.hamcrest.Matchers.containsString("&redirect_uri=")));
    }

    @Test
    @DisplayName("o login manda o desafio PKCE S256 e guarda o verifier num cookie HttpOnly")
    void pkceNaIda() throws Exception {
        var resposta = mvc.perform(get("/api/auth/sso/login"))
                .andExpect(status().isFound())
                .andReturn().getResponse();
        String local = resposta.getHeader(HttpHeaders.LOCATION);
        String cookie = resposta.getHeader(HttpHeaders.SET_COOKIE);

        assertThat(local).contains("code_challenge_method=S256").contains("code_challenge=");
        assertThat(cookie)
                .startsWith("goianao_sso=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/auth/sso");

        // O desafio da URL e o SHA-256 do verifier guardado: e isso que o
        // Keycloak confere na troca.
        String guardado = cookie.substring("goianao_sso=".length(), cookie.indexOf(';'));
        String verifier = guardado.substring(guardado.indexOf('.') + 1);
        String challenge = parametro(local, "code_challenge");
        String esperado = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        assertThat(challenge).isEqualTo(esperado);
        // O verifier nunca vai ao Keycloak na ida.
        assertThat(local).doesNotContain(verifier);
    }

    @Test
    @DisplayName("codigo que volta sem o cookie da tentativa e recusado antes de ir ao Keycloak")
    void semCookieNaoTroca() throws Exception {
        String state = extrairState(mvc.perform(get("/api/auth/sso/login"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION));

        String volta = mvc.perform(get("/api/auth/sso/callback")
                        .param("code", "abc")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(volta).contains("/entrar#erro=").contains(enc("expirou"));
    }

    @Test
    @DisplayName("cookie de outra tentativa nao serve: e o login forjado do CSRF")
    void cookieDeOutraTentativa() throws Exception {
        String state = extrairState(mvc.perform(get("/api/auth/sso/login"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION));

        var resposta = mvc.perform(get("/api/auth/sso/callback")
                        .param("code", "abc")
                        .param("state", state)
                        .cookie(new Cookie("goianao_sso", "outrononce.verifierqualquer")))
                .andExpect(status().isFound())
                .andReturn().getResponse();

        assertThat(resposta.getHeader(HttpHeaders.LOCATION)).contains("/entrar#erro=");
        // A tentativa acaba ali: o cookie e apagado.
        assertThat(resposta.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    private static String parametro(String url, String nome) {
        for (String par : url.substring(url.indexOf('?') + 1).split("&")) {
            if (par.startsWith(nome + "=")) {
                return java.net.URLDecoder.decode(par.substring(nome.length() + 1),
                        StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String extrairState(String url) {
        int i = url.indexOf("state=");
        return java.net.URLDecoder.decode(url.substring(i + "state=".length()),
                StandardCharsets.UTF_8);
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
