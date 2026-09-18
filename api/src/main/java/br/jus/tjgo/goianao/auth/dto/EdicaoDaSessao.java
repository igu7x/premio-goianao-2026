package br.jus.tjgo.goianao.auth.dto;

import br.jus.tjgo.goianao.auth.AcessoPorEdicao.EdicaoDeAcesso;
import java.util.List;

/**
 * Uma edicao como a sessao a enxerga (feature 011).
 *
 * <p>Vai na resposta do login e do {@code /me} para que a interface saiba sobre
 * qual base esta agindo e quais outras estao ao alcance de quem entrou. O nome
 * do schema nao vem junto: e detalhe de armazenamento, e o frontend nao tem o
 * que fazer com ele.
 */
public record EdicaoDaSessao(Long id, int ano, boolean vigente, List<String> papeis) {

    public static EdicaoDaSessao de(EdicaoDeAcesso acesso) {
        return new EdicaoDaSessao(acesso.id(), acesso.ano(), acesso.vigente(),
                acesso.papeis().stream().map(Enum::name).sorted().toList());
    }
}
