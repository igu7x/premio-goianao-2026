package br.jus.tjgo.goianao.layout.render;

import br.jus.tjgo.goianao.layout.AreaCodigo;
import br.jus.tjgo.goianao.layout.AreaQr;
import br.jus.tjgo.goianao.layout.AreaTexto;
import br.jus.tjgo.goianao.layout.LayoutCertificado;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/**
 * Motor unico de composicao do certificado: desenha nome, unidade e codigo de
 * validacao sobre a arte e exporta um PDF A4 paisagem.
 *
 * <p>E deliberadamente compartilhado pela pre-visualizacao do administrador
 * (003/RF-6) e pelas emissoes de magistrado (005) e servidor (006) — e essa
 * reutilizacao que garante que o preview seja exatamente o que sera emitido
 * (003/RNF-2).
 */
@Component
public class CertificadoRenderer {

    /** Dados variaveis; tudo o mais (titulo, ano, selo) ja vem embutido na arte. */
    public record DadosCertificado(
            String nome, String unidade, String codigoValidacao, String urlVerificacao) {}

    private static final float TAMANHO_MINIMO = 5f;
    private static final float CAP_HEIGHT_PADRAO = 700f;
    private static final String RETICENCIAS = "...";

    private final ImageStorage storage;
    private final FonteInstitucional fonte;
    private final GeradorQrCode qrCode;

    public CertificadoRenderer(ImageStorage storage, FonteInstitucional fonte,
                               GeradorQrCode qrCode) {
        this.storage = storage;
        this.fonte = fonte;
        this.qrCode = qrCode;
    }

    public byte[] renderizar(LayoutCertificado layout, DadosCertificado dados) {
        // A4 paisagem: a arte ocupa a pagina inteira e as areas do layout sao
        // coordenadas em px sobre a imagem-base, com origem no topo-esquerda.
        PDRectangle tamanhoPagina =
                new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {

            PDPage pagina = new PDPage(tamanhoPagina);
            documento.addPage(pagina);
            preencherMetadados(documento, dados);

            PDFont font = fonte.carregar(documento);
            float larguraPt = tamanhoPagina.getWidth();
            float alturaPt = tamanhoPagina.getHeight();
            float escalaX = larguraPt / layout.getImagemLargura();
            float escalaY = alturaPt / layout.getImagemAltura();

            try (PDPageContentStream fluxo = new PDPageContentStream(documento, pagina)) {
                PDImageXObject arte = PDImageXObject.createFromByteArray(
                        documento, storage.ler(layout.getImagemRef()), layout.getImagemRef());
                fluxo.drawImage(arte, 0, 0, larguraPt, alturaPt);

                fluxo.setNonStrokingColor(Color.BLACK);

                escreverNaArea(fluxo, font, layout.getAreaNome(), dados.nome(),
                        alturaPt, escalaX, escalaY);
                escreverNaArea(fluxo, font, layout.getAreaUnidade(), dados.unidade(),
                        alturaPt, escalaX, escalaY);

                AreaCodigo areaCodigo = layout.getAreaCodigo();
                escreverNaArea(fluxo, font, areaCodigo.comoAreaTexto(),
                        dados.codigoValidacao(), alturaPt, escalaX, escalaY);

                if (areaCodigo.temQr() && dados.urlVerificacao() != null) {
                    desenharQr(documento, fluxo, areaCodigo.qr(), dados.urlVerificacao(),
                            alturaPt, escalaX, escalaY);
                }
            }

            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do certificado.", e);
        }
    }

    private void preencherMetadados(PDDocument documento, DadosCertificado dados) {
        var info = documento.getDocumentInformation();
        info.setTitle("Certificado Prêmio Goianão - TJGO");
        info.setAuthor("Tribunal de Justiça do Estado de Goiás");
        info.setSubject("Certificado de reconhecimento - " + dados.unidade());
        info.setKeywords("Goianão; TJGO; certificado; " + dados.codigoValidacao());
        info.setCreator("Sistema Goianão");
    }

