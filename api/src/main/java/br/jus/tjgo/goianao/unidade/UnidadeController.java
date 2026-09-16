package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.unidade.dto.ImportacaoResponsaveis;
import br.jus.tjgo.goianao.unidade.dto.UnidadeCadastrada;
import br.jus.tjgo.goianao.unidade.dto.UnidadeEgespResposta;
import br.jus.tjgo.goianao.unidade.dto.UnidadeResposta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/unidades")
public class UnidadeController {

    private final UnidadeService servico;
    private final ImportacaoResponsaveisService importacao;

    public UnidadeController(UnidadeService servico, ImportacaoResponsaveisService importacao) {
        this.servico = servico;
        this.importacao = importacao;
    }

    /** Catalogo do EGESP, para o autocomplete do administrador (004/RF-1). */
    @GetMapping("/egesp")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<UnidadeEgespResposta> listarDoEgesp(@RequestParam(required = false) String q) {
        return servico.listarDoEgesp(q);
    }

    /**
     * Cadastro de unidades — exclusivo do superadministrador.
     *
     * Restrito porque a resposta traz quem responde por cada unidade, com nome e
     * e-mail: e o mapa de quem manda em quê, não uma lista de nomes de vara.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<UnidadeResposta> listar() {
        return servico.listarLocais().stream().map(UnidadeResposta::de).toList();
    }

    /**
     * As unidades cadastradas no sistema, para escolher uma num formulario.
     *
     * <p>Os formularios perguntavam ao RH ao vivo, e quando a API corporativa
     * falhava a lista vinha vazia sem aviso nenhum. As unidades entram no
     * sistema pela sincronizacao, com codigo; e daqui que as telas devem
     * escolher. Aberta ao administrador porque nao traz responsavel.
     */
    @GetMapping("/cadastradas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<UnidadeCadastrada> cadastradas() {
        return servico.listarLocais().stream().map(UnidadeCadastrada::de).toList();
    }

    /** Uma unidade, para a pagina dela. Mesma restricao da listagem. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UnidadeResposta buscar(@PathVariable Long id) {
        return UnidadeResposta.de(servico.buscar(id));
    }

    /**
     * Designa o superior responsavel pela unidade.
     *
     * A partir daqui o magistrado designado passa a ver a unidade na aba
     * "Servidores da unidade" e a poder gerenciar a lista de habilitados dela —
     * mesmo sem ter sido reconhecido no premio por ela.
     */
    @PutMapping("/{id}/responsavel")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UnidadeResposta designarResponsavel(@PathVariable Long id,
                                               @Valid @RequestBody ResponsavelRequisicao dados) {
        return UnidadeResposta.de(servico.designarResponsavel(id, dados.usuarioId()));
    }

    @DeleteMapping("/{id}/responsavel")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UnidadeResposta removerResponsavel(@PathVariable Long id) {
        return UnidadeResposta.de(servico.removerResponsavel(id));
    }

    /**
     * Planilha de quem responde por cada unidade.
     *
     * Designar uma a uma nao e caminho com centenas de unidades; e a lista vem
     * pronta do tribunal, em planilha. Cria o magistrado que ainda nao existe no
     * sistema — sem isso, seria preciso cadastrar cada um antes, a mao, so para
     * poder designa-lo.
     */
    @PostMapping(path = "/responsaveis/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ImportacaoResponsaveis importarResponsaveis(
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestParam(required = false) Long edicaoId) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie o arquivo CSV com os responsáveis.");
        }
        try {
            return importacao.importar(arquivo.getBytes(), edicaoId);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    public record ResponsavelRequisicao(
            @NotNull(message = "informe o usuário") Long usuarioId) {}
}
