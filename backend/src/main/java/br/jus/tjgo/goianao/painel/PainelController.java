package br.jus.tjgo.goianao.painel;

import br.jus.tjgo.goianao.certificado.CertificadoEmitidoRepository;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.layout.LayoutRepository;
import br.jus.tjgo.goianao.magistrado.ContagemServidoresHabilitados;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.magistrado.dto.UnidadeReconhecidaResposta;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consolidado de uma edicao para a tela inicial do administrador.
 *
 * <p>Existe para evitar que o frontend precise disparar cinco chamadas e somar
 * numeros no cliente; nao acrescenta regra de negocio, apenas agrega leituras
 * que ja existem nas features 002 a 008.
 */
@RestController
@RequestMapping("/api/painel")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class PainelController {

    private final EdicaoService edicoes;
    private final MagistradoService magistrados;
    private final LayoutRepository layouts;
    private final CertificadoEmitidoRepository certificados;
    private final ContagemServidoresHabilitados contagem;

    public PainelController(EdicaoService edicoes,
                            MagistradoService magistrados,
                            LayoutRepository layouts,
                            CertificadoEmitidoRepository certificados,
                            ContagemServidoresHabilitados contagem) {
        this.edicoes = edicoes;
        this.magistrados = magistrados;
        this.layouts = layouts;
        this.certificados = certificados;
        this.contagem = contagem;
    }

    @GetMapping("/edicoes/{edicaoId}")
    public ResumoEdicaoResposta resumo(@PathVariable Long edicaoId) {
        Edicao edicao = edicoes.buscar(edicaoId);

        List<UnidadeReconhecidaResposta> unidades = magistrados.unidadesReconhecidas(
                edicaoId, unidadeId -> contagem.contar(edicaoId, unidadeId));

        long servidores = unidades.stream()
                .mapToLong(UnidadeReconhecidaResposta::servidoresHabilitados)
                .sum();

        return new ResumoEdicaoResposta(
                edicao.getId(),
                edicao.getAno(),
                edicao.getStatus(),
                edicao.isVigente(),
                layouts.countByEdicaoId(edicaoId),
                edicoes.pendenciasParaPublicar(edicaoId),
                magistrados.listar(edicaoId).size(),
                unidades.size(),
                servidores,
                certificados.countByEdicaoId(edicaoId));
    }
}
