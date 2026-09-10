package br.jus.tjgo.goianao.comum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("E-mail (chave da pessoa, DI-24)")
class EmailTest {

    @Test
    @DisplayName("normaliza para minusculas e sem espacos nas pontas; vazio vira nulo")
    void normaliza() {
        assertThat(Email.normalizar("  Fulano.Tal@TJGO.Jus.BR ")).isEqualTo("fulano.tal@tjgo.jus.br");
        assertThat(Email.normalizar(null)).isNull();
        assertThat(Email.normalizar("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fulano@tjgo.jus.br", " Fulano@TJGO.jus.br ", "a.b-c+d@tjgo.example"})
    @DisplayName("aceita e-mails validos, inclusive antes de normalizar")
    void aceitaValidos(String email) {
        assertThat(Email.valido(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fulano", "fulano@tjgo", "@tjgo.jus.br", "a b@tjgo.jus.br",
        "a@b@tjgo.jus.br"})
    @DisplayName("recusa o que nao tem formato de e-mail")
    void recusaInvalidos(String email) {
        assertThat(Email.valido(email)).isFalse();
    }

    @Test
    @DisplayName("recusa nulo e endereco maior que a coluna")
    void recusaNuloELongo() {
        assertThat(Email.valido(null)).isFalse();
        assertThat(Email.valido("a".repeat(190) + "@tjgo.jus.br")).isFalse();
    }

    @Test
    @DisplayName("exigir devolve normalizado e explica o que falta")
    void exigir() {
        assertThat(Email.exigir(" Fulano@TJGO.jus.br")).isEqualTo("fulano@tjgo.jus.br");

        assertThatThrownBy(() -> Email.exigir(null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Informe o e-mail.");
        assertThatThrownBy(() -> Email.exigir("  "))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Informe o e-mail.");
        assertThatThrownBy(() -> Email.exigir("fulano"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("E-mail inválido.");
    }

    @Test
    @DisplayName("mascara mantendo a inicial e o dominio")
    void mascara() {
        assertThat(Email.mascarar("teixeira@tjgo.jus.br")).isEqualTo("t***@tjgo.jus.br");
        assertThat(Email.mascarar("  Teixeira@TJGO.jus.br")).isEqualTo("t***@tjgo.jus.br");
    }

    @Test
    @DisplayName("sem endereco aproveitavel, a mascara nao revela nada")
    void mascaraDeInutilizavel() {
        assertThat(Email.mascarar(null)).isEqualTo("***");
        assertThat(Email.mascarar("")).isEqualTo("***");
        assertThat(Email.mascarar("semarroba")).isEqualTo("***");
        assertThat(Email.mascarar("@tjgo.jus.br")).isEqualTo("***");
    }
}
