package br.jus.tjgo.goianao.suporte;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Artes sinteticas para os testes. Sao imagens chapadas de proposito: o que
 * importa aqui e a dimensao (o validador exige A4 paisagem a 300 DPI) e a
 * rapidez, nao o desenho.
 */
public final class ArteDeTeste {

    /** A4 paisagem a 300 DPI, o padrao exigido por 003/RNF-4. */
    public static final int LARGURA = 3508;
    public static final int ALTURA = 2480;

    private ArteDeTeste() {}

    public static byte[] valida() {
        return png(LARGURA, ALTURA);
    }

    /** Retrato: deve ser recusada pelo validador. */
    public static byte[] retrato() {
        return png(ALTURA, LARGURA);
    }

    /** Paisagem, porem com resolucao muito abaixo do exigido. */
    public static byte[] baixaResolucao() {
        return png(1200, 848);
    }

    /** Paisagem e grande, mas com proporcao fora do A4. */
    public static byte[] proporcaoErrada() {
        return png(3508, 1800);
    }

    private static byte[] png(int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        try {
            g.setColor(new Color(0xFA, 0xF8, 0xF3));
            g.fillRect(0, 0, largura, altura);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, "png", saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
