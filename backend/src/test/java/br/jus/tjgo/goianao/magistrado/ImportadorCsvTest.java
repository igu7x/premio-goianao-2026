package br.jus.tjgo.goianao.magistrado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Leitura da planilha de reconhecidos (004/RF-11)")
class ImportadorCsvTest {

    private final ImportadorCsv importador = new ImportadorCsv();

    @Test
    @DisplayName("le cabecalho, ponto e virgula e acentuacao")
    void formatoPadrao() {
        String csv = """
                cpf;nome;unidade;selo
                10120230100;Ana Rebelo;1ª Vara Cível da Comarca de Goiânia;Ouro
                20450670252;Rafael Bittencourt;2ª Vara Cível da Comarca de Goiânia;Bronze
                """;

        List<ImportadorCsv.LinhaCsv> linhas = importador.ler(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).cpf()).isEqualTo("10120230100");
        assertThat(linhas.get(0).unidade()).isEqualTo("1ª Vara Cível da Comarca de Goiânia");
        assertThat(linhas.get(1).selo()).isEqualTo("Bronze");
    }

    @Test
    @DisplayName("aceita virgula como separador e aspas em torno do nome da unidade")
    void separadorVirgulaComAspas() {
        String csv = "cpf,nome,unidade,selo\n"
                + "10120230100,\"Rebelo, Ana\",\"1ª Vara Cível, Goiânia\",Ouro\n";

        List<ImportadorCsv.LinhaCsv> linhas = importador.ler(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).nome()).isEqualTo("Rebelo, Ana");
        assertThat(linhas.get(0).unidade()).isEqualTo("1ª Vara Cível, Goiânia");
    }

    @Test
    @DisplayName("arquivo sem cabecalho tambem e aceito")
    void semCabecalho() {
        String csv = "10120230100;Ana Rebelo;Vara Unica;Prata\n";

        assertThat(importador.ler(csv.getBytes(StandardCharsets.UTF_8))).hasSize(1);
    }

    @Test
    @DisplayName("descarta o BOM que o Excel costuma gravar")
    void ignoraBom() {
        String csv = "﻿cpf;nome;unidade;selo\n10120230100;Ana;Vara Unica;Ouro\n";

        List<ImportadorCsv.LinhaCsv> linhas = importador.ler(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).cpf()).isEqualTo("10120230100");
    }

    @Test
    @DisplayName("arquivo vazio ou so com cabecalho e recusado com mensagem util")
    void arquivoSemDados() {
        assertThatThrownBy(() -> importador.ler(new byte[0]))
                .isInstanceOf(RegraDeNegocioException.class);

        assertThatThrownBy(() -> importador.ler(
                "cpf;nome;unidade;selo\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("cpf;nome;unidade;selo");
    }
}
