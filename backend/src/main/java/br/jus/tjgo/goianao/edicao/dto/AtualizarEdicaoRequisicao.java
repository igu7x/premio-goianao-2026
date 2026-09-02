package br.jus.tjgo.goianao.edicao.dto;

import jakarta.validation.constraints.Size;

/** Apenas dados descritivos: ano e status nunca sao editados por aqui (002/RF-8). */
public record AtualizarEdicaoRequisicao(
        @Size(max = 500, message = "descrição deve ter no máximo 500 caracteres")
        String descricao) {}
