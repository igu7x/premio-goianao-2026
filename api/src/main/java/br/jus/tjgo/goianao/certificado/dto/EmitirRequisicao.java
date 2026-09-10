package br.jus.tjgo.goianao.certificado.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Pedido de emissao. Repare que <b>nao</b> ha e-mail nem selo: a identidade vem do
 * token e o selo vem do cadastro — o emissor so escolhe a unidade e, se quiser,
 * uma edicao anterior para reemitir (005/RNF-1, 006/RNF-1).
 */
public record EmitirRequisicao(
        Long edicaoId,
        @NotNull(message = "informe a unidade") Long unidadeId) {}
