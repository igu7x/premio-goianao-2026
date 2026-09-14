package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Token da API corporativa, em cache.
 *
 * <p>O token vale 5 minutos, e uma varredura de unidade faz dezenas de
 * chamadas: pedir um token por chamada seria desperdicio, e guardar sem prazo
 * quebraria no meio da varredura. Guarda-se com a validade informada, menos uma
 * margem — e quem recebe 401 manda invalidar e tenta de novo, uma vez so.
 */
class TokenConnectTj {

    /** Margem para o token nao vencer entre a conferencia e a chegada da requisicao. */
    private static final Duration MARGEM = Duration.ofSeconds(30);

    private final ConnectTjProperties props;
    private final RestClient http;

    private String valor;
    private Instant expiraEm = Instant.EPOCH;

    TokenConnectTj(ConnectTjProperties props, RestClient http) {
        this.props = props;
        this.http = http;
    }

    synchronized String obter() {
        if (valor != null && Instant.now().isBefore(expiraEm)) {
            return valor;
        }
        renovar();
        return valor;
    }

    /** Chamado no 401: o token pode ter sido revogado antes de vencer. */
    synchronized void invalidar() {
        valor = null;
        expiraEm = Instant.EPOCH;
    }

    private void renovar() {
        MultiValueMap<String, String> corpo = new LinkedMultiValueMap<>();
        corpo.add("grant_type", "client_credentials");
        corpo.add("client_id", props.clientId());
        corpo.add("client_secret", props.clientSecret());

        @SuppressWarnings("unchecked")
        Map<String, Object> resposta = http.post()
                .uri(props.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(corpo)
                .retrieve()
                .body(Map.class);

        if (resposta == null || resposta.get("access_token") == null) {
            throw new ConnectTjException("O Keycloak não devolveu token para a API corporativa.");
        }
        valor = resposta.get("access_token").toString();

        long segundos = resposta.get("expires_in") instanceof Number n ? n.longValue() : 300L;
        expiraEm = Instant.now().plusSeconds(segundos).minus(MARGEM);
    }
}
