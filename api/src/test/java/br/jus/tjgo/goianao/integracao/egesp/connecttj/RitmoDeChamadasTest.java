package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O freio das chamadas a API corporativa.
 *
 * <p>Os tempos sao propositalmente folgados: o teste prova que <b>existe</b>
 * espacamento, nao mede precisao de relogio — cravar milissegundos em maquina
 * compartilhada produz teste que falha sozinho.
 */
@DisplayName("Ritmo das chamadas a API corporativa")
class RitmoDeChamadasTest {

    @Test
    @DisplayName("espaca as chamadas conforme o teto combinado")
    void espacaAsChamadas() {
        // 20 por segundo => 50ms entre elas; cinco chamadas gastam ~200ms.
        RitmoDeChamadas ritmo = new RitmoDeChamadas(20);

        long inicio = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            ritmo.aguardarVez();
        }
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(gasto).isGreaterThanOrEqualTo(Duration.ofMillis(150));
    }

    @Test
    @DisplayName("a primeira chamada nao espera: o freio e entre chamadas")
    void primeiraNaoEspera() {
        RitmoDeChamadas ritmo = new RitmoDeChamadas(1);

        long inicio = System.nanoTime();
        ritmo.aguardarVez();
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(gasto).isLessThan(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("teto invalido nao trava o sistema: vira sem espera")
    void tetoInvalido() {
        RitmoDeChamadas ritmo = new RitmoDeChamadas(0);

        long inicio = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            ritmo.aguardarVez();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - inicio))
                .isLessThan(Duration.ofMillis(200));
    }
}
