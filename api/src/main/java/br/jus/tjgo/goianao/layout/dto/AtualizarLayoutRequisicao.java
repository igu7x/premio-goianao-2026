package br.jus.tjgo.goianao.layout.dto;

import br.jus.tjgo.goianao.layout.AreaCodigo;
import br.jus.tjgo.goianao.layout.AreaTexto;
import jakarta.validation.constraints.NotNull;

/** Atualizacao das areas; a arte so e trocada se um novo arquivo for enviado. */
public record AtualizarLayoutRequisicao(
        @NotNull(message = "informe a área do nome") AreaTexto areaNome,
        @NotNull(message = "informe a área da unidade") AreaTexto areaUnidade,
        @NotNull(message = "informe a área do código") AreaCodigo areaCodigo) {}
