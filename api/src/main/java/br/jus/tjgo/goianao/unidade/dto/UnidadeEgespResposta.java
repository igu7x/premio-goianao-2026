package br.jus.tjgo.goianao.unidade.dto;

/**
 * Unidade oferecida ao administrador para selecao. {@code jaCadastrada} indica
 * que ja existe espelho local (e portanto um id utilizavel diretamente).
 */
public record UnidadeEgespResposta(String nome, String comarca, Long unidadeId, boolean jaCadastrada) {}
