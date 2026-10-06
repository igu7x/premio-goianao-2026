package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Token da API corporativa, em cache.
 *
 * <p>O token vale 5 minutos, e uma varredura de unidade faz dezenas de
 * chamadas: pedir um token por chamada seria desperdicio, e guardar sem prazo
 * quebraria no meio da varredura. Guarda-se com a validade informada, menos uma
 * margem — e quem recebe 401 manda invalidar e tenta de novo, uma vez so.
 *
 * <p>Com chave, o client se identifica por uma assertion assinada (DI-32); sem
 * ela, pelo segredo do modelo antigo, que homologacao ainda usa.
 */
class TokenConnectTj {

    /** Margem para o token nao vencer entre a conferencia e a chegada da requisicao. */
    private static final Duration MARGEM = Duration.ofSeconds(30);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TIPO_ASSERCAO =
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final ConnectTjProperties props;
    private final RestClient http;
    private final AssercaoDoClient assercao;

    private String valor;
    private Instant expiraEm = Instant.EPOCH;

    /** {@code assercao} nulo significa o modelo antigo, por segredo. */
    TokenConnectTj(ConnectTjProperties props, RestClient http, AssercaoDoClient assercao) {
        this.props = props;
        this.http = http;
        this.assercao = assercao;
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
        if (assercao != null) {
            corpo.add("client_assertion_type", TIPO_ASSERCAO);
            corpo.add("client_assertion", assercao.gerar(props.clientId(), props.tokenUrl()));
        } else {
            corpo.add("client_secret", props.clientSecret());
        }

        Map<?, ?> resposta;
        try {
            resposta = http.post()
                    .uri(props.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(corpo)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new ConnectTjException(recusa(e), e);
        }

        if (resposta == null || resposta.get("access_token") == null) {
            throw new ConnectTjException("O Keycloak não devolveu token para a API corporativa.");
        }
        valor = resposta.get("access_token").toString();

        long segundos = resposta.get("expires_in") instanceof Number n ? n.longValue() : 300L;
        expiraEm = Instant.now().plusSeconds(segundos).minus(MARGEM);
    }

    /**
     * O Keycloak responde {@code invalid_client} para qualquer falha do client
     * — inexistente, chave publica diferente, autenticador errado, {@code aud}
     * errado, relogio adiantado. Sem dizer <i>quem</i> recusou, o erro parece
     * vir do ConnectTJ e a investigacao comeca no lugar errado; a mensagem
     * aponta onde olhar.
     */
    private String recusa(RestClientResponseException e) {
        String motivo = motivoDoKeycloak(e);
        return "O Keycloak recusou o client " + props.clientId() + " (HTTP "
                + e.getStatusCode().value() + (motivo == null ? "" : ": " + motivo)
                + "). Confira o client id, o realm do endereço do token, a chave pública "
                + "cadastrada no client e o relógio do servidor.";
    }

    /**
     * O {@code error} e o {@code error_description} que o Keycloak devolve no
     * corpo. Sem eles, um 400 de "service account desligado" e um de "chave
     * errada" chegavam a tela iguais, e descobrir qual era custava uma ida ao
     * log do Keycloak — que nem a infra do Goianao le. Nao ha credencial nesses
     * campos: sao mensagens do proprio servidor sobre o pedido.
     */
    private static String motivoDoKeycloak(RestClientResponseException e) {
        try {
            Map<?, ?> corpo = JSON.readValue(e.getResponseBodyAsString(), Map.class);
            Object erro = corpo.get("error");
            Object descricao = corpo.get("error_description");
            if (erro == null && descricao == null) {
                return null;
            }
            return (erro == null ? "" : erro) + (descricao == null ? "" : " — " + descricao);
        } catch (Exception semJson) {
            return null;
        }
    }
}
