package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoService;
import br.jus.tjgo.goianao.servidor.dto.SemeaduraResposta;
import br.jus.tjgo.goianao.unidade.dto.DesignacaoResposta;
import br.jus.tjgo.goianao.unidade.dto.UnidadeResposta;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Designar o responsavel e, no mesmo ato, semear a lista de habilitados da
 * unidade (008/RF-1a).
 *
 * <p>Mora aqui, e nao no {@link UnidadeService}, por uma razao de dependencia: o
 * servico de habilitados ja depende do de unidades, e chamar de volta fecharia o
 * ciclo. Este coordena os dois sem que nenhum precise conhecer o outro.
 *
 * <p>A semeadura <b>nao</b> derruba a designacao. O EGESP e servico externo: se
 * ele estiver fora do ar, o responsavel foi designado do mesmo jeito e a lista
 * pode ser semeada depois pelo botao da tela dele.
 */
@Service
public class DesignacaoService {

    private static final Logger log = LoggerFactory.getLogger(DesignacaoService.class);

    private final UnidadeService unidades;
    private final ServidorHabilitadoService servidores;
    private final EdicaoService edicoes;

    public DesignacaoService(UnidadeService unidades, ServidorHabilitadoService servidores,
                             EdicaoService edicoes) {
        this.unidades = unidades;
        this.servidores = servidores;
        this.edicoes = edicoes;
    }

    /**
     * @param edicaoId edicao em que a lista sera semeada; nulo usa a vigente.
     *                 Sem nenhuma das duas a designacao acontece assim mesmo, e
     *                 o aviso explica por que a lista nao veio
     */
    public DesignacaoResposta designar(Long unidadeId, Long usuarioId, Long edicaoId) {
        UnidadeJudiciaria unidade = unidades.designarResponsavel(unidadeId, usuarioId);
        UnidadeResposta resposta = UnidadeResposta.de(unidade);

        Optional<Edicao> alvo = edicaoAlvo(edicaoId);
        if (alvo.isEmpty()) {
            return new DesignacaoResposta(resposta, null,
                    "Responsável designado. A lista de servidores não foi semeada porque não há "
                            + "edição vigente definida.");
        }

        try {
            SemeaduraResposta semeadura = servidores.semear(alvo.get().getId(), unidadeId);
            return new DesignacaoResposta(resposta, semeadura, null);
        } catch (RuntimeException e) {
            log.warn("Responsavel designado na unidade {}, porem a semeadura falhou.",
                    unidadeId, e);
            return new DesignacaoResposta(resposta, null,
                    "Responsável designado, mas a lista não pôde ser semeada agora: "
                            + (e.getMessage() == null ? "falha ao consultar o RH." : e.getMessage())
                            + " Use “Semear do EGESP” quando o RH responder.");
        }
    }

    private Optional<Edicao> edicaoAlvo(Long edicaoId) {
        return edicaoId != null ? Optional.of(edicoes.buscar(edicaoId)) : edicoes.vigente();
    }
}
