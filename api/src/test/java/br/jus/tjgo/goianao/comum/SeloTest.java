package br.jus.tjgo.goianao.comum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Regra do maior selo (006/CA-1)")
class SeloTest {

    @Test
    @DisplayName("entre Bronze e Ouro, vale Ouro")
    void bronzeEOuro() {
        assertThat(Selo.maior(List.of(Selo.BRONZE, Selo.OURO))).contains(Selo.OURO);
    }

    @Test
    @DisplayName("Diamante supera todos os demais")
    void diamanteVence() {
        assertThat(Selo.maior(Set.of(Selo.BRONZE, Selo.PRATA, Selo.OURO, Selo.DIAMANTE)))
                .contains(Selo.DIAMANTE);
    }

    @Test
    @DisplayName("unidade sem reconhecimento nao tem selo")
    void semSelos() {
        assertThat(Selo.maior(List.of())).isEmpty();
    }

    @Test
    @DisplayName("a ordem dos selos e Bronze < Prata < Ouro < Diamante")
    void ordem() {
        assertThat(Selo.BRONZE.valor()).isLessThan(Selo.PRATA.valor());
        assertThat(Selo.PRATA.valor()).isLessThan(Selo.OURO.valor());
        assertThat(Selo.OURO.valor()).isLessThan(Selo.DIAMANTE.valor());
    }
}
