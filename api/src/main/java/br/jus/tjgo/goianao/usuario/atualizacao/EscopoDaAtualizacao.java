package br.jus.tjgo.goianao.usuario.atualizacao;

/**
 * Ate onde a atualizacao da base de usuarios vai.
 *
 * <p>{@link #TJGO} e a estrutura sob a raiz do tribunal: sao as unidades
 * administrativas, e leva minutos. {@link #COMPLETA} e o organograma inteiro,
 * varas incluidas — que ficam sob as comarcas, e nao sob a raiz do TJGO —, e
 * leva dezenas de minutos.
 */
public enum EscopoDaAtualizacao {
    TJGO,
    COMPLETA
}
