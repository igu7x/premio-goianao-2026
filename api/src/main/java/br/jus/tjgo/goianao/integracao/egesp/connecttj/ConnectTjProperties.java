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
 * @param dominioEmail  dominio do e-mail corporativo, usado para reconstruir o
 *                      endereco a partir do login do AD quando o RH nao tem o
 *                      e-mail da pessoa
 * @param requisicoesPorSegundo teto combinado com a equipe da API (6 por
 *                      padrao). Uma acao da tela vira dezenas de chamadas, e
 *                      sem freio elas sairiam todas de uma vez
 */
@ConfigurationProperties(prefix = "goianao.connecttj")
public record ConnectTjProperties(
        String url,
        String tokenUrl,
        String clientId,
        String clientSecret,
        Integer tamanhoPagina,
        String dominioEmail,
        Integer requisicoesPorSegundo) {

    public ConnectTjProperties {
        requisicoesPorSegundo = (requisicoesPorSegundo == null || requisicoesPorSegundo < 1)
                ? 6
                : requisicoesPorSegundo;
        url = semBarraFinal(url);
        tokenUrl = semBarraFinal(tokenUrl);
        tamanhoPagina = (tamanhoPagina == null || tamanhoPagina < 1) ? 100 : tamanhoPagina;
        dominioEmail = (dominioEmail == null || dominioEmail.isBlank())
                ? "tjgo.jus.br"
                : dominioEmail.replaceFirst("^@", "").trim();
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
