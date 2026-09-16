package br.jus.tjgo.goianao.usuario.atualizacao;

import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import br.jus.tjgo.goianao.integracao.egesp.UnidadeEgesp;
import br.jus.tjgo.goianao.sincronizacao.SincronizacaoService;
import br.jus.tjgo.goianao.sincronizacao.dto.LotacaoAplicada;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Atualiza a base de usuarios inteira a partir do RH: todos os lotados de
 * todas as unidades do escopo viram usuarios, com a lotacao gravada.
 *
 * <p><b>Roda em segundo plano, e nao na requisicao.</b> Cada unidade custa uma
 * chamada, cada pessoa outra — e mais uma no AD para quem o RH nao tem e-mail —,
 * com teto de seis chamadas por segundo. O ramo do TJGO leva minutos; a base
 * completa, dezenas de minutos. A rota cairia muito antes. A tela dispara, e
 * depois so pergunta como esta.
 *
 * <p><b>Uma de cada vez.</b> Duas varreduras simultaneas dividiriam a mesma cota
 * de chamadas e demorariam o dobro cada uma, gravando as mesmas pessoas.
 *
 * <p><b>Cada unidade e independente.</b> A consulta ao RH fica fora de
 * transacao, e a gravacao de cada unidade tem a sua: uma unidade que falhe entra
 * na contagem e a varredura segue, sem desfazer o que ja foi gravado. E como
 * gravar a mesma pessoa de novo so atualiza, rodar outra vez depois de uma queda
 * e seguro.
 *
 * <p>O estado fica em memoria. Se o pod reiniciar no meio, o progresso some da
 * tela, mas nada do que foi gravado se perde — basta disparar de novo.
 */
@Service
public class AtualizacaoDaBaseDeUsuarios {

    private static final Logger log = LoggerFactory.getLogger(AtualizacaoDaBaseDeUsuarios.class);

    /**
     * Raiz do TJGO no organograma do RH. E a mesma da tela de sincronizacao: traz
     * a estrutura do tribunal, mas nao as varas, que ficam sob as comarcas.
     */
    public static final long CODIGO_TJGO = 600_000_009L;

    private final EgespClient egesp;
    private final SincronizacaoService sincronizacao;

    /** Uma thread so: e ela que garante uma varredura de cada vez, sem ocupar o
     *  pool das tarefas de login, que nao podem esperar meia hora na fila. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "goianao-atualizacao-da-base");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicReference<SituacaoDaAtualizacao> situacao =
            new AtomicReference<>(SituacaoDaAtualizacao.nuncaExecutada());

    public AtualizacaoDaBaseDeUsuarios(EgespClient egesp, SincronizacaoService sincronizacao) {
        this.egesp = egesp;
        this.sincronizacao = sincronizacao;
    }

    public SituacaoDaAtualizacao situacao() {
        return situacao.get();
    }

    public synchronized SituacaoDaAtualizacao iniciar(EscopoDaAtualizacao escopo) {
        if (situacao.get().estado() == SituacaoDaAtualizacao.Estado.EM_ANDAMENTO) {
            throw new ConflitoException("Já há uma atualização da base em andamento. Espere ela "
                    + "terminar: duas ao mesmo tempo dividiriam a cota de chamadas ao RH e "
                    + "levariam o dobro do tempo cada uma.");
        }
        situacao.set(SituacaoDaAtualizacao.iniciada(escopo));
        executor.submit(() -> varrer(escopo));
        return situacao.get();
    }

    private void varrer(EscopoDaAtualizacao escopo) {
        try {
            List<UnidadeEgesp> doRh = escopo == EscopoDaAtualizacao.TJGO
                    ? egesp.hierarquia(CODIGO_TJGO)
                    : egesp.organogramaCompleto();

            // Unidade repetida na resposta nao e varrida duas vezes.
            Map<Long, UnidadeEgesp> unidades = new LinkedHashMap<>();
            for (UnidadeEgesp unidade : doRh) {
                if (unidade.codigo() != null) {
                    unidades.putIfAbsent(unidade.codigo(), unidade);
                }
            }
            situacao.updateAndGet(s -> s.comTotal(unidades.size()));
            log.info("Atualizacao da base ({}): {} unidade(s) a varrer.", escopo, unidades.size());

            // Quem ja foi gravado nesta varredura nao e gravado de novo. Lotacao e
            // um campo so, e pessoa que aparece em duas unidades ficaria com a
            // ultima varrida — uma escolha arbitraria que mudaria a cada rodada.
            // Assim vence a primeira, na ordem do organograma, sempre a mesma.
            Set<String> jaGravados = new HashSet<>();

            for (UnidadeEgesp unidade : unidades.values()) {
                situacao.updateAndGet(s -> s.naUnidade(unidade.nome()));
                try {
                    List<ServidorEgesp> pessoas = egesp.servidoresPorCodigo(unidade.codigo())
                            .stream()
                            .filter(p -> !Email.valido(p.email())
                                    || jaGravados.add(Email.normalizar(p.email())))
                            .toList();
                    LotacaoAplicada gravada = sincronizacao.gravarLotados(pessoas, unidade.nome());
                    situacao.updateAndGet(s -> s.somar(gravada));
                } catch (RuntimeException e) {
                    log.warn("Atualizacao da base: falha na unidade {} ({}): {}",
                            unidade.codigo(), unidade.nome(), e.getMessage());
                    situacao.updateAndGet(SituacaoDaAtualizacao::comFalhaNaUnidade);
                }
            }

            situacao.updateAndGet(SituacaoDaAtualizacao::concluida);
            SituacaoDaAtualizacao fim = situacao.get();
            log.info("Atualizacao da base ({}) concluida: {} criado(s), {} atualizado(s), "
                    + "{} sem e-mail, {} unidade(s) com falha.", escopo, fim.criados(),
                    fim.atualizados(), fim.semEmail(), fim.unidadesComFalha());
        } catch (RuntimeException e) {
            log.error("Atualizacao da base ({}) interrompida.", escopo, e);
            situacao.updateAndGet(s -> s.falhou(e.getMessage() == null
                    ? "A consulta ao RH falhou." : e.getMessage()));
        }
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    /** Para os testes: espera a varredura em curso sem depender de relogio. */
    boolean emAndamento() {
        return situacao.get().estado() == SituacaoDaAtualizacao.Estado.EM_ANDAMENTO;
    }

    static LocalDateTime agora() {
        return LocalDateTime.now();
    }
}
