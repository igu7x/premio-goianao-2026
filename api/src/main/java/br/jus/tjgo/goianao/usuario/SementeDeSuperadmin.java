package br.jus.tjgo.goianao.usuario;

/**
 * O que um superadministrador leva consigo ao povoar a base de uma edicao nova
 * (011/RF-3).
 *
 * <p>E uma projecao, e nao a entidade, porque a copia acontece depois de trocar
 * para a base de destino: uma {@link Usuario} levada para fora da sessao em que
 * nasceu nao consegue nem carregar os proprios papeis.
 */
public record SementeDeSuperadmin(String email, String nome, String cpf, String senhaHash) {}
