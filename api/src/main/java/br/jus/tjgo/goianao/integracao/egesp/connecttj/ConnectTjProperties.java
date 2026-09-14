package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da API corporativa (ConnectTJ, que le o SIEDOS e o cadastro de
 * pessoal).
 *
 * <p>Fica em prefixo proprio porque pode simplesmente nao existir: enquanto o
 * client nao for criado, nenhuma destas propriedades esta definida e o sistema
 * segue com os dados mockados. E o mesmo criterio do SSO — variavel ausente
 * derruba a funcionalidade, nao o pod.
 *
 * @param url          base da API, com esquema
 * @param tokenUrl     endereco do token no Keycloak (client_credentials)
 * @param clientId     identificador do client
 * @param clientSecret segredo do client (vem de Secret, nunca versionado)
 * @param tamanhoPagina paginacao usada ao varrer os lotados de uma unidade
 */
@ConfigurationProperties(prefix = "goianao.connecttj")
public record ConnectTjProperties(
        String url,
        String tokenUrl,
        String clientId,
        String clientSecret,
        Integer tamanhoPagina) {

    public ConnectTjProperties {
        url = semBarraFinal(url);
        tokenUrl = semBarraFinal(tokenUrl);
        tamanhoPagina = (tamanhoPagina == null || tamanhoPagina < 1) ? 100 : tamanhoPagina;
    }

    /** So liga com a configuracao completa; faltando qualquer peca, vale o mock. */
    public boolean habilitado() {
        return preenchido(url) && preenchido(tokenUrl) && preenchido(clientId)
                && preenchido(clientSecret);
    }

    private static String semBarraFinal(String valor) {
        return valor == null ? null : valor.replaceAll("/+$", "");
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
