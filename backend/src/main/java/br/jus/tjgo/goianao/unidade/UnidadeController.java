package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.unidade.dto.UnidadeEgespResposta;
import br.jus.tjgo.goianao.unidade.dto.UnidadeResposta;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** Unidades ja espelhadas localmente (as que foram usadas em algum cadastro). */
    @GetMapping
    public List<UnidadeResposta> listar() {
        return servico.listarLocais().stream().map(UnidadeResposta::de).toList();
    }
}
