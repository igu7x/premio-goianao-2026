package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Leitor da planilha de reconhecidos (004/RF-11), com colunas
 * <b>cpf, nome, unidade, selo</b>.
 *
 * <p>Escrito a mao de proposito: o formato e simples e conhecido, e evitar uma
 * dependencia extra mantem o controle sobre separador, BOM e acentuacao — que
 * sao exatamente os pontos onde planilhas exportadas do Excel costumam falhar.
 */
@Component
public class ImportadorCsv {

    /** Uma linha ja separada em colunas, preservando o texto original para o relatorio. */
    public record LinhaCsv(int numero, String bruto, String cpf, String nome,
                           String unidade, String selo) {}

    private static final List<String> CABECALHOS = List.of("cpf", "nome", "unidade", "selo");

    public List<LinhaCsv> ler(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraDeNegocioException("Envie o arquivo CSV com os reconhecidos.");
        }

        String texto = new String(conteudo, StandardCharsets.UTF_8);
        if (!texto.isEmpty() && texto.charAt(0) == '﻿') {
            texto = texto.substring(1); // BOM gravado pelo Excel
        }

        String[] linhas = texto.split("\\r?\\n");
        if (linhas.length == 0) {
            throw new RegraDeNegocioException("O arquivo CSV está vazio.");
        }

        char separador = detectarSeparador(linhas[0]);
        int inicio = pareceCabecalho(linhas[0], separador) ? 1 : 0;

        List<LinhaCsv> resultado = new ArrayList<>();
        for (int i = inicio; i < linhas.length; i++) {
            String linha = linhas[i];
            if (linha == null || linha.isBlank()) {
                continue;
            }
            List<String> colunas = separar(linha, separador);
            resultado.add(new LinhaCsv(
                    i + 1,
                    linha.trim(),
                    coluna(colunas, 0),
                    coluna(colunas, 1),
                    coluna(colunas, 2),
                    coluna(colunas, 3)));
        }

        if (resultado.isEmpty()) {
            throw new RegraDeNegocioException(
                    "O arquivo não tem linhas de dados. Formato esperado: cpf;nome;unidade;selo");
        }
        return resultado;
    }

    private char detectarSeparador(String primeiraLinha) {
        long pontoEVirgula = primeiraLinha.chars().filter(c -> c == ';').count();
        long virgula = primeiraLinha.chars().filter(c -> c == ',').count();
        long tab = primeiraLinha.chars().filter(c -> c == '\t').count();
        if (tab > pontoEVirgula && tab > virgula) {
            return '\t';
        }
        return virgula > pontoEVirgula ? ',' : ';';
    }

    private boolean pareceCabecalho(String linha, char separador) {
        List<String> colunas = separar(linha, separador);
        if (colunas.isEmpty()) {
            return false;
        }
        String primeira = Texto.canonicalizar(colunas.get(0));
        return primeira != null && CABECALHOS.contains(primeira);
    }

    /** Separa respeitando aspas duplas, inclusive aspas escapadas por duplicacao. */
    private List<String> separar(String linha, char separador) {
        List<String> colunas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (dentroDeAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    dentroDeAspas = !dentroDeAspas;
                }
            } else if (c == separador && !dentroDeAspas) {
                colunas.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        colunas.add(atual.toString());
        return colunas;
    }

    private String coluna(List<String> colunas, int indice) {
        if (indice >= colunas.size()) {
            return null;
        }
        return Texto.aparar(colunas.get(indice));
    }
}
