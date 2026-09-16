package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Resumo do cadastro dos lotados de uma unidade.
 *
 * @param lotadosNoRh quantos o RH aponta na unidade
 * @param criados     quantos viraram usuario agora
 * @param atualizados quantos ja existiam e tiveram os dados do RH e a lotacao
 *                    gravados
 * @param semEmail    quantos ficaram de fora por nao ter e-mail em lugar nenhum —
 *                    e o que explica a conta nao fechar, e sem ele o numero menor
 *                    parece falha
 */
public record LotacaoAplicada(int lotadosNoRh, int criados, int atualizados, int semEmail) {}
