package br.jus.tjgo.goianao.integracao.egesp;

/**
 * Unidade judiciaria como o EGESP a devolve. O {@code nome} e o identificador
 * de fato: e ele que casa a unidade entre o cadastro de reconhecimentos (004) e
 * a semeadura da lista de servidores habilitados (008).
 */
public record UnidadeEgesp(String nome, String comarca) {}
