package br.jus.tjgo.goianao.unidade;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.comum.LeitorCsv;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.Texto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O CSV de exemplo entregue ao administrador precisa estar no formato que o
 * importador aceita.
 *
 * <p>Nao e zelo excessivo: um exemplo que falha e pior do que nenhum, porque a
 * pessoa conclui que o sistema esta quebrado. E os tropecos aqui sao sutis — o
 * {@code ª} de "1ª UNIDADE" nao e acento, entao a canonicalizacao <b>nao</b> o
 * transforma em "a": trocar um pelo outro faz a linha nao casar com unidade
 * nenhuma.
 */
@DisplayName("CSV de exemplo dos magistrados responsaveis")
class ExemploDeCsvTest {

    private static final Path ARQUIVO =
            Path.of("..", "specs", "exemplos", "magistrados-responsaveis.csv");

    @Test
    @DisplayName("tem as quatro colunas, e-mails validos e selos que existem")
    void exemploEhValido() throws Exception {
        assertThat(ARQUIVO).exists();

        List<LeitorCsv.LinhaBruta> linhas = new LeitorCsv().ler(
                Files.readAllBytes(ARQUIVO), List.of("nome", "magistrado"), "vazio");

        assertThat(linhas).as("o cabecalho nao entra como dado").isNotEmpty();

        for (LeitorCsv.LinhaBruta linha : linhas) {
            assertThat(linha.coluna(0)).as("nome na linha %d", linha.numero()).isNotBlank();
            assertThat(linha.coluna(1)).as("e-mail na linha %d", linha.numero())
                    .isNotNull()
                    .contains("@")
                    // Dominio reservado: ninguem confunde o exemplo com a lista real.
                    .endsWith("@tjgo.example");
            assertThat(linha.coluna(2)).as("unidade na linha %d", linha.numero()).isNotBlank();
            assertThat(seloExiste(linha.coluna(3)))
                    .as("selo \"%s\" na linha %d", linha.coluna(3), linha.numero())
                    .isTrue();
        }
    }

    private boolean seloExiste(String bruto) {
        String valor = Texto.canonicalizar(bruto);
        for (Selo selo : Selo.values()) {
            if (selo.name().toLowerCase(Locale.ROOT).equals(valor)
                    || Texto.canonicalizar(selo.rotulo()).equals(valor)) {
                return true;
            }
        }
        return false;
    }
}
