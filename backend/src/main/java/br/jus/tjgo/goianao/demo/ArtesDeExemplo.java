package br.jus.tjgo.goianao.demo;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * As oito artes de exemplo, uma por combinacao selo x tipo.
 *
 * <p>Sao as pecas de verdade fornecidas pela comunicacao do tribunal, rendidas
 * do PDF original em A4 paisagem a 300 DPI — o mesmo padrao que o
 * {@code ValidadorDeArte} exige de quem sobe arte pela tela. Substituiram a arte
 * gerada em codigo: com um desenho real, o posicionamento das caixas de texto
 * deixa de ser chute e pode ser conferido olhando o PDF emitido.
 *
 * <p>Elas existem para a carga de demonstracao e para exercitar o editor. As
 * definitivas de cada edicao sao enviadas pelo administrador e vao para o banco,
 * nunca para o repositorio.
 */
@Component
public class ArtesDeExemplo {

    /** Dimensoes das artes: A4 paisagem a 300 DPI. */
    public static final int LARGURA = 3507;
    public static final int ALTURA = 2480;

    public static final String EXTENSAO = "jpg";

    private static final String CAMINHO = "artes-exemplo/arte-%s-%s.jpg";

    public byte[] carregar(Selo selo, TipoCertificado tipo) {
        String caminho = String.format(CAMINHO,
                selo.name().toLowerCase(Locale.ROOT),
                tipo.name().toLowerCase(Locale.ROOT));

        try (InputStream entrada = new ClassPathResource(caminho).getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Arte de exemplo ausente no classpath: " + caminho, e);
        }
    }
}
