package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da API corporativa (ConnectTJ, que le o SIEDOS e o cadastro de
 * pessoal).
 *
 * <p>Fica em prefixo proprio porque pode simplesmente nao existir: enquanto o
 * client nao for criado, nenhuma credencial esta definida e o sistema segue
 * com os dados mockados. E o mesmo criterio do SSO — variavel ausente derruba
 * a funcionalidade, nao o pod.
 *
 * <p>A credencial e a <b>chave privada</b> (Signed JWT, DI-32), padrao que o
 * tribunal adotou em 2026-10. O segredo compartilhado continua aceito enquanto
 * homologacao nao troca de client: tira-lo antes deixaria stag no mock.
 *
 * @param url          base da API, com esquema
 * @param tokenUrl     endereco do token no Keycloak (client_credentials); e
 *                     tambem o {@code aud} da assertion assinada
 * @param clientId     identificador do client
 * @param chavePrivada chave privada PKCS#8 em PEM, Ed25519 ou RSA (vem de
 *                     Secret, nunca versionada). Tem precedencia sobre o segredo
 * @param clientSecret segredo do client, no modelo antigo
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
        String chavePrivada,
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
        return faltando().isEmpty();
    }

    /** Assinatura com chave (Signed JWT) ou, sem ela, o segredo do modelo antigo. */
    public boolean usaChave() {
        return preenchido(chavePrivada);
    }

    /**
     * O que falta para ligar, nos nomes das variaveis. Vai para o log da
     * subida: "por que esta no mock?" se responde lendo uma linha, sem abrir o
     * Deployment — que o usuario nem tem permissao de ver.
     */
    public List<String> faltando() {
        List<String> faltam = new ArrayList<>();
        if (!preenchido(url)) {
            faltam.add("GOIANAO_CONNECTTJ_URL");
        }
        if (!preenchido(tokenUrl)) {
            faltam.add("GOIANAO_CONNECTTJ_TOKEN_URL");
        }
        if (!preenchido(clientId)) {
            faltam.add("GOIANAO_CONNECTTJ_CLIENT_ID");
        }
        if (!preenchido(chavePrivada) && !preenchido(clientSecret)) {
            faltam.add("OPENSHIFT_SSO_KEYCLOACK_PRIVATE_KEY");
        }
        return faltam;
    }

    private static String semBarraFinal(String valor) {
        return valor == null ? null : valor.replaceAll("/+$", "");
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
