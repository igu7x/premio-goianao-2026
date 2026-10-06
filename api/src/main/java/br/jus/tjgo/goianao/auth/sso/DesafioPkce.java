package br.jus.tjgo.goianao.auth.sso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * O par do PKCE (RFC 7636): o {@code verifier} fica com a API, o
 * {@code challenge} vai ao Keycloak na ida (DI-33).
 *
 * <p>O codigo de autorizacao volta pela URL, e URL vaza — historico, log de
 * proxy, Referer. Com PKCE, o codigo sozinho nao vale nada: a troca exige o
 * verifier, que nunca passou pelo navegador em claro nem pelo Keycloak antes
 * da troca. O tribunal tornou o S256 obrigatorio em 2026-10.
 *
 * @param verifier  segredo aleatorio desta tentativa de login
 * @param challenge {@code BASE64URL(SHA-256(verifier))}
 */
record DesafioPkce(String verifier, String challenge) {

    static final String METODO = "S256";

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    /** 32 bytes aleatorios viram 43 caracteres: o minimo da RFC, com entropia de sobra. */
    static DesafioPkce novo() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String verifier = BASE64_URL.encodeToString(bytes);
        return new DesafioPkce(verifier, challengeDe(verifier));
    }

    static String challengeDe(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ausente na JVM", e);
        }
    }
}
