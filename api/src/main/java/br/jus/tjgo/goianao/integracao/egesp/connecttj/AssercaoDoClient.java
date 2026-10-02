package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A prova de identidade do Goianao diante do Keycloak: um JWT curto assinado
 * com a chave privada do client (Signed JWT, RFC 7523; DI-32).
 *
 * <p>Substitui o segredo compartilhado, que o tribunal abandonou em 2026-10:
 * com segredo, quem o lesse num log ou num chat se passaria pelo sistema para
 * sempre; com chave, o que circula e uma assinatura que vale dois minutos.
 *
 * <p>O algoritmo sai do <b>tipo da chave</b>, e nao de configuracao: producao
 * entrega Ed25519 (EdDSA) e homologacao usa RSA (RS256). Assim o mesmo codigo e
 * a mesma imagem servem aos dois, e nao existe variavel para esquecer de trocar
 * na promocao.
 */
class AssercaoDoClient {

    /** Validade da assertion: o padrao dos projetos do tribunal. */
    static final long VALIDADE_SEGUNDOS = 120;

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PrivateKey chave;
    private final String algoritmoJws;
    private final String algoritmoAssinatura;
    private final Clock relogio;

    /**
     * Le a chave na construcao, que acontece na subida: chave presente e
     * ilegivel derruba o pod com o motivo no log. Cair no mock em silencio
     * seria pior — a aplicacao subiria dizendo que o RH esta desligado, e a
     * causa so apareceria quando alguem estranhasse os dados.
     */
    AssercaoDoClient(String pem, Clock relogio) {
        this.relogio = relogio;
        PKCS8EncodedKeySpec especificacao = new PKCS8EncodedKeySpec(decodificar(pem));

        PrivateKey lida;
        String jws;
        String assinatura;
        try {
            lida = KeyFactory.getInstance("Ed25519").generatePrivate(especificacao);
            jws = "EdDSA";
            assinatura = "Ed25519";
        } catch (GeneralSecurityException naoEhEd25519) {
            try {
                lida = KeyFactory.getInstance("RSA").generatePrivate(especificacao);
                jws = "RS256";
                assinatura = "SHA256withRSA";
            } catch (GeneralSecurityException naoEhRsa) {
                throw new IllegalStateException(ilegivel(
                        "o conteudo nao e uma chave Ed25519 nem RSA em PKCS#8."));
            }
        }
        this.chave = lida;
        this.algoritmoJws = jws;
        this.algoritmoAssinatura = assinatura;
    }

    AssercaoDoClient(String pem) {
        this(pem, Clock.systemUTC());
    }

    /** "EdDSA" ou "RS256" — vai para o log da subida. */
    String algoritmo() {
        return algoritmoJws;
    }

    /**
     * Uma assertion nova a cada pedido de token. O {@code jti} nunca se repete:
     * o Keycloak recusa assertion reaproveitada, e o erro que ele devolve
     * ({@code invalid_client}) e o mesmo de chave errada — gastaria uma tarde
     * para descobrir que era so isso.
     */
    String gerar(String clientId, String enderecoDoToken) {
        long agora = relogio.instant().getEpochSecond();

        Map<String, Object> cabecalho = new LinkedHashMap<>();
        cabecalho.put("alg", algoritmoJws);
        cabecalho.put("typ", "JWT");

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("iss", clientId);
        corpo.put("sub", clientId);
        corpo.put("aud", enderecoDoToken);
        corpo.put("jti", UUID.randomUUID().toString());
        corpo.put("iat", agora);
        corpo.put("exp", agora + VALIDADE_SEGUNDOS);

        String assinado = base64(cabecalho) + "." + base64(corpo);
        try {
            Signature assinador = Signature.getInstance(algoritmoAssinatura);
            assinador.initSign(chave);
            assinador.update(assinado.getBytes(StandardCharsets.US_ASCII));
            return assinado + "." + BASE64_URL.encodeToString(assinador.sign());
        } catch (GeneralSecurityException e) {
            throw new ConnectTjException("Não foi possível assinar a credencial do sistema.", e);
        }
    }

    /**
     * A chave chega como a infra conseguir colar: com quebras de linha, com
     * espacos no lugar delas, com o {@code \n} literal de quem a pos numa linha
     * so, ou sem os marcadores. Tudo isso e a mesma chave, entao tudo e aceito.
     * Formatos que <b>nao</b> sao a mesma chave tem mensagem propria, porque a
     * correcao e um comando de conversao, e nao "confira a chave".
     */
    private static byte[] decodificar(String pem) {
        if (pem.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
            throw new IllegalStateException(ilegivel(
                    "a chave esta protegida por senha; ela precisa vir em PKCS#8 sem senha."));
        }
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException(ilegivel(
                    "a chave esta em PKCS#1. Converta com "
                            + "`openssl pkcs8 -topk8 -nocrypt -in chave.pem -out chave-pkcs8.pem`."));
        }
        String base64 = pem
                .replace("\\n", "")
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ilegivel("o conteudo nao e base64 valido."));
        }
    }

    /** Nunca inclui a chave na mensagem: ela vai para o log do pod. */
    private static String ilegivel(String motivo) {
        return "OPENSHIFT_SSO_KEYCLOACK_PRIVATE_KEY nao pode ser lida: " + motivo;
    }

    private static String base64(Map<String, Object> conteudo) {
        try {
            return BASE64_URL.encodeToString(JSON.writeValueAsBytes(conteudo));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
