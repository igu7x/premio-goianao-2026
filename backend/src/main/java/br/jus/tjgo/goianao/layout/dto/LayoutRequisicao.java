package br.jus.tjgo.goianao.layout.dto;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.layout.AreaCodigo;
import br.jus.tjgo.goianao.layout.AreaTexto;
import jakarta.validation.constraints.NotNull;

/**
 * Metadados enviados junto da arte (parte JSON do multipart).
 *
 * @param substituir confirmacao explicita quando a combinacao selo x tipo ja
 *                   existe na edicao (003/RF-4, CA-2).
 */
public record LayoutRequisicao(
        @NotNull(message = "informe o selo") Selo selo,
        @NotNull(message = "informe o tipo") TipoCertificado tipo,
        @NotNull(message = "informe a área do nome") AreaTexto areaNome,
        @NotNull(message = "informe a área da unidade") AreaTexto areaUnidade,
        @NotNull(message = "informe a área do código") AreaCodigo areaCodigo,
        boolean substituir) {}
