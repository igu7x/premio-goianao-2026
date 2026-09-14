package br.jus.tjgo.goianao.integracao.egesp;

/**
 * Pessoa lotada numa unidade, como a listagem de lotados a devolve: matricula,
 * nome e situacao funcional — <b>sem e-mail e sem CPF</b>. Para virar alguem
 * que pode emitir certificado e preciso resolver o e-mail pela matricula
 * ({@link EgespClient#servidorPorMatricula}).
 */
public record LotadoEgesp(long matricula, String nome, String situacao, Long codigoUnidade) {}
