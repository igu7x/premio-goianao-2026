package br.jus.tjgo.goianao.publico;

import br.jus.tjgo.goianao.config.GoianaoProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Balde simples por IP para a verificacao publica (007/RNF-3).
 *
 * <p>O codigo ja e opaco, mas sem limite de taxa um script ainda poderia tentar
 * forca bruta a vontade. Em memoria e suficiente para uma instancia; havendo
 * varias, o limite deve migrar para o proxy reverso ou um Redis compartilhado.
 */
@Component
public class LimitadorDeTaxa {

    private record Balde(AtomicInteger consultas, Instant inicioJanela) {}

    private static final Duration JANELA = Duration.ofMinutes(1);
    private static final int LIMITE_DE_ORIGENS = 10_000;

    private final Map<String, Balde> baldes = new ConcurrentHashMap<>();
    private final int limitePorJanela;

    public LimitadorDeTaxa(GoianaoProperties props) {
        this.limitePorJanela = props.verificacaoPublica().consultasPorMinuto();
    }

    public boolean permitir(String origem) {
        Instant agora = Instant.now();

        // Guarda contra crescimento indefinido do mapa sob varredura distribuida.
        if (baldes.size() > LIMITE_DE_ORIGENS) {
            baldes.entrySet().removeIf(e -> janelaExpirada(e.getValue(), agora));
        }

        Balde balde = baldes.compute(origem, (chave, atual) -> {
            if (atual == null || janelaExpirada(atual, agora)) {
                return new Balde(new AtomicInteger(0), agora);
            }
            return atual;
        });

        return balde.consultas().incrementAndGet() <= limitePorJanela;
    }

    private boolean janelaExpirada(Balde balde, Instant agora) {
        return Duration.between(balde.inicioJanela(), agora).compareTo(JANELA) > 0;
    }
}
