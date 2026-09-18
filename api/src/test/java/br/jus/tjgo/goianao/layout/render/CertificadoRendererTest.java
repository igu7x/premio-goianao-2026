package br.jus.tjgo.goianao.layout.render;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.layout.Alinhamento;
import br.jus.tjgo.goianao.layout.AreaCodigo;
import br.jus.tjgo.goianao.layout.AreaQr;
import br.jus.tjgo.goianao.layout.AreaTexto;
import br.jus.tjgo.goianao.layout.LayoutCertificado;
import br.jus.tjgo.goianao.suporte.ArteDeTeste;
import java.util.HashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Composicao do certificado em PDF")
class CertificadoRendererTest {

    /** Storage em memoria: o teste e sobre o desenho, nao sobre disco. */
    private static final class StorageEmMemoria implements ImageStorage {
        private final Map<String, byte[]> arquivos = new HashMap<>();

        @Override
        public String salvar(byte[] conteudo, String extensao) {
            String referencia = "arte-" + arquivos.size() + "." + extensao;
            arquivos.put(referencia, conteudo);
            return referencia;
        }

        @Override
        public byte[] ler(String referencia) {
            return arquivos.get(referencia);
        }

        @Override
        public void remover(String referencia) {
            arquivos.remove(referencia);
        }
    }

    private StorageEmMemoria storage;
    private CertificadoRenderer renderer;

    @BeforeEach
    void preparar() {
        storage = new StorageEmMemoria();
        renderer = new CertificadoRenderer(storage, new FonteInstitucional(), new GeradorQrCode());
    }

    @Test
    @DisplayName("gera PDF A4 paisagem com nome, unidade e codigo impressos")
    void geraPdfComOsTresCampos() throws Exception {
        LayoutCertificado layout = layout(new AreaTexto(454, 1150, 2600, 150, Alinhamento.CENTRO));

        byte[] pdf = renderer.renderizar(layout, new CertificadoRenderer.DadosCertificado(
                "Rafael Siqueira Bittencourt",
                "1a Vara Civel da Comarca de Goiania",
                "47RR-CTCH-321N",
                "https://goianao.tjgo.jus.br/verificar/47RR-CTCH-321N"));

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);

            PDRectangle pagina = documento.getPage(0).getMediaBox();
            assertThat(pagina.getWidth()).isGreaterThan(pagina.getHeight());
            assertThat(pagina.getWidth()).isCloseTo(PDRectangle.A4.getHeight(), within());

            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto)
                    .contains("Rafael Siqueira Bittencourt")
                    .contains("1a Vara Civel da Comarca de Goiania")
                    .contains("47RR-CTCH-321N");
        }
    }

    @Test
    @DisplayName("nome muito longo e reduzido para caber, sem quebrar a emissao")
    void nomeLongoNaoQuebra() throws Exception {
        // Caixa estreita de proposito: forca o auto-ajuste ate o limite (003/RF-3).
        LayoutCertificado layout = layout(new AreaTexto(454, 1150, 400, 60, Alinhamento.CENTRO));

        byte[] pdf = renderer.renderizar(layout, new CertificadoRenderer.DadosCertificado(
                "Maria das Gracas Albuquerque Fontes Vasconcelos de Andrade Cavalcanti",
                "Vara Unica", "ABCD-1234-EFGH", "https://exemplo/verificar/ABCD-1234-EFGH"));

        assertThat(pdf).isNotEmpty();
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(documento)).contains("Vara Unica");
        }
    }

    @Test
    @DisplayName("caractere fora do repertorio da fonte nao derruba a emissao")
    void caractereExoticoNaoQuebra() {
        LayoutCertificado layout = layout(new AreaTexto(454, 1150, 2600, 150, Alinhamento.CENTRO));

        byte[] pdf = renderer.renderizar(layout, new CertificadoRenderer.DadosCertificado(
                "Nome com simbolo raro ☃ no meio", "Vara Unica",
                "ABCD-1234-EFGH", "https://exemplo/verificar/ABCD-1234-EFGH"));

        assertThat(pdf).isNotEmpty();
    }

    private LayoutCertificado layout(AreaTexto areaNome) {
        String referencia = storage.salvar(ArteDeTeste.valida(), "png");
        return new LayoutCertificado(
                new Edicao(2025, "teste", "edicao_2025"), Selo.OURO, TipoCertificado.MAGISTRADO,
                referencia, ArteDeTeste.LARGURA, ArteDeTeste.ALTURA,
                areaNome,
                new AreaTexto(454, 1495, 2600, 110, Alinhamento.CENTRO),
                new AreaCodigo(520, 2230, 1000, 50, Alinhamento.ESQUERDA,
                        new AreaQr(280, 2080, 200)));
    }

    private static org.assertj.core.data.Offset<Float> within() {
        return org.assertj.core.data.Offset.offset(1f);
    }
}
