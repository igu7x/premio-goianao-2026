package br.jus.tjgo.goianao.layout;

/** Quadrado onde o QR de verificacao e desenhado (003/RF-3b). Opcional. */
public record AreaQr(int x, int y, int tamanho) {

    public AreaQr {
        if (tamanho <= 0) {
            throw new IllegalArgumentException("O QR precisa de um tamanho positivo.");
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("O QR não pode começar fora da imagem.");
        }
    }
}
