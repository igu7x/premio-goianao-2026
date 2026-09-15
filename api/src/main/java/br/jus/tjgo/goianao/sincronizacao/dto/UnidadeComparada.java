package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Uma unidade vista dos dois lados.
 *
 * <p>Não traz responsável nem contagem de habilitados de propósito: seriam duas
 * consultas por unidade para enfeitar uma lista que pode ter centenas delas. Os
 * dois aparecem na comparação de servidores, que é por unidade — onde custam
 * uma consulta só e a informação é de fato usada.
 *
 * <p>O código do pai e o nível vêm junto para a tela montar a árvore do
 * organograma: a partir da Presidência são quase duzentas unidades em seis
 * níveis, e uma lista plana não deixa ninguém entender onde cada uma fica.
 *
 * @param unidadeId     id local; nulo quando a unidade só existe no RH
 * @param nomeNoSistema nome gravado aqui — o que vai impresso no certificado
 * @param nomeNaApi     nome no RH; diferente do de cima significa renomeação
 * @param nivel         profundidade no organograma, como o RH a informa
 */
public record UnidadeComparada(
        ItemSincronizacao situacao,
        Long codigo,
        Long unidadeId,
        String nomeNoSistema,
        String nomeNaApi,
        String comarca,
        Long codigoPai,
        String nomePai,
        Integer nivel) {}
