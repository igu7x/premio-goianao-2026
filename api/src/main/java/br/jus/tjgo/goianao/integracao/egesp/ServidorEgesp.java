package br.jus.tjgo.goianao.integracao.egesp;

/**
 * Servidor lotado em uma unidade, conforme o EGESP.
 *
 * @param email e-mail corporativo — a chave da pessoa (DI-24). Quem vier sem
 *              ele nao entra na lista, porque nao seria reconhecido no login
 * @param cpf   opcional, so informativo
 */
public record ServidorEgesp(String email, String nome, String cpf) {}
