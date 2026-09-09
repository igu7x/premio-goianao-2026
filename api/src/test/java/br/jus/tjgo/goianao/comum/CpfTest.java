package br.jus.tjgo.goianao.comum;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CPF")
class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"10120230100", "204.506.702-52", "309 801 403 23"})
    @DisplayName("aceita CPFs validos, com ou sem formatacao")
    void aceitaValidos(String cpf) {
        assertThat(Cpf.valido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10120230101", "1234567890", "123456789012", "abcdefghijk"})
    @DisplayName("recusa digito verificador errado e tamanho invalido")
    void recusaInvalidos(String cpf) {
        assertThat(Cpf.valido(cpf)).isFalse();
    }

    @Test
    @DisplayName("recusa sequencias de digito repetido, ainda que fechem a conta")
    void recusaRepetidos() {
        assertThat(Cpf.valido("11111111111")).isFalse();
        assertThat(Cpf.valido("00000000000")).isFalse();
    }

    @Test
    @DisplayName("nulo e vazio nao sao validos")
    void recusaNulo() {
        assertThat(Cpf.valido(null)).isFalse();
        assertThat(Cpf.valido("")).isFalse();
    }

    @Test
    @DisplayName("normaliza removendo qualquer formatacao")
    void normaliza() {
        assertThat(Cpf.normalizar("101.202.301-00")).isEqualTo("10120230100");
    }

    @Test
    @DisplayName("formata e mascara para exibicao")
    void formataEMascara() {
        assertThat(Cpf.formatar("10120230100")).isEqualTo("101.202.301-00");
        // A mascara e o que pode aparecer em tela e auditoria (001/RNF-3).
        assertThat(Cpf.mascarar("10120230100")).isEqualTo("***.202.301-**");
        assertThat(Cpf.mascarar("123")).isEqualTo("***");
    }
}
