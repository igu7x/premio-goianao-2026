package br.jus.tjgo.goianao.auth.sso;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do SSO (Keycloak do TJGO).
 *
 * Fica separada de {@code GoianaoProperties} porque pode nao existir: enquanto o
 * client nao for criado, nenhuma destas propriedades esta definida e o sistema
 * continua no login mockado. Ter um prefixo proprio deixa isso explicito no
 * ConfigMap e evita que a ausencia de SSO quebre o resto da configuracao.
 *
 * <h2>Tres armadilhas do Keycloak do TJGO</h2>
 *
 * <b>Os enderecos precisam de esquema.</b> Sem {@code https://}, o
 * redirecionamento para o Keycloak sai relativo e o navegador o resolve contra
 * o caminho da propria API, devolvendo 404. Ver {@link #comEsquema}.
 *
 * <b>O sufixo {@code /auth} e obrigatorio.</b> Confirmado no discovery do
 * proprio servidor em 09/09/2026: o issuer e
 * {@code https://sso.tjgo.jus.br/auth/realms/tjgo.gov-tst}. Sem o sufixo, o
 * Keycloak responde "Resource not found" — e a resposta vem do Keycloak, com a
 * cara dele, o que engana: parece problema de realm ou de client, e e so o
 * caminho. Confira com
 * {@code curl https://sso.tjgo.jus.br/auth/realms/<realm>/.well-known/openid-configuration},
 * que devolve 200 com o sufixo e 404 sem ele.
 *
 * <b>O realm nao publica CPF.</b> O discovery lista {@code email},
 * {@code preferred_username} e {@code name}, e nada de CPF — foi isso que levou
 * o sistema a chavear pelo e-mail corporativo (DI-24), como o outro sistema do
 * tribunal ja fazia. Ainda assim {@link #claimsEmail()} e uma lista tentada em
 * ordem: se um client entregar o e-mail noutro claim, a correcao e de
 * ConfigMap, nao de codigo.
 *
 * @param url          base do Keycloak, <b>incluindo</b> {@code /auth}
 * @param realm        realm do tribunal
 * @param clientId     identificador do client
 * @param clientSecret segredo do client (vem de Secret, nunca versionado)
 * @param redirectUri  URI de callback registrada no client
 * @param urlFrontend  para onde devolver o navegador apos autenticar
 * @param claimsEmail  claims tentados, em ordem, ate achar um e-mail valido
 * @param claimNome    claim do nome de exibicao
 */
@ConfigurationProperties(prefix = "goianao.sso")
public record SsoProperties(
        String url,
        String realm,
        String clientId,
        String clientSecret,
        String redirectUri,
        String urlFrontend,
        List<String> claimsEmail,
        String claimNome) {

    private static final Logger log = LoggerFactory.getLogger(SsoProperties.class);

    public SsoProperties {
        // preferred_username fica de reserva: em alguns realms o login e o
        // proprio e-mail. Quando nao e, a validacao de formato o descarta.
        claimsEmail = (claimsEmail == null || claimsEmail.isEmpty())
                ? List.of("email", "preferred_username")
                : claimsEmail;
        claimNome = (claimNome == null || claimNome.isBlank()) ? "name" : claimNome;
        url = comEsquema(url == null ? null : url.replaceAll("/+$", ""), "url");
        redirectUri = comEsquema(redirectUri, "redirect-uri");
        urlFrontend = comEsquema(urlFrontend, "url-frontend");
    }

    /**
     * Garante que o endereco tenha esquema.
     *
     * <p>Sem {@code https://}, o {@code Location} do redirecionamento sai
     * <b>relativo</b> e o navegador o resolve contra o caminho da propria API:
     * em vez de ir ao Keycloak, ele pede
     * {@code /api/auth/sso/sso.tjgo.jus.br/realms/...} e recebe 404 da nossa
     * aplicacao. O sintoma nao aponta em nada para a variavel de ambiente que o
     * causou, e ja custou um deploy.
     *
     * <p>Completar e melhor do que recusar: um endereco sem esquema so poderia
     * ser HTTPS num ambiente de tribunal, e derrubar a aplicacao por isso
     * deixaria o sistema inteiro fora do ar por causa de oito caracteres. O
     * aviso mantem a configuracao errada visivel a quem for procurar.
     */
    private static String comEsquema(String endereco, String nome) {
        if (endereco == null || endereco.isBlank() || endereco.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return endereco;
        }
        log.warn("goianao.sso.{} veio sem esquema (\"{}\"); assumindo https."
                + " Corrija a variavel de ambiente: sem esquema o redirecionamento"
                + " sai relativo e o login falha com 404 na propria API.", nome, endereco);
        return "https://" + endereco;
    }

    /**
     * O SSO so liga com a configuracao completa. Faltando qualquer peca, o
     * sistema segue no login mockado em vez de subir quebrado — um pod que nao
     * inicia por causa de uma variavel ausente e pior de diagnosticar do que um
     * endpoint que responde "SSO nao configurado".
     */
    public boolean habilitado() {
        return preenchido(url) && preenchido(realm) && preenchido(clientId)
                && preenchido(clientSecret) && preenchido(redirectUri);
    }

    public String issuer() {
        return url + "/realms/" + realm;
    }

    public String urlAutorizacao() {
        return issuer() + "/protocol/openid-connect/auth";
    }

    public String urlToken() {
        return issuer() + "/protocol/openid-connect/token";
    }

    public String urlJwks() {
        return issuer() + "/protocol/openid-connect/certs";
    }

    public String urlLogout() {
        return issuer() + "/protocol/openid-connect/logout";
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
