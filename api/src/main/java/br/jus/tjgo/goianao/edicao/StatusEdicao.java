package br.jus.tjgo.goianao.edicao;

/**
 * Ciclo de vida da edicao (002). Nao existe "encerrada": a vigencia e apenas a
 * edicao padrao, e toda edicao PUBLICADA continua emitivel para reemissao
 * (constituicao, principio 8).
 */
public enum StatusEdicao {
    RASCUNHO("Rascunho"),
    PUBLICADA("Publicada");

    private final String rotulo;

    StatusEdicao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
