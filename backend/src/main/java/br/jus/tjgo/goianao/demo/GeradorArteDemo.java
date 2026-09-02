package br.jus.tjgo.goianao.demo;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Desenha uma arte de certificado para os dados de demonstracao.
 *
 * <p>Existe porque o sistema so faz sentido com arte de verdade: sem ela nao da
 * para conferir posicionamento, preview nem emissao. A arte definitiva vem do
 * setor de comunicacao do TJGO e e enviada pela tela de layouts — esta aqui
 * apenas ocupa o lugar dela em desenvolvimento, ja no padrao exigido
 * (A4 paisagem, 300 DPI, 3508x2480 px).
 */
@Component
public class GeradorArteDemo {

    public static final int LARGURA = 3508;
    public static final int ALTURA = 2480;

    private static final Color PAPEL = new Color(0xFA, 0xF8, 0xF3);
    private static final Color TINTA = new Color(0x1A, 0x1D, 0x1B);
    private static final Color TINTA_SUAVE = new Color(0x5B, 0x60, 0x5C);

    public byte[] gerar(int ano, Selo selo, TipoCertificado tipo) {
        BufferedImage imagem = new BufferedImage(LARGURA, ALTURA, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            Color corSelo = corDo(selo);

            g.setColor(PAPEL);
            g.fillRect(0, 0, LARGURA, ALTURA);

            desenharMoldura(g, corSelo);
            desenharCabecalho(g, ano);
            desenharMedalha(g, corSelo, selo);
            desenharTextosFixos(g, ano, selo, tipo);
            desenharAssinaturas(g);
            desenharRodapeVerificacao(g);

            return paraPng(imagem);
        } finally {
            g.dispose();
        }
    }

    private void desenharMoldura(Graphics2D g, Color corSelo) {
        g.setColor(corSelo);
        g.setStroke(new BasicStroke(10f));
        g.draw(new Rectangle2D.Float(150, 150, LARGURA - 300f, ALTURA - 300f));

        g.setColor(new Color(corSelo.getRed(), corSelo.getGreen(), corSelo.getBlue(), 90));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Rectangle2D.Float(190, 190, LARGURA - 380f, ALTURA - 380f));

        // Filete inferior: apoia visualmente a area de assinaturas.
        g.setColor(new Color(0x00, 0x00, 0x00, 26));
        g.fillRect(150, ALTURA - 160, LARGURA - 300, 10);
    }

    private void desenharCabecalho(Graphics2D g, int ano) {
        g.setColor(TINTA_SUAVE);
        centralizar(g, "TRIBUNAL DE JUSTIÇA DO ESTADO DE GOIÁS",
                fonte(Font.SANS_SERIF, Font.PLAIN, 46, 14), 400);

        g.setColor(TINTA);
        centralizar(g, "PRÊMIO GOIANÃO", fonte(Font.SERIF, Font.BOLD, 150, 10), 600);

        g.setColor(TINTA_SUAVE);
        centralizar(g, "EDIÇÃO " + ano, fonte(Font.SANS_SERIF, Font.PLAIN, 44, 22), 690);
    }

    private void desenharMedalha(Graphics2D g, Color corSelo, Selo selo) {
        int centroX = 3010;
        int centroY = 500;
        int raio = 150;

        g.setColor(new Color(corSelo.getRed(), corSelo.getGreen(), corSelo.getBlue(), 38));
        g.fill(new Ellipse2D.Float(centroX - raio, centroY - raio, raio * 2f, raio * 2f));

        g.setColor(corSelo);
        g.setStroke(new BasicStroke(9f));
        g.draw(new Ellipse2D.Float(centroX - raio, centroY - raio, raio * 2f, raio * 2f));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Float(centroX - raio + 26, centroY - raio + 26,
                (raio - 26) * 2f, (raio - 26) * 2f));

        Font fonte = fonte(Font.SANS_SERIF, Font.BOLD, 40, 6);
        String texto = selo.rotulo().toUpperCase();
        g.setFont(fonte);
        int largura = larguraDe(g, texto, fonte);
        g.setColor(corSelo.darker());
        g.drawString(texto, centroX - largura / 2, centroY + 14);
    }

    private void desenharTextosFixos(Graphics2D g, int ano, Selo selo, TipoCertificado tipo) {
        g.setColor(TINTA_SUAVE);
        centralizar(g, "Certificamos que", fonte(Font.SERIF, Font.ITALIC, 56, 0), 1060);

        String meio = tipo == TipoCertificado.MAGISTRADO
                ? "obteve o reconhecimento do Prêmio Goianão pela unidade"
                : "integra o quadro de servidores da unidade reconhecida";
        centralizar(g, meio, fonte(Font.SERIF, Font.ITALIC, 52, 0), 1420);

        String fecho = "reconhecida com o Selo " + selo.rotulo()
                + " na edição " + ano + " do Prêmio Goianão.";
        centralizar(g, fecho, fonte(Font.SERIF, Font.ITALIC, 52, 0), 1740);
    }

    private void desenharAssinaturas(Graphics2D g) {
        int linhaY = 1990;
        desenharAssinatura(g, 1100, linhaY, "Presidência do Tribunal de Justiça");
        desenharAssinatura(g, 2408, linhaY, "Coordenação do Prêmio Goianão");
    }

    private void desenharAssinatura(Graphics2D g, int centroX, int y, String legenda) {
        g.setColor(TINTA);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(centroX - 420, y, centroX + 420, y);

        Font fonte = fonte(Font.SANS_SERIF, Font.PLAIN, 38, 4);
        g.setFont(fonte);
        g.setColor(TINTA_SUAVE);
        int largura = larguraDe(g, legenda, fonte);
        g.drawString(legenda, centroX - largura / 2, y + 62);
    }

    private void desenharRodapeVerificacao(Graphics2D g) {
        Font fonte = fonte(Font.SANS_SERIF, Font.PLAIN, 30, 6);
        g.setFont(fonte);
        g.setColor(TINTA_SUAVE);
        g.drawString("AUTENTICIDADE", 520, 2150);
        g.drawString("Confira pelo QR ao lado ou informe o código:", 520, 2200);
    }

    /** Paleta dos selos, escolhida para manter contraste com o texto preto. */
    private Color corDo(Selo selo) {
        return switch (selo) {
            case BRONZE -> new Color(0xA1, 0x6B, 0x3C);
            case PRATA -> new Color(0x79, 0x80, 0x86);
            case OURO -> new Color(0xB4, 0x88, 0x2B);
            case DIAMANTE -> new Color(0x3E, 0x74, 0x9B);
        };
    }

    private Font fonte(String familia, int estilo, int tamanho, int espacamento) {
        Font base = new Font(familia, estilo, tamanho);
        if (espacamento == 0) {
            return base;
        }
        return base.deriveFont(java.util.Map.of(
                java.awt.font.TextAttribute.TRACKING, espacamento / 100f));
    }

    private void centralizar(Graphics2D g, String texto, Font fonte, int linhaBase) {
        g.setFont(fonte);
        int largura = larguraDe(g, texto, fonte);
        g.drawString(texto, (LARGURA - largura) / 2, linhaBase);
    }

    private int larguraDe(Graphics2D g, String texto, Font fonte) {
        FontRenderContext contexto = g.getFontRenderContext();
        return (int) fonte.getStringBounds(texto, contexto).getWidth();
    }

    private byte[] paraPng(BufferedImage imagem) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, "png", saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar a arte de demonstracao.", e);
        }
    }
}
