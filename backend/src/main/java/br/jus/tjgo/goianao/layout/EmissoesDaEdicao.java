package br.jus.tjgo.goianao.layout;

/**
 * Porta para saber se uma edicao ja emitiu algum certificado. Mantida aqui para
 * que o pacote de layouts nao dependa do pacote de certificados.
 *
 * <p>E o que decide se o layout ainda pode ser ajustado depois da publicacao:
 * ver {@link LayoutService#edicaoEditavel(Long)}.
 */
public interface EmissoesDaEdicao {

    boolean houveEmissao(Long edicaoId);
}
