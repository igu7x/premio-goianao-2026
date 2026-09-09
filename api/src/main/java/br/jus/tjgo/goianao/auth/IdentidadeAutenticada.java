package br.jus.tjgo.goianao.auth;

/**
 * O que a porta de identidade devolve: apenas CPF e nome. Manter o contrato
 * minimo e o que permite trocar o mock pelo SSO real sem tocar no dominio
 * (constituicao, principio 7).
 */
public record IdentidadeAutenticada(String cpf, String nome) {}
