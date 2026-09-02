package br.jus.tjgo.goianao.layout.render;

import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.config.GoianaoProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Armazenamento em diretorio local, configurado por {@code goianao.storage.dir}.
 *
 * Deixou de ser o padrao: ativa-se com {@code goianao.storage.tipo=filesystem}.
 * Serve a quem roda fora de container e prefere ver os arquivos, ou a uma
 * eventual instalacao com volume dedicado. Num pod sem volume, o conteudo
 * desaparece no restart — por isso o padrao passou a ser o banco
 * ({@link BancoImageStorage}).
 */
@Component
@ConditionalOnProperty(name = "goianao.storage.tipo", havingValue = "filesystem")
public class FilesystemImageStorage implements ImageStorage {

    private final Path raiz;

    public FilesystemImageStorage(GoianaoProperties props) {
        this.raiz = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar o diretório de artes.", e);
        }
    }

    @Override
    public String salvar(byte[] conteudo, String extensao) {
        String nome = UUID.randomUUID() + "." + normalizarExtensao(extensao);
        try {
            Files.write(raiz.resolve(nome), conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar a arte do certificado.", e);
        }
        return nome;
    }

    @Override
    public byte[] ler(String referencia) {
        Path caminho = resolver(referencia);
        if (!Files.exists(caminho)) {
            throw new NaoEncontradoException("Arte do certificado não encontrada no armazenamento.");
        }
        try {
            return Files.readAllBytes(caminho);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a arte do certificado.", e);
        }
    }

    @Override
    public void remover(String referencia) {
        try {
            Files.deleteIfExists(resolver(referencia));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao remover a arte do certificado.", e);
        }
    }

    /** Impede que uma referencia manipulada escape do diretorio de artes. */
    private Path resolver(String referencia) {
        Path caminho = raiz.resolve(referencia).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("Referência de arte inválida.");
        }
        return caminho;
    }

    private String normalizarExtensao(String extensao) {
        if (extensao == null || extensao.isBlank()) {
            return "png";
        }
        String limpa = extensao.toLowerCase().replaceAll("[^a-z0-9]", "");
        return limpa.isEmpty() ? "png" : limpa;
    }
}
