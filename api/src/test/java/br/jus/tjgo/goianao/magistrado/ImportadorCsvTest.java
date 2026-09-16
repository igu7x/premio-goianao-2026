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

    private final ImportadorCsv importador = new ImportadorCsv(new br.jus.tjgo.goianao.comum.LeitorCsv());

    private List<ImportadorCsv.LinhaCsv> ler(String csv) {
        return importador.ler(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("le cabecalho, ponto e virgula e acentuacao, com o e-mail na primeira coluna")
    void formatoPadrao() {
        List<ImportadorCsv.LinhaCsv> linhas = ler("""
                email;nome;unidade;selo
                ana.rebelo@tjgo.example;Ana Rebelo;1ª Vara Cível da Comarca de Goiânia;Ouro
                rafael.bittencourt@tjgo.example;Rafael Bittencourt;2ª Vara Cível da Comarca de Goiânia;Bronze
                """);

        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).email()).isEqualTo("ana.rebelo@tjgo.example");
        assertThat(linhas.get(0).unidade()).isEqualTo("1ª Vara Cível da Comarca de Goiânia");
        assertThat(linhas.get(1).selo()).isEqualTo("Bronze");
        // Sem a quinta coluna, nao ha CPF — ele e opcional (DI-24).
        assertThat(linhas.get(0).cpf()).isNull();
    }

    @Test
    @DisplayName("le o CPF opcional na quinta coluna, e coluna vazia vira nulo")
    void cpfOpcionalNaQuintaColuna() {
        List<ImportadorCsv.LinhaCsv> linhas = ler("""
                email;nome;unidade;selo;cpf
                ana.rebelo@tjgo.example;Ana Rebelo;Vara Unica;Ouro;101.202.301-00
                rafael.bittencourt@tjgo.example;Rafael;Vara Unica;Prata;
                helena.aires@tjgo.example;Helena;Vara Unica;Bronze
                """);

        assertThat(linhas).hasSize(3);
        assertThat(linhas.get(0).cpf()).isEqualTo("101.202.301-00");
        assertThat(linhas.get(1).cpf()).isNull();
        assertThat(linhas.get(2).cpf()).isNull();
    }

    @Test
    @DisplayName("aceita virgula como separador e aspas em torno do nome da unidade")
    void separadorVirgulaComAspas() {
        List<ImportadorCsv.LinhaCsv> linhas = ler("email,nome,unidade,selo\n"
                + "ana.rebelo@tjgo.example,\"Rebelo, Ana\",\"1ª Vara Cível, Goiânia\",Ouro\n");

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).nome()).isEqualTo("Rebelo, Ana");
        assertThat(linhas.get(0).unidade()).isEqualTo("1ª Vara Cível, Goiânia");
    }

    @Test
    @DisplayName("o cabecalho tambem pode vir como E-mail")
    void cabecalhoComHifen() {
        List<ImportadorCsv.LinhaCsv> linhas = ler("E-mail;Nome;Unidade;Selo\n"
                + "ana.rebelo@tjgo.example;Ana;Vara Unica;Ouro\n");

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).email()).isEqualTo("ana.rebelo@tjgo.example");
    }

    @Test
    @DisplayName("arquivo sem cabecalho tambem e aceito")
    void semCabecalho() {
        assertThat(ler("ana.rebelo@tjgo.example;Ana Rebelo;Vara Unica;Prata\n")).hasSize(1);
    }

    @Test
    @DisplayName("descarta o BOM que o Excel costuma gravar")
    void ignoraBom() {
        List<ImportadorCsv.LinhaCsv> linhas =
                ler("﻿email;nome;unidade;selo\nana.rebelo@tjgo.example;Ana;Vara Unica;Ouro\n");

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).email()).isEqualTo("ana.rebelo@tjgo.example");
    }

    @Test
    @DisplayName("arquivo vazio ou so com cabecalho e recusado com mensagem util")
    void arquivoSemDados() {
        assertThatThrownBy(() -> importador.ler(new byte[0]))
                .isInstanceOf(RegraDeNegocioException.class);

        assertThatThrownBy(() -> ler("email;nome;unidade;selo\n"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("email;nome;unidade;selo;cpf");
    }

    @Test
    @DisplayName("planilha no formato antigo, com CPF na primeira coluna, e recusada inteira")
    void formatoAntigoRecusado() {
        assertThatThrownBy(() -> ler("cpf;nome;unidade;selo\n10120230100;Ana;Vara Unica;Ouro\n"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("formato antigo")
                .hasMessageContaining("email;nome;unidade;selo");

        // Maiusculas e outro separador nao escapam da deteccao.
        assertThatThrownBy(() -> ler("CPF,nome,unidade,selo\n10120230100,Ana,Vara Unica,Ouro\n"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("formato antigo");
    }
}
