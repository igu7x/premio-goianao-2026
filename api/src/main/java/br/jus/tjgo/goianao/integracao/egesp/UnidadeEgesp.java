package br.jus.tjgo.goianao.integracao.egesp;

/**
 * Unidade judiciaria como o RH a devolve.
 *
 * <p>O {@code nome} continua sendo o que casa a unidade com o cadastro de
 * reconhecimentos (004) e o que vai impresso no certificado. O {@code codigo} e
 * a chave do SIEDOS: e por ele que a sincronizacao (010) reencontra a unidade
 * depois do primeiro casamento, imune a mudanca de grafia.
 *
 * @param codigo     codigo no SIEDOS; nulo no mock e no cadastro antigo
 * @param codigoPai  codigo da unidade superior, quando a origem informa
 */
public record UnidadeEgesp(Long codigo, String nome, String comarca, Long codigoPai) {

    public UnidadeEgesp(String nome, String comarca) {
        this(null, nome, comarca, null);
    }
}