    /**
     * Escreve o texto centralizado verticalmente na caixa, no maior tamanho que
     * couber (003/RF-3). Se nem no tamanho minimo couber, corta com reticencias
     * em vez de invadir a arte.
     */
    private void escreverNaArea(PDPageContentStream fluxo, PDFont font, AreaTexto area,
                                String texto, float alturaPt, float escalaX, float escalaY)
            throws IOException {

        String conteudo = sanitizar(font, texto);
        if (conteudo.isBlank()) {
            return;
        }

        float caixaX = area.x() * escalaX;
        float caixaLargura = area.largura() * escalaX;
        float caixaAltura = area.altura() * escalaY;
        float caixaTopo = alturaPt - (area.y() * escalaY);
        float caixaBase = caixaTopo - caixaAltura;

        float capHeight = alturaDaCaixaDeGlifo(font);
        float tamanhoPorAltura = caixaAltura / (capHeight / 1000f);

        float larguraUnitaria = larguraDoTexto(font, conteudo, 1f);
        float tamanhoPorLargura = larguraUnitaria > 0
                ? caixaLargura / larguraUnitaria
                : tamanhoPorAltura;

        float tamanho = Math.min(tamanhoPorAltura, tamanhoPorLargura);

        if (tamanho < TAMANHO_MINIMO) {
            tamanho = TAMANHO_MINIMO;
            conteudo = truncarPara(font, conteudo, caixaLargura, tamanho);
        }

        float larguraTexto = larguraDoTexto(font, conteudo, tamanho);
        float x = switch (area.alinhamento()) {
            case ESQUERDA -> caixaX;
            case DIREITA -> caixaX + caixaLargura - larguraTexto;
            case CENTRO -> caixaX + (caixaLargura - larguraTexto) / 2f;
        };
        float linhaBase = caixaBase + (caixaAltura - (capHeight / 1000f) * tamanho) / 2f;

        fluxo.beginText();
        fluxo.setFont(font, tamanho);
        fluxo.newLineAtOffset(x, linhaBase);
        fluxo.showText(conteudo);
        fluxo.endText();
    }

    private void desenharQr(PDDocument documento, PDPageContentStream fluxo, AreaQr qr,
                            String url, float alturaPt, float escalaX, float escalaY)
            throws IOException {
        int ladoPx = Math.max(96, qr.tamanho());
        BufferedImage imagem = qrCode.gerar(url, ladoPx);
        PDImageXObject objeto = LosslessFactory.createFromImage(documento, imagem);

        float lado = qr.tamanho() * escalaX;
        float x = qr.x() * escalaX;
        float y = alturaPt - ((qr.y() + qr.tamanho()) * escalaY);
        fluxo.drawImage(objeto, x, y, lado, lado);
    }

    private float alturaDaCaixaDeGlifo(PDFont font) {
        try {
            float capHeight = font.getFontDescriptor() == null
                    ? 0f
                    : font.getFontDescriptor().getCapHeight();
            return capHeight > 0 ? capHeight : CAP_HEIGHT_PADRAO;
        } catch (RuntimeException e) {
            return CAP_HEIGHT_PADRAO;
        }
    }

    private float larguraDoTexto(PDFont font, String texto, float tamanho) {
        try {
            return font.getStringWidth(texto) / 1000f * tamanho;
        } catch (IOException | IllegalArgumentException e) {
            return 0f;
        }
    }

    private String truncarPara(PDFont font, String texto, float larguraMaxima, float tamanho) {
        String candidato = texto;
        while (candidato.length() > 1
                && larguraDoTexto(font, candidato + RETICENCIAS, tamanho) > larguraMaxima) {
            candidato = candidato.substring(0, candidato.length() - 1);
        }
        return candidato.equals(texto) ? texto : candidato.trim() + RETICENCIAS;
    }

    /**
     * Remove caracteres que a fonte corrente nao consegue codificar. Sem isto, um
     * nome com caractere fora do repertorio derrubaria a emissao inteira.
     */
    private String sanitizar(PDFont font, String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder limpo = new StringBuilder(texto.length());
        for (char c : texto.trim().toCharArray()) {
            if (Character.isWhitespace(c)) {
                limpo.append(' ');
                continue;
            }
            try {
                font.getStringWidth(String.valueOf(c));
                limpo.append(c);
            } catch (IOException | IllegalArgumentException e) {
                limpo.append(' ');
            }
        }
        return limpo.toString().replaceAll("\\s+", " ").trim();
    }
}
