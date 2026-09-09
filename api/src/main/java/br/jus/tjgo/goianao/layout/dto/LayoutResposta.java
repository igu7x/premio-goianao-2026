package br.jus.tjgo.goianao.layout.dto;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.layout.AreaCodigo;
import br.jus.tjgo.goianao.layout.AreaTexto;
import br.jus.tjgo.goianao.layout.LayoutCertificado;
import java.time.LocalDateTime;

public record LayoutResposta(
        Long id,
        Long edicaoId,
        Selo selo,
        TipoCertificado tipo,
        int imagemLargura,
        int imagemAltura,
        String imagemUrl,
        AreaTexto areaNome,
        AreaTexto areaUnidade,
        AreaCodigo areaCodigo,
        LocalDateTime atualizadoEm) {

    public static LayoutResposta de(LayoutCertificado layout) {
        Long edicaoId = layout.getEdicao().getId();
        return new LayoutResposta(
                layout.getId(),
                edicaoId,
                layout.getSelo(),
                layout.getTipo(),
                layout.getImagemLargura(),
                layout.getImagemAltura(),
                "/api/edicoes/" + edicaoId + "/layouts/" + layout.getId() + "/imagem",
                layout.getAreaNome(),
                layout.getAreaUnidade(),
                layout.getAreaCodigo(),
                layout.getAtualizadoEm());
    }
}
