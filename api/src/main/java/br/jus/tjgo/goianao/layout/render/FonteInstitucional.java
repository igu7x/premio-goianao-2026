package br.jus.tjgo.goianao.layout.render;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Fonte unica de todo texto impresso no certificado (003/RF-3: o administrador
 * nao escolhe fonte, cor nem tamanho).
 *
 * <p>Se o TTF institucional do TJGO estiver em
 * {@code classpath:fontes/institucional.ttf}, ele e embarcado no PDF. Enquanto o
 * arquivo/licenca nao e fornecido, cai para uma fonte padrao do PDF — a troca e
 * apenas soltar o arquivo no lugar, sem mudar codigo.
 */
@Component
public class FonteInstitucional {

    private static final Logger log = LoggerFactory.getLogger(FonteInstitucional.class);
    private static final String CAMINHO = "fontes/institucional.ttf";

    /**
     * Carrega a fonte para um documento. PDFBox exige que fontes embarcadas
     * pertencam ao documento em que serao usadas, entao a carga e por PDF.
     */
    public PDFont carregar(PDDocument documento) {
        ClassPathResource recurso = new ClassPathResource(CAMINHO);
        if (recurso.exists()) {
            try (InputStream in = recurso.getInputStream()) {
                return PDType0Font.load(documento, in, true);
            } catch (IOException e) {
                log.warn("Falha ao carregar a fonte institucional; usando a fonte padrao.", e);
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    public boolean institucionalDisponivel() {
        return new ClassPathResource(CAMINHO).exists();
    }
}
