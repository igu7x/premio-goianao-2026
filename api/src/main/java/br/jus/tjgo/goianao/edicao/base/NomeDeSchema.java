package br.jus.tjgo.goianao.edicao.base;

import java.util.regex.Pattern;

/**
 * Nome do schema onde vive a base de uma edicao (feature 011).
 *
 * <p>A convencao e {@code edicao_<ano>}, mas quem le o nome deve le-lo de
 * {@code edicao.schema_dados}, e nao deriva-lo do ano: a coluna e a verdade, a
 * convencao e so como o valor nasce.
 *
 * <p>O nome entra em {@code SET search_path} e em {@code CREATE SCHEMA} por
 * concatenacao — nao ha como parametrizar identificador em SQL. Por isso
 * {@link #exigirSeguro} e obrigatorio antes de qualquer uso: e ele que garante
 * que o que vai para o comando veio do formato esperado, e nao de um valor
 * plantado na coluna.
 */
public final class NomeDeSchema {

    /** Minusculas, digitos e sublinhado; comeca por letra. 63 e o limite do PostgreSQL. */
    private static final Pattern SEGURO = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private NomeDeSchema() {}

    public static String paraAno(int ano) {
        return "edicao_" + ano;
    }

    public static String exigirSeguro(String nome) {
        if (nome == null || !SEGURO.matcher(nome).matches()) {
            throw new IllegalArgumentException(
                    "Nome de schema inválido: " + nome + ". Esperado " + SEGURO.pattern() + ".");
        }
        return nome;
    }

    public static boolean ehSeguro(String nome) {
        return nome != null && SEGURO.matcher(nome).matches();
    }
}
