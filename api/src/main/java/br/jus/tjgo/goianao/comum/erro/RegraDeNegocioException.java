package br.jus.tjgo.goianao.comum.erro;

/** Dado ate coerente, mas que viola uma regra do dominio -> HTTP 422. */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
