package br.jus.tjgo.goianao.layout.dto;

import br.jus.tjgo.goianao.layout.AreaCodigo;
import br.jus.tjgo.goianao.layout.AreaTexto;
import jakarta.validation.constraints.NotNull;

/**
 * Posicoes a aplicar em todos os layouts da edicao de uma vez.
 *
 * <p>So carrega as areas: a arte de cada combinacao continua sendo a dela. O que
 * se replica e onde o texto entra, que e o que costuma ser igual nas oito pecas.
 */
public record AreasEmLoteRequisicao(
        @NotNull(message = "informe a área do nome") AreaTexto areaNome,
        @NotNull(message = "informe a área da unidade") AreaTexto areaUnidade,
        @NotNull(message = "informe a área do código") AreaCodigo areaCodigo) {}
