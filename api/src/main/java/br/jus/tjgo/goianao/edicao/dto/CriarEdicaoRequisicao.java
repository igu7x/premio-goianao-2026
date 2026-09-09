package br.jus.tjgo.goianao.edicao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarEdicaoRequisicao(
        @NotNull(message = "informe o ano")
        @Min(value = 2000, message = "ano deve ser a partir de 2000")
        @Max(value = 2100, message = "ano deve ser até 2100")
        Integer ano,

        @Size(max = 500, message = "descrição deve ter no máximo 500 caracteres")
        String descricao) {}
