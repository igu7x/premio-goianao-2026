package br.jus.tjgo.goianao.servidor;

import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import br.jus.tjgo.goianao.servidor.dto.SemeaduraResposta;
import br.jus.tjgo.goianao.servidor.dto.SituacaoDaSemeadura;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Semeia a lista de varias unidades de uma vez, <b>em segundo plano</b>.
 *
 * <p>Existe por causa da planilha de responsaveis. Semear uma unidade e barato;
 * semear as 190 da planilha inteira, nao: cada uma e uma consulta ao RH — mais
 * uma por matricula sem e-mail —, com teto de seis chamadas por segundo. Na
 * requisicao, isso passava de minutos e a rota do OpenShift derrubava a conexao
 * antes da resposta; no navegador o erro aparecia como falha de CORS, que nao
 * era. A importacao agora grava o cadastro na hora e deixa a semeadura para ca.
 *
 * <p><b>Uma de cada vez</b>, numa thread so: duas varreduras dividiriam a mesma
 * cota de chamadas e cada uma levaria o dobro. Cada unidade e independente — a
 * que falhar entra na contagem e a fila segue.
 *
 * <p>O estado fica em memoria, como na atualizacao da base: se o pod reiniciar
 * no meio, o progresso some da tela, mas o que foi semeado esta gravado, e
 * semear de novo mescla sem duplicar ninguem.
 */
@Service
public class SemeaduraEmLote {

    private static final Logger log = LoggerFactory.getLogger(SemeaduraEmLote.class);

    private final ServidorHabilitadoService servidores;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "goianao-semeadura-em-lote");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicReference<SituacaoDaSemeadura> situacao =
            new AtomicReference<>(SituacaoDaSemeadura.nuncaExecutada());

    public SemeaduraEmLote(ServidorHabilitadoService servidores) {
        this.servidores = servidores;
    }

    public SituacaoDaSemeadura situacao() {
        return situacao.get();
    }

    /**
     * Enfileira as unidades e devolve na hora. Chamar de novo com uma semeadura
     * em curso nao e erro: a nova fila espera a anterior na mesma thread.
     *
     * @param unidades id → nome, para a tela dizer em qual esta
     */
    public SituacaoDaSemeadura semear(Long edicaoId, Integer edicaoAno, Map<Long, String> unidades) {
        if (unidades.isEmpty()) {
            return situacao.get();
        }
        // O trabalho roda fora da requisicao, onde nao ha usuario: as guardas de
        // escopo e a autoria do registro olham para quem disparou, entao a
        // identidade dele viaja junto em vez de o servico virar "sistema".
        UsuarioAutenticado quemPediu = UsuarioAtual.obrigatorio();
        Map<Long, String> fila = new LinkedHashMap<>(unidades);

        situacao.set(SituacaoDaSemeadura.iniciada(edicaoAno, fila.size()));
        executor.submit(() -> varrer(edicaoId, fila, quemPediu));
        return situacao.get();
    }

    private void varrer(Long edicaoId, Map<Long, String> unidades, UsuarioAutenticado quemPediu) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(quemPediu, null, quemPediu.authorities()));
        try {
            for (Map.Entry<Long, String> unidade : unidades.entrySet()) {
                situacao.updateAndGet(s -> s.naUnidade(unidade.getValue()));
                try {
                    SemeaduraResposta resultado = servidores.semear(edicaoId, unidade.getKey());
                    situacao.updateAndGet(s -> s.somar(resultado));
                } catch (RuntimeException e) {
                    log.warn("Semeadura em lote: falha na unidade {} ({}): {}",
                            unidade.getKey(), unidade.getValue(), e.getMessage());
                    situacao.updateAndGet(s -> s.comFalha(unidade.getValue()));
                }
            }
            situacao.updateAndGet(SituacaoDaSemeadura::concluida);
            SituacaoDaSemeadura fim = situacao.get();
            log.info("Semeadura em lote concluida: {} unidade(s), {} incluido(s), {} com falha.",
                    fim.unidadesTotal(), fim.incluidos(), fim.unidadesComFalha());
        } catch (RuntimeException e) {
            log.error("Semeadura em lote interrompida.", e);
            situacao.updateAndGet(s -> s.falhou(e.getMessage() == null
                    ? "A consulta ao RH falhou." : e.getMessage()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    /** Para os testes: esperar a fila terminar sem depender de relogio. */
    public boolean emAndamento() {
        return situacao.get().estado() == SituacaoDaSemeadura.Estado.EM_ANDAMENTO;
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now();
    }
}
