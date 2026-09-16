package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Resumo do cadastro em lote das unidades que so existiam no RH.
 *
 * @param criadas     quantas passaram a existir no cadastro agora
 * @param jaExistiam  quantas ja estavam aqui e foram deixadas como estavam
 * @param casadas     quantas ja existiam pelo nome e ganharam o codigo do SIEDOS
 *                    nesta passagem — e o que transforma uma unidade digitada a
 *                    mao em unidade reconhecida pelo RH
 */
public record CadastroEmLote(int criadas, int jaExistiam, int casadas) {}
