package br.jus.tjgo.goianao.comum;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Canonicalizacao de nomes de unidade")
class TextoTest {

    @Test
    @DisplayName("iguala grafias que so diferem em acento, caixa e espacos")
    void igualaVariacoes() {
        // E exatamente esse o risco real: o EGESP devolver a mesma unidade com
        // grafia levemente diferente entre a listagem (004) e a lotacao (008).
        String daListagem = "1ª Vara Cível da Comarca de Goiânia";
        String daLotacao = "  1ª  VARA CIVEL DA COMARCA DE GOIANIA ";

        assertThat(Texto.canonicalizar(daListagem))
                .isEqualTo(Texto.canonicalizar(daLotacao));
    }

    @Test
    @DisplayName("nao confunde unidades diferentes")
    void distingueUnidades() {
        assertThat(Texto.canonicalizar("1ª Vara Cível da Comarca de Goiânia"))
                .isNotEqualTo(Texto.canonicalizar("2ª Vara Cível da Comarca de Goiânia"));
    }

    @Test
    @DisplayName("aparar colapsa espacos e transforma vazio em nulo")
    void apara() {
        assertThat(Texto.aparar("  Vara   Unica  ")).isEqualTo("Vara Unica");
        assertThat(Texto.aparar("   ")).isNull();
        assertThat(Texto.aparar(null)).isNull();
    }
}
