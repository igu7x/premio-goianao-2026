package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A assertion que prova a identidade do sistema ao Keycloak (DI-32).
 *
 * <p>O Keycloak responde {@code invalid_client} para qualquer defeito dela —
 * assinatura, {@code aud}, {@code jti} repetido, algoritmo trocado —, sem dizer
 * qual. Cada um desses e conferido aqui, porque la fora o unico sintoma seria
 * "recusou".
 */
@DisplayName("Assertion assinada do client (Signed JWT)")
class AssercaoDoClientTest {

    private static final String TOKEN = "https://sso.example/auth/realms/x/protocol/openid-connect/token";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("chave Ed25519 assina com EdDSA, e a assinatura confere com a chave publica")
    void ed25519() throws Exception {
        KeyPair par = ChavesDeTeste.ed25519();
        AssercaoDoClient assercao = new AssercaoDoClient(ChavesDeTeste.pem(par), relogio);

        String jwt = assercao.gerar("ces-goianao", TOKEN);

        assertThat(assercao.algoritmo()).isEqualTo("EdDSA");
        assertThat(cabecalho(jwt)).containsEntry("alg", "EdDSA").containsEntry("typ", "JWT");
        assertThat(confere(jwt, "Ed25519", par)).isTrue();
    }

    @Test
    @DisplayName("chave RSA assina com RS256: o mesmo codigo serve a homologacao")
    void rsa() throws Exception {
        KeyPair par = ChavesDeTeste.rsa();
        AssercaoDoClient assercao = new AssercaoDoClient(ChavesDeTeste.pem(par), relogio);

        String jwt = assercao.gerar("ces-goianao-stag", TOKEN);

        assertThat(assercao.algoritmo()).isEqualTo("RS256");
        assertThat(cabecalho(jwt)).containsEntry("alg", "RS256");
        assertThat(confere(jwt, "SHA256withRSA", par)).isTrue();
    }

    @Test
    @DisplayName("o client e emissor e assunto, o endereco do token e a audiencia, e vale 120 s")
    void conteudo() throws Exception {
        AssercaoDoClient assercao =
                new AssercaoDoClient(ChavesDeTeste.pem(ChavesDeTeste.ed25519()), relogio);

        Map<String, Object> corpo = corpo(assercao.gerar("ces-goianao", TOKEN));

        assertThat(corpo.get("iss")).isEqualTo("ces-goianao");
        assertThat(corpo.get("sub")).isEqualTo("ces-goianao");
        assertThat(corpo.get("aud")).isEqualTo(TOKEN);
        long iat = ((Number) corpo.get("iat")).longValue();
        assertThat(iat).isEqualTo(relogio.instant().getEpochSecond());
        assertThat(((Number) corpo.get("exp")).longValue() - iat).isEqualTo(120);
    }

    @Test
    @DisplayName("cada assertion tem jti proprio: o Keycloak recusa a repetida")
    void jtiNuncaSeRepete() throws Exception {
        AssercaoDoClient assercao =
                new AssercaoDoClient(ChavesDeTeste.pem(ChavesDeTeste.ed25519()), relogio);

        Object primeiro = corpo(assercao.gerar("c", TOKEN)).get("jti");
        Object segundo = corpo(assercao.gerar("c", TOKEN)).get("jti");

        assertThat(primeiro).isNotNull().isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("aceita a chave numa linha so, com espacos, com \\n literal ou sem marcadores")
    void formatosDeColagem() {
        String pem = ChavesDeTeste.pem(ChavesDeTeste.ed25519());
        String semMarcadores = pem.replaceAll("-----[A-Z ]+-----", "").trim();

        for (String variante : new String[] {
                pem.replace("\n", " "),
                pem.replace("\n", "\\n"),
                semMarcadores,
                pem.replace("\n", "\r\n")}) {
            assertThat(new AssercaoDoClient(variante, relogio).algoritmo()).isEqualTo("EdDSA");
        }
    }

    @Test
    @DisplayName("PKCS#1 e recusado com o comando de conversao na mensagem")
    void pkcs1() {
        String pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\nMIIEow==\n-----END RSA PRIVATE KEY-----";

        assertThatThrownBy(() -> new AssercaoDoClient(pkcs1, relogio))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENSHIFT_SSO_KEYCLOACK_PRIVATE_KEY")
                .hasMessageContaining("openssl pkcs8 -topk8 -nocrypt");
    }

    @Test
    @DisplayName("chave ilegivel derruba a subida sem repetir a chave no log")
    void ilegivel() {
        String lixo = "-----BEGIN PRIVATE KEY-----\nAAAAsegredoAAAA\n-----END PRIVATE KEY-----";

        assertThatThrownBy(() -> new AssercaoDoClient(lixo, relogio))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao pode ser lida")
                .hasMessageNotContaining("segredo");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cabecalho(String jwt) throws Exception {
        return JSON.readValue(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> corpo(String jwt) throws Exception {
        return JSON.readValue(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), Map.class);
    }

    private static boolean confere(String jwt, String algoritmo, KeyPair par) throws Exception {
        String[] partes = jwt.split("\\.");
        Signature verificador = Signature.getInstance(algoritmo);
        verificador.initVerify(par.getPublic());
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        return verificador.verify(Base64.getUrlDecoder().decode(partes[2]));
    }
}
