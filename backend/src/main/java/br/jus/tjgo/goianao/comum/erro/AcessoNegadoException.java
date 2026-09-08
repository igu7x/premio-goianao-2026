package br.jus.tjgo.goianao.comum.erro;

/**
 * O usuario esta autenticado, mas a operacao esta fora do seu escopo — emitir
 * por unidade alheia, editar lista de unidade que nao e sua -> HTTP 403.
 */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
