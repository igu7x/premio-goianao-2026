package br.jus.tjgo.goianao.layout.render;

import br.jus.tjgo.goianao.config.GoianaoProperties;
import br.jus.tjgo.goianao.layout.LayoutCertificado;
import br.jus.tjgo.goianao.layout.LayoutRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traz para o banco as artes que ficaram no disco antes da migracao V6.
 *
 * <p>Sem isto, qualquer base criada antes da mudanca fica com os layouts
 * apontando para nomes de arquivo que o {@link BancoImageStorage} nao encontra —
 * e o sintoma nao e um erro na subida, e cada certificado falhando na hora de
 * emitir, que e o pior momento possivel para descobrir.
 *
 * <p>Roda uma vez e se desliga sozinho: na segunda subida nao ha nada faltando
 * e o metodo sai no primeiro <i>if</i>. Nao apaga os arquivos — se algo der
 * errado, o original continua la para uma segunda tentativa.
 */
@Component
@ConditionalOnProperty(name = "goianao.storage.tipo", havingValue = "banco", matchIfMissing = true)
public class ImportadorDeArtesDoDisco {

    private static final Logger log = LoggerFactory.getLogger(ImportadorDeArtesDoDisco.class);

    private final LayoutRepository layouts;
    private final ArteLayoutRepository artes;
    private final Path diretorio;

    public ImportadorDeArtesDoDisco(LayoutRepository layouts, ArteLayoutRepository artes,
                                    GoianaoProperties props) {
        this.layouts = layouts;
        this.artes = artes;
        this.diretorio = Path.of(props.storage().dir()).toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void importar() {
        List<String> pendentes = layouts.findAll().stream()
                .map(LayoutCertificado::getImagemRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .filter(ref -> !artes.existsById(ref))
                .toList();

        if (pendentes.isEmpty()) {
            return;
        }

        if (!Files.isDirectory(diretorio)) {
            log.warn("{} arte(s) de layout não estão no banco e o diretório {} não existe. "
                            + "Os certificados dessas combinações vão falhar na emissão.",
                    pendentes.size(), diretorio);
            return;
        }

        int trazidas = 0;
        for (String referencia : pendentes) {
            Path arquivo = diretorio.resolve(referencia).normalize();
            if (!arquivo.startsWith(diretorio) || !Files.isRegularFile(arquivo)) {
                log.warn("Arte {} não encontrada em disco; a emissão dessa combinação vai falhar.",
                        referencia);
                continue;
            }
            try {
                artes.save(new ArteLayout(referencia, Files.readAllBytes(arquivo),
                        extensaoDe(referencia)));
                trazidas++;
            } catch (IOException e) {
                log.warn("Falha ao ler a arte {} do disco.", referencia, e);
            }
        }

        if (trazidas > 0) {
            log.info("Artes migradas do disco para o banco: {} de {}. "
                            + "Os arquivos em {} não são mais usados e podem ser removidos.",
                    trazidas, pendentes.size(), diretorio);
        }
    }

    private static String extensaoDe(String referencia) {
        int ponto = referencia.lastIndexOf('.');
        return ponto > 0 && ponto < referencia.length() - 1
                ? referencia.substring(ponto + 1).toLowerCase()
                : "png";
    }

}
