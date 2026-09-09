package br.jus.tjgo.goianao.servidor;

import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.magistrado.Reconhecimento;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import br.jus.tjgo.goianao.servidor.dto.ListaHabilitadosResposta;
import br.jus.tjgo.goianao.servidor.dto.ServidorHabilitadoResposta;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visao do magistrado sobre as listas das <b>suas</b> unidades (008/RF-4). O
 * contexto padrao e a edicao vigente, que e a unica em que ele pode editar.
 */
@RestController
@RequestMapping("/api/magistrado/servidores")
@PreAuthorize("hasRole('MAGISTRADO')")
public class MagistradoServidoresController {

    private final ServidorHabilitadoService servico;
    private final MagistradoService magistrados;
    private final EdicaoService edicoes;
    private final UnidadeService unidadesDoCadastro;

    public MagistradoServidoresController(ServidorHabilitadoService servico,
                                          MagistradoService magistrados,
                                          EdicaoService edicoes,
                                          UnidadeService unidadesDoCadastro) {
        this.servico = servico;
        this.magistrados = magistrados;
        this.edicoes = edicoes;
        this.unidadesDoCadastro = unidadesDoCadastro;
    }

    @GetMapping
    public List<ListaHabilitadosResposta> minhasUnidades(
            @RequestParam(required = false) Long edicaoId) {

        Edicao edicao = edicoes.resolverAlvo(edicaoId);
        String cpf = UsuarioAtual.obrigatorio().cpf();

        /*
         * As unidades do magistrado vêm de dois lugares, sem repetir:
         *
         * 1. aquelas pelas quais ele foi reconhecido nesta edição;
         * 2. aquelas pelas quais ele responde, por designação do
         *    superadministrador no cadastro de unidades.
         *
         * As designadas só entram quando a unidade foi reconhecida na edição —
         * fora disso não existe lista de habilitados para gerenciar, e mostrar
         * um cartão que não leva a lugar algum seria pior do que omiti-lo.
         */
        Map<Long, String> unidades = new LinkedHashMap<>();
        for (Reconhecimento r : magistrados.reconhecimentosDe(edicao.getId(), cpf)) {
            unidades.putIfAbsent(r.getUnidade().getId(), r.getUnidade().getNome());
        }
        for (UnidadeJudiciaria u : unidadesDoCadastro.unidadesSobResponsabilidade(cpf)) {
            if (magistrados.unidadeEhReconhecida(edicao.getId(), u.getId())) {
                unidades.putIfAbsent(u.getId(), u.getNome());
            }
        }

        List<ListaHabilitadosResposta> resposta = new ArrayList<>();
        unidades.forEach((unidadeId, nome) -> {
            boolean podeEditar = servico.podeEditar(edicao, unidadeId);
            resposta.add(new ListaHabilitadosResposta(
                    edicao.getId(),
                    edicao.getAno(),
                    unidadeId,
                    nome,
                    podeEditar,
                    UsuarioAtual.obrigatorio().ehAdministrador(),
                    servico.listar(edicao.getId(), unidadeId).stream()
                            .map(servidor -> ServidorHabilitadoResposta.de(servidor, podeEditar))
                            .toList()));
        });
        return resposta;
    }
}
