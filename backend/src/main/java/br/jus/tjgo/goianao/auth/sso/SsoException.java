package br.jus.tjgo.goianao.auth.sso;

/**
 * Falha no login corporativo.
 *
 * A mensagem e escrita para chegar ao usuario: o que aconteceu e, quando ha,
 * qual o proximo passo. O detalhe tecnico (client_id, redirect_uri, resposta do
 * Keycloak) fica no log — em tela ele nao ajuda quem esta tentando entrar e
 * expoe configuracao a quem nao deveria ver.
 */
public class SsoException extends RuntimeException {

    public SsoException(String mensagem) {
        super(mensagem);
    }
}
