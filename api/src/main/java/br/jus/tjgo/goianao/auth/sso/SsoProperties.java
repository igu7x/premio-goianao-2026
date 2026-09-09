package br.jus.tjgo.goianao.auth.sso;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do SSO (Keycloak do TJGO).
 *
 * Fica separada de {@code GoianaoProperties} porque pode nao existir: enquanto o
 * client nao for criado, nenhuma destas propriedades esta definida e o sistema
 * continua no login mockado. Ter um prefixo proprio deixa isso explicito no
 * ConfigMap e evita que a ausencia de SSO quebre o resto da configuracao.
 *
 * <h2>Duas armadilhas do Keycloak do TJGO</h2>
 *
 * <b>O sufixo {@code /auth} na URL e obrigatorio.</b> O Keycloak do tribunal e
 * anterior a versao 17, que foi quando o prefixo saiu do caminho padrao. A URL
 * correta termina em {@code /auth} — {@code https://<host-do-keycloak>/auth},
 * nao {@code https://<host-do-keycloak>}. Sem ele o login falha com "Resource not
 * found" — e essa e a primeira coisa a conferir quando o login para de
 * funcionar depois de alguem recriar o secret.
 *
 * <b>O CPF nao tem claim garantido.</b> Depende do mapper configurado no
 * client, e o outro sistema do tribunal chaveia por e-mail, entao nao serve de
 * referencia. Por isso {@link #claimsCpf()} e uma lista tentada em ordem: assim
 * a resposta da infra vira alteracao de ConfigMap, nao de codigo.
 *
 * @param url          base do Keycloak, <b>incluindo</b> {@code /auth}
 * @param realm        realm do tribunal
 * @param clientId     identificador do client
 * @param clientSecret segredo do client (vem de Secret, nunca versionado)
 * @param redirectUri  URI de callback registrada no client
 * @param urlFrontend  para onde devolver o navegador apos autenticar
 * @param claimsCpf    claims tentados, em ordem, ate achar o CPF
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
        List<String> claimsCpf,
        String claimNome) {

    public SsoProperties {
        claimsCpf = (claimsCpf == null || claimsCpf.isEmpty())
                ? List.of("cpf", "CPF", "preferred_username")
                : claimsCpf;
        claimNome = (claimNome == null || claimNome.isBlank()) ? "name" : claimNome;
        url = url == null ? null : url.replaceAll("/+$", "");
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
