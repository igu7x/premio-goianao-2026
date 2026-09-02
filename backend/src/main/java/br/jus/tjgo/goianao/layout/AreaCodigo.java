package br.jus.tjgo.goianao.layout;

/**
 * Area do codigo de validacao: a caixa do texto e, opcionalmente, o quadrado do
 * QR. O texto permite conferencia manual; o QR abre direto a pagina publica de
 * verificacao (007/RF-4).
 */
public record AreaCodigo(int x, int y, int largura, int altura,
                         Alinhamento alinhamento, AreaQr qr) {

    public AreaCodigo {
        if (largura <= 0 || altura <= 0) {
            throw new IllegalArgumentException("A área do código precisa de largura e altura positivas.");
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("A área do código não pode começar fora da imagem.");
        }
        if (alinhamento == null) {
            alinhamento = Alinhamento.CENTRO;
        }
    }

    public AreaTexto comoAreaTexto() {
        return new AreaTexto(x, y, largura, altura, alinhamento);
    }

    public boolean temQr() {
        return qr != null;
    }
}
