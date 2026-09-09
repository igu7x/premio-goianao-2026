package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.unidade.dto.UnidadeEgespResposta;
import br.jus.tjgo.goianao.unidade.dto.UnidadeResposta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/unidades")
public class UnidadeController {

    private final UnidadeService servico;

    public UnidadeController(UnidadeService servico) {
        this.servico = servico;
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

    public record ResponsavelRequisicao(
            @NotNull(message = "informe o usuário") Long usuarioId) {}
}
