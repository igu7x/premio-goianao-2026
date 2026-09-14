package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Como cada linha da tela de sincronizacao (010) se compara com o RH.
 *
 * <p>As quatro situacoes existem porque cada uma pede uma acao diferente —
 * nenhuma, corrigir, criar ou desvincular — e porque ver o que <b>nao</b> mudou
 * e parte de confiar no que mudou.
 */
public enum ItemSincronizacao {

    /** Igual dos dois lados. Nada a fazer. */
    SINCRONIZADO,

    /** Mesma pessoa ou unidade, algum dado divergente. */
    DESATUALIZADO,

    /** Existe no RH e nao no Goianao. */
    SO_NA_API,

    /** Existe no Goianao e nao veio do RH: transferencia, exoneracao, ou cadastro manual. */
    ORFAO
}
