package br.jus.tjgo.goianao.integracao.egesp;

import java.util.List;

/**
 * Porta de acesso ao EGESP (sistema de RH). Isolada atras de interface para que
 * a troca do mock pela integracao real nao afete o dominio (constituicao,
 * principio 7).
 *
 * <p>E usada em dois momentos e <b>nunca</b> na emissao: para listar as unidades
 * que o administrador seleciona (004/RF-1) e para semear a lista de servidores
 * habilitados de uma unidade (008/RF-1). A emissao do servidor le a lista ja
 * persistida (006/RNF-2), o que mantem a reemissao de edicoes antigas fiel ao
 * que valia naquela epoca.
 */
public interface EgespClient {

    /** Unidades judiciarias do TJGO; {@code filtro} opcional por trecho do nome. */
    List<UnidadeEgesp> listarUnidades(String filtro);

    /** Servidores lotados na unidade, identificada pelo nome vindo do EGESP. */
    List<ServidorEgesp> listarServidoresPorUnidade(String nomeUnidade);
}
