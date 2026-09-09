package br.jus.tjgo.goianao.unidade.dto;

import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.usuario.Usuario;

/**
 * Unidade como sai no cadastro do superadministrador.
 *
 * <p>Traz o responsavel embutido em vez de so o id: a tela precisa mostrar quem
 * e, e uma segunda chamada por linha para descobrir o nome seria desperdicio
 * numa lista que tende a ter centenas de unidades.
 */
public record UnidadeResposta(
        Long id,
        String nome,
        boolean ativo,
        Responsavel responsavel) {

    public record Responsavel(Long id, String nome, String email) {}

    public static UnidadeResposta de(UnidadeJudiciaria unidade) {
        Usuario r = unidade.getResponsavel();
        return new UnidadeResposta(
                unidade.getId(),
                unidade.getNome(),
                unidade.isAtivo(),
                r == null ? null : new Responsavel(r.getId(), r.getNome(), r.getEmail()));
    }
}
