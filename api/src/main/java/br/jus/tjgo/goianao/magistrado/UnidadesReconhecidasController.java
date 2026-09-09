package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.magistrado.dto.UnidadeReconhecidaResposta;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panorama das unidades reconhecidas de uma edicao (004/RF-9). E o ponto de
 * partida do administrador para gerenciar a lista de servidores habilitados
 * (008) e a origem do maior selo usado na emissao do servidor (006).
 */
@RestController
@RequestMapping("/api/edicoes/{edicaoId}/unidades-reconhecidas")
public class UnidadesReconhecidasController {

    private final MagistradoService servico;
    private final ContagemServidoresHabilitados contagem;

    public UnidadesReconhecidasController(MagistradoService servico,
                                          ContagemServidoresHabilitados contagem) {
        this.servico = servico;
        this.contagem = contagem;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'MAGISTRADO')")
    public List<UnidadeReconhecidaResposta> listar(@PathVariable Long edicaoId) {
        return servico.unidadesReconhecidas(edicaoId,
                unidadeId -> contagem.contar(edicaoId, unidadeId));
    }
}
