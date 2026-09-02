package br.jus.tjgo.goianao.unidade.dto;

import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;

public record UnidadeResposta(Long id, String nome) {

    public static UnidadeResposta de(UnidadeJudiciaria unidade) {
        return new UnidadeResposta(unidade.getId(), unidade.getNome());
    }
}
