package br.jus.tjgo.goianao.layout;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * As oito artes padrao do premio, uma por combinacao selo x tipo.
 *
 * <p>Sao as pecas de verdade fornecidas pela comunicacao do tribunal, rendidas
 * do PDF original em A4 paisagem a 300 DPI — o mesmo padrao que o
 * {@code ValidadorDeArte} exige de quem sobe arte pela tela (DI-18).
 *
 * <p>Elas nascem no classpath, e nao no banco, porque servem a dois usos que
 * antecedem qualquer cadastro: a carga de demonstracao e o botao que preenche
 * uma edicao nova com o padrao do premio, para que o administrador tenha de
 * onde partir em vez de oito quadros vazios. A arte definitiva de uma edicao,
 * quando existir, e enviada pela tela e substitui esta no banco.
 *
 * <p>Esta classe morava no pacote {@code demo}; saiu de la quando deixou de ser
 * so de demonstracao — codigo de producao nao pode depender de algo que um dia
 * alguem apague junto com a carga de exemplo. A pasta de recursos manteve o
 * nome historico.
 */
@Component
public class ArtesPadrao {

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
                    "Arte padrao ausente no classpath: " + caminho, e);
        }
    }

    /*
     * Caixas medidas sobre a arte (3507x2480).
     *
     * A peca ja traz o texto fixo: "A Presidencia ... reconhece que" termina por
     * volta de y=970 e "Conquistou o Selo ..." comeca em y=1480. O vao entre os
     * dois e onde entram nome e unidade — e e por isso que as caixas comecam em
     * x=1250, alinhadas a esquerda com o restante do paragrafo, em vez de
     * centradas na pagina: centrar deixaria o nome fora do eixo do texto que vem
     * antes e depois dele.
     *
     * O codigo e o QR vao para o rodape branco, no vao entre a assinatura e a
     * marca do premio — a unica area livre da peca.
     *
     * Elas sao o ponto de partida do editor visual: com a arte definitiva de uma
     * edicao, o administrador arrasta e redimensiona a partir daqui.
     */
    public static AreaTexto areaNome() {
        return new AreaTexto(1250, 1030, 1870, 170, Alinhamento.ESQUERDA);
    }

    public static AreaTexto areaUnidade() {
        return new AreaTexto(1250, 1235, 1870, 110, Alinhamento.ESQUERDA);
    }

    public static AreaCodigo areaCodigo() {
        return new AreaCodigo(2545, 2215, 460, 44, Alinhamento.CENTRO,
                new AreaQr(2660, 1955, 230));
    }
}
