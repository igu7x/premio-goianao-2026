package br.jus.tjgo.goianao.servidor;

import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.servidor.dto.IncluirServidorRequisicao;
import br.jus.tjgo.goianao.servidor.dto.ListaHabilitadosResposta;
import br.jus.tjgo.goianao.servidor.dto.SemeaduraResposta;
import br.jus.tjgo.goianao.servidor.dto.ServidorHabilitadoResposta;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestao da lista de servidores habilitados de uma unidade (008).
 *
 * <p>Acessivel a Administrador e Magistrado; o escopo fino (magistrado so nas
 * suas unidades e so na edicao vigente) e decidido no servico, porque depende
 * dos reconhecimentos e do estado da edicao.
 */
@RestController
@RequestMapping("/api/edicoes/{edicaoId}/unidades/{unidadeId}/servidores")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'MAGISTRADO')")
public class ServidorHabilitadoController {

    private final ServidorHabilitadoService servico;
    private final EdicaoService edicoes;
    private final UnidadeService unidades;

    public ServidorHabilitadoController(ServidorHabilitadoService servico,
                                        EdicaoService edicoes,
                                        UnidadeService unidades) {
        this.servico = servico;
        this.edicoes = edicoes;
        this.unidades = unidades;
    }

    @GetMapping
    public ListaHabilitadosResposta listar(@PathVariable Long edicaoId,
                                           @PathVariable Long unidadeId) {
        Edicao edicao = edicoes.buscar(edicaoId);
        UnidadeJudiciaria unidade = unidades.buscar(unidadeId);
        boolean podeEditar = servico.podeEditar(edicao, unidadeId);

        return new ListaHabilitadosResposta(
                edicaoId,
                edicao.getAno(),
                unidadeId,
                unidade.getNome(),
                podeEditar,
                UsuarioAtual.obrigatorio().ehAdministrador(),
                servico.listar(edicaoId, unidadeId).stream()
                        .map(servidor -> ServidorHabilitadoResposta.de(servidor, podeEditar))
                        .toList());
    }

    /** Semeadura a partir do EGESP: operacao do administrador (008/plan). */
    @PostMapping("/semear")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public SemeaduraResposta semear(@PathVariable Long edicaoId, @PathVariable Long unidadeId) {
        return servico.semear(edicaoId, unidadeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServidorHabilitadoResposta incluir(
            @PathVariable Long edicaoId,
            @PathVariable Long unidadeId,
            @Valid @RequestBody IncluirServidorRequisicao requisicao) {
        // Quem chegou aqui passou pela guarda de escopo, entao pode ver o CPF.
        return ServidorHabilitadoResposta.de(
                servico.incluir(edicaoId, unidadeId, requisicao.cpf(), requisicao.nome()), true);
    }

    @DeleteMapping("/{cpf}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long edicaoId, @PathVariable Long unidadeId,
                        @PathVariable String cpf) {
        servico.remover(edicaoId, unidadeId, cpf);
    }
}
