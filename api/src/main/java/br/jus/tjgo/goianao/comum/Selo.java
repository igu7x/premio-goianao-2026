package br.jus.tjgo.goianao.comum;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Nivel de reconhecimento do premio. O {@link #valor()} ordena os selos e e a
 * base da "regra do maior selo" (constituicao, principio 4): quando uma unidade
 * e reconhecida com selos diferentes, o certificado do servidor usa o maior.
 */
public enum Selo {
    BRONZE(1, "Bronze"),
    PRATA(2, "Prata"),
    OURO(3, "Ouro"),
    DIAMANTE(4, "Diamante");

    private final int valor;
    private final String rotulo;

    Selo(int valor, String rotulo) {
        this.valor = valor;
        this.rotulo = rotulo;
    }

    public int valor() {
        return valor;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Maior selo de um conjunto; vazio quando a colecao nao tem selos. */
    public static Optional<Selo> maior(Collection<Selo> selos) {
        return selos.stream().max(Comparator.comparingInt(Selo::valor));
    }
}
