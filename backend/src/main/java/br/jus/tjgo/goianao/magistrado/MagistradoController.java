package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.magistrado.dto.ImportacaoResposta;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoResposta;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Cadastro de reconhecidos: exclusivo do Administrador (004/RNF-1). */
@RestController
@RequestMapping("/api/edicoes/{edicaoId}/magistrados")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class MagistradoController {

    private final MagistradoService servico;
    private final ImportacaoMagistradosService importacao;

    public MagistradoController(MagistradoService servico,
                                ImportacaoMagistradosService importacao) {
        this.servico = servico;
        this.importacao = importacao;
    }

    @GetMapping
    public List<MagistradoResposta> listar(@PathVariable Long edicaoId) {
        return servico.listar(edicaoId).stream().map(MagistradoResposta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MagistradoResposta criar(@PathVariable Long edicaoId,
                                    @Valid @RequestBody MagistradoRequisicao requisicao) {
        return MagistradoResposta.de(servico.criar(edicaoId, requisicao));
    }

    /** Inclusao aditiva de unidade a um magistrado ja cadastrado (009/RF-2). */
    @PostMapping("/{magistradoId}/reconhecimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public MagistradoResposta adicionarReconhecimento(
            @PathVariable Long edicaoId,
            @PathVariable Long magistradoId,
            @Valid @RequestBody ReconhecimentoRequisicao requisicao) {
        return MagistradoResposta.de(
                servico.adicionarReconhecimento(edicaoId, magistradoId, requisicao));
    }

    @PutMapping("/{magistradoId}")
    public MagistradoResposta atualizar(@PathVariable Long edicaoId,
                                        @PathVariable Long magistradoId,
                                        @Valid @RequestBody MagistradoRequisicao requisicao) {
        return MagistradoResposta.de(servico.atualizar(edicaoId, magistradoId, requisicao));
    }

    @DeleteMapping("/{magistradoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long edicaoId, @PathVariable Long magistradoId) {
        servico.remover(edicaoId, magistradoId);
    }

    @PostMapping(path = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoResposta importar(@PathVariable Long edicaoId,
                                       @RequestPart("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie o arquivo CSV com os reconhecidos.");
        }
        try {
            return importacao.importar(edicaoId, arquivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }
}
