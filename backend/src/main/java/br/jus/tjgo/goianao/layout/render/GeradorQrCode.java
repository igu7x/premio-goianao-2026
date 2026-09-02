package br.jus.tjgo.goianao.layout.render;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gera o QR impresso no certificado. Ele codifica a **URL publica de
 * verificacao** (007/RF-4), de modo que ler o codigo ja abre a conferencia.
 */
@Component
public class GeradorQrCode {

    public BufferedImage gerar(String conteudo, int tamanhoPx) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(
                    conteudo,
                    BarcodeFormat.QR_CODE,
                    tamanhoPx,
                    tamanhoPx,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            // Preto sobre branco: o QR precisa de fundo solido para leitura
            // confiavel mesmo sobre uma arte com textura.
            return MatrixToImageWriter.toBufferedImage(matriz,
                    new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF));
        } catch (WriterException e) {
            throw new IllegalStateException("Falha ao gerar o QR de verificacao.", e);
        }
    }
}
