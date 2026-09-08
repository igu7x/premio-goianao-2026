package br.jus.tjgo.goianao.layout.render;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.config.GoianaoProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Valida o upload da arte: A4 paisagem a 300 DPI (~3508x2480 px), conforme
 * 003/RNF-4. Aceitar arte fora do padrao produziria certificados com texto
 * deslocado, ja que as areas sao coordenadas em px sobre essa base.
 */
@Component
public class ValidadorDeArte {

    private static final double PROPORCAO_A4 = Math.sqrt(2.0);
    private static final List<String> TIPOS_ACEITOS = List.of("image/png", "image/jpeg");

    private final GoianaoProperties props;

    public ValidadorDeArte(GoianaoProperties props) {
        this.props = props;
    }

    public DimensoesArte validar(byte[] conteudo, String contentType) {
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraDeNegocioException("Envie a imagem da arte do certificado.");
        }
        if (contentType == null
                || !TIPOS_ACEITOS.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new RegraDeNegocioException(
                    "Formato não aceito. Envie a arte em PNG ou JPEG.");
        }

        BufferedImage imagem;
        try {
            imagem = ImageIO.read(new ByteArrayInputStream(conteudo));
        } catch (IOException e) {
            throw new RegraDeNegocioException("Não foi possível ler a imagem enviada.");
        }
        if (imagem == null) {
            throw new RegraDeNegocioException("O arquivo enviado não é uma imagem válida.");
        }

        int largura = imagem.getWidth();
        int altura = imagem.getHeight();

        if (largura <= altura) {
            throw new RegraDeNegocioException(
                    "A arte deve estar em orientação paisagem (largura maior que a altura).");
        }
        if (largura < props.layout().larguraMinima()) {
            throw new RegraDeNegocioException(
                    "Resolução insuficiente: a arte deve ter ao menos "
                            + props.layout().larguraMinima()
                            + " px de largura (A4 paisagem a 300 DPI ~ 3508x2480).");
        }

        double proporcao = (double) largura / altura;
        double desvio = Math.abs(proporcao - PROPORCAO_A4) / PROPORCAO_A4;
        if (desvio > props.layout().toleranciaProporcao()) {
            throw new RegraDeNegocioException(String.format(Locale.ROOT,
                    "Proporção fora do padrão A4: a arte está em %.3f:1 e o esperado é %.3f:1"
                            + " (~3508x2480 px).", proporcao, PROPORCAO_A4));
        }

        return new DimensoesArte(largura, altura);
    }

    public String extensaoDe(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType) ? "jpg" : "png";
    }
}
