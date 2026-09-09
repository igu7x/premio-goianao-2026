package br.jus.tjgo.goianao.layout;

/**
 * Caixa onde um texto e escrito sobre a arte, em pixels com origem no canto
 * superior esquerdo da imagem-base (003, ponto resolvido).
 *
 * <p>Fonte, cor e tamanho nao sao configuraveis: a fonte e institucional fixa, a
 * cor e preta e o tamanho e auto-ajustado para caber na caixa (003/RF-3).
 */
public record AreaTexto(int x, int y, int largura, int altura, Alinhamento alinhamento) {

    public AreaTexto {
        if (largura <= 0 || altura <= 0) {
            throw new IllegalArgumentException("A área precisa de largura e altura positivas.");
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("A área não pode começar fora da imagem.");
        }
        if (alinhamento == null) {
            alinhamento = Alinhamento.CENTRO;
        }
    }

    public int direita() {
        return x + largura;
    }

    public int base() {
        return y + altura;
    }
}
