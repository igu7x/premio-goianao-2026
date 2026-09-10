package br.jus.tjgo.goianao.auth;

/**
 * O que a porta de identidade devolve: apenas e-mail e nome. Manter o contrato
 * minimo e o que permite trocar o mock pelo SSO real sem tocar no dominio
 * (constituicao, principio 7). O e-mail e a chave da pessoa (DI-24).
 */
public record IdentidadeAutenticada(String email, String nome) {}
