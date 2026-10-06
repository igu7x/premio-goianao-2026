package br.jus.tjgo.goianao.auth.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * A troca do codigo pelo token, contra um Keycloak simulado (DI-33).
 *
 * <p>O que importa aqui e o corpo do pedido: sem o {@code code_verifier} o
 * Keycloak do tribunal recusa a troca desde que o PKCE virou obrigatorio, e
 * com o segredo indo para um client publico a recusa seria por outro motivo.
 * O resto do fluxo (verificar o id_token) ja tem cobertura propria.
 */
@DisplayName("Troca do codigo no Keycloak")
class ClienteKeycloakTest {

    private static final String TOKEN =
            "https://sso.exemplo.jus.br/auth/realms/tjgo/protocol/openid-connect/token";

    private static SsoProperties props(String segredo) {
        return new SsoProperties("https://sso.exemplo.jus.br/auth", "tjgo", "goianao-stag",
                segredo, "https://api.exemplo.jus.br/api/auth/sso/callback", null, List.of(), null);
    }

    private static String trocar(SsoProperties props) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer keycloak = MockRestServiceServer.bindTo(builder).build();
        StringBuilder corpo = new StringBuilder();
        keycloak.expect(requestTo(TOKEN))
                .andExpect(requisicao ->
                        corpo.append(((MockClientHttpRequest) requisicao).getBodyAsString()))
                // Sem id_token: a troca para logo depois, que e o que se quer aqui.
                .andRespond(withSuccess("{\"access_token\":\"x\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new ClienteKeycloak(props, builder).autenticar("codigo-1", "verificador-1"))
                .isInstanceOf(SsoException.class);
        keycloak.verify();
        return corpo.toString();
    }

    @Test
    @DisplayName("leva o code_verifier da tentativa")
    void levaVerifier() {
        assertThat(trocar(props("segredo")))
                .contains("grant_type=authorization_code")
                .contains("code=codigo-1")
                .contains("code_verifier=verificador-1");
    }

    @Test
    @DisplayName("client confidencial manda o segredo; client publico nao manda nada")
    void segredoSoQuandoExiste() {
        assertThat(trocar(props("segredo"))).contains("client_secret=segredo");
        assertThat(trocar(props(null))).doesNotContain("client_secret");
    }

    @Test
    @DisplayName("o desafio e o SHA-256 do verifier em base64url, sem padding (RFC 7636)")
    void desafioDaRfc() {
        // Vetor do apendice B da RFC 7636.
        assertThat(DesafioPkce.challengeDe("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        DesafioPkce novo = DesafioPkce.novo();
        assertThat(novo.verifier()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(novo.challenge()).isEqualTo(DesafioPkce.challengeDe(novo.verifier()));
    }
}
