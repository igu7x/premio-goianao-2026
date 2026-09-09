package br.jus.tjgo.goianao.edicao.dto;

import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.StatusEdicao;
import java.time.LocalDateTime;

public record EdicaoResposta(
        Long id,
        Integer ano,
        String descricao,
        StatusEdicao status,
        boolean vigente,
        boolean emitivel,
        boolean aceitaInclusoes,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static EdicaoResposta de(Edicao e) {
        return new EdicaoResposta(
                e.getId(),
                e.getAno(),
                e.getDescricao(),
                e.getStatus(),
                e.isVigente(),
                e.estaPublicada(),
                e.aceitaInclusoes(),
                e.getCriadoEm(),
                e.getAtualizadoEm());
    }
}
