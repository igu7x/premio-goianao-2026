package br.jus.tjgo.goianao.comum.erro;

/**
 * Login recusado.
 *
 * <p>A mensagem e deliberadamente unica para e-mail inexistente, senha errada e
 * usuario desativado: distinguir os casos informaria a quem tenta adivinhar
 * quais contas existem no sistema.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException(String mensagem) {
        super(mensagem);
    }
}
