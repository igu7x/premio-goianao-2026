package br.jus.tjgo.goianao.comum;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Leitura crua de CSV: separa linhas e colunas, e para por ai.
 *
 * <p>Escrito a mao de proposito: o formato e simples e conhecido, e evitar uma
 * dependencia extra mantem o controle sobre separador, BOM e acentuacao — que
 * sao exatamente os pontos onde planilhas exportadas do Excel costumam falhar.
 *
 * <p>Quem sabe o que cada coluna significa e o importador de cada assunto. Aqui
 * nao ha nome de campo nenhum: as duas planilhas do sistema — reconhecidos e
 * responsaveis — tem colunas diferentes, e so o trabalho bruto e igual.
 */
@Component
public class LeitorCsv {

    /** Uma linha ja separada, com o texto original preservado para o relatorio. */
    public record LinhaBruta(int numero, String bruto, List<String> colunas) {

        /** A coluna, aparada; nulo quando a linha nem chegou ate ela. */
        public String coluna(int indice) {
            if (indice >= colunas.size()) {
                return null;
            }
            return Texto.aparar(colunas.get(indice));
        }
    }

    /**
     * @param cabecalhosConhecidos primeiras colunas que indicam uma linha de
     *                             titulo, ja canonicalizadas; sem isso o
     *                             cabecalho entraria como dado e apareceria no
     *                             relatorio como erro
     */
    public List<LinhaBruta> ler(byte[] conteudo, List<String> cabecalhosConhecidos,
                                String mensagemSeVazio) {
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraDeNegocioException(mensagemSeVazio);
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
        int inicio = pareceCabecalho(linhas[0], separador, cabecalhosConhecidos) ? 1 : 0;

        List<LinhaBruta> resultado = new ArrayList<>();
        for (int i = inicio; i < linhas.length; i++) {
            String linha = linhas[i];
            if (linha == null || linha.isBlank()) {
                continue;
            }
            resultado.add(new LinhaBruta(i + 1, linha.trim(), separar(linha, separador)));
        }
        return resultado;
    }

    /** As colunas da primeira linha, para quem precisa conferir o formato. */
    public List<String> primeiraLinha(byte[] conteudo) {
        List<LinhaBruta> lidas = ler(conteudo, List.of(), "O arquivo CSV está vazio.");
        return lidas.isEmpty() ? List.of() : lidas.get(0).colunas();
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

    private boolean pareceCabecalho(String linha, char separador, List<String> conhecidos) {
        List<String> colunas = separar(linha, separador);
        if (colunas.isEmpty()) {
            return false;
        }
        String primeira = Texto.canonicalizar(colunas.get(0));
        return primeira != null && conhecidos.contains(primeira);
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
}
