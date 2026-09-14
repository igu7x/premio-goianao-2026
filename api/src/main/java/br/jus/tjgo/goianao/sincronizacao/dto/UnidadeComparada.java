package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Uma unidade vista dos dois lados.
 *
 * <p>Nao traz responsavel nem contagem de habilitados de proposito: seriam duas
 * consultas por unidade para enfeitar uma lista que pode ter dezenas delas. Os
 * dois aparecem na comparacao de servidores, que e por unidade — onde custam
 * uma consulta so e a informacao e de fato usada.
 *
 * @param unidadeId     id local; nulo quando a unidade so existe no RH
 * @param nomeNoSistema nome gravado aqui — o que vai impresso no certificado
 * @param nomeNaApi     nome no RH; diferente do de cima significa renomeacao
 */
public record UnidadeComparada(
        ItemSincronizacao situacao,
        Long codigo,
        Long unidadeId,
        String nomeNoSistema,
        String nomeNaApi,
        String comarca) {}
