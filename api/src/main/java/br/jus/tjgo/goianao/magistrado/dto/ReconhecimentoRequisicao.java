package br.jus.tjgo.goianao.magistrado.dto;

import br.jus.tjgo.goianao.comum.Selo;
import jakarta.validation.constraints.NotNull;

/**
 * Unidade + selo de um reconhecimento.
 *
 * <p>A unidade pode vir pelo id local (quando ja espelhada) ou pelo nome exato
 * do EGESP. Em ambos os casos o servidor confere a origem: o administrador
 * seleciona da lista do EGESP, nunca digita texto livre (004/RF-1).
 */
public record ReconhecimentoRequisicao(
        Long unidadeId,
        String unidadeNome,
        @NotNull(message = "informe o selo") Selo selo) {}
