package br.jus.tjgo.goianao.comum;

import java.text.Normalizer;
import java.util.Locale;

/** Normalizacao de texto usada para casar nomes vindos de integracoes distintas. */
public final class Texto {

    private Texto() {}

    /**
     * Forma canonica de um nome de unidade: sem espacos nas bordas, espacos
     * internos colapsados, minusculo e sem acentos. E ela que casa o nome da
     * unidade entre a listagem do EGESP e o cadastro local (004), sem que o
     * texto oficial (impresso no certificado) seja alterado.
     */
    public static String canonicalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** {@code null} e strings em branco viram {@code null}; o resto vem aparado. */
    public static String aparar(String valor) {
        if (valor == null) {
            return null;
        }
        String aparado = valor.trim().replaceAll("\\s+", " ");
        return aparado.isEmpty() ? null : aparado;
    }
}
