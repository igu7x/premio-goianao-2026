package br.jus.tjgo.goianao.sincronizacao;

import br.jus.tjgo.goianao.sincronizacao.dto.ComparacaoServidores;
import br.jus.tjgo.goianao.sincronizacao.dto.ImportacaoDaUnidade;
import br.jus.tjgo.goianao.sincronizacao.dto.SituacaoIntegracao;
import br.jus.tjgo.goianao.sincronizacao.dto.UnidadeComparada;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.dto.UnidadeResposta;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tela de sincronizacao com o RH (010). Exclusiva do superadministrador: e ela
 * que pode renomear unidade impressa em certificado e mexer em quem tem direito
 * de emitir.
 *
 * <p>Os GET apenas comparam — nao gravam nada. Cada alteracao tem seu proprio
 * verbo e acontece uma de cada vez, a pedido.
 */
@RestController
@RequestMapping("/api/sincronizacao")
@PreAuthorize("hasRole('SUPERADMIN')")
public class SincronizacaoController {

    private final SincronizacaoService servico;

    public SincronizacaoController(SincronizacaoService servico) {
        this.servico = servico;
    }

    public record CadastrarUnidadeRequisicao(@NotNull(message = "informe o código") Long codigo) {}

    public record ImportarRequisicao(@NotNull(message = "informe a edição") Long edicaoId) {}

    public record IncluirServidorRequisicao(
            @NotNull(message = "informe a edição") Long edicaoId,
            @NotNull(message = "informe a matrícula") Long matricula) {}

    @GetMapping("/situacao")
    public SituacaoIntegracao situacao() {
        return servico.situacao();
    }

    @GetMapping("/unidades")
    public List<UnidadeComparada> unidades(@RequestParam long codigo) {
        return servico.compararUnidades(codigo);
    }

    @GetMapping("/unidades/{unidadeId}/servidores")
    public ComparacaoServidores servidores(@PathVariable Long unidadeId,
                                           @RequestParam Long edicaoId) {
        return servico.compararServidores(unidadeId, edicaoId);
    }

    @PostMapping("/unidades")
    @ResponseStatus(HttpStatus.CREATED)
    public UnidadeResposta cadastrarUnidade(@RequestBody CadastrarUnidadeRequisicao requisicao) {
        return UnidadeResposta.de(servico.cadastrarUnidade(requisicao.codigo()));
    }

    @PutMapping("/unidades/{unidadeId}")
    public UnidadeResposta atualizarUnidade(@PathVariable Long unidadeId) {
        UnidadeJudiciaria unidade = servico.atualizarUnidade(unidadeId);
        return UnidadeResposta.de(unidade);
    }

    @PostMapping("/unidades/{unidadeId}/servidores")
    @ResponseStatus(HttpStatus.CREATED)
    public void incluirServidor(@PathVariable Long unidadeId,
                                @RequestBody IncluirServidorRequisicao requisicao) {
        servico.incluirServidor(requisicao.edicaoId(), unidadeId, requisicao.matricula());
    }

    /**
     * A unidade inteira de uma vez: cria os usuarios que faltam e habilita
     * todos na edicao. E a carga inicial; os botoes item a item continuam
     * valendo para o ajuste fino depois.
     */
    @PostMapping("/unidades/{unidadeId}/importar")
    public ImportacaoDaUnidade importar(@PathVariable Long unidadeId,
                                        @RequestBody ImportarRequisicao requisicao) {
        return servico.importarUnidade(unidadeId, requisicao.edicaoId());
    }

    /** Pelo id do item: dado pessoal nao vai na URL (DI-10). */
    @DeleteMapping("/servidores/{servidorHabilitadoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@PathVariable Long servidorHabilitadoId) {
        servico.desvincular(servidorHabilitadoId);
    }
}
