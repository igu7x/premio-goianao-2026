package br.jus.tjgo.goianao.certificado.dto;

import br.jus.tjgo.goianao.edicao.Edicao;

/**
 * Edicao oferecida no seletor de emissao. A vigente vem marcada e e o padrao,
 * mas qualquer edicao publicada anterior continua disponivel para reemissao.
 */
public record EdicaoOpcaoResposta(Long id, Integer ano, String descricao, boolean vigente) {

    public static EdicaoOpcaoResposta de(Edicao edicao) {
        return new EdicaoOpcaoResposta(edicao.getId(), edicao.getAno(),
                edicao.getDescricao(), edicao.isVigente());
    }
}
