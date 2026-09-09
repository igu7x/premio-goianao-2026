package br.jus.tjgo.goianao.servidor;

/** De onde veio o item da lista de habilitados (008/RF-2). */
public enum OrigemServidor {
    EGESP("EGESP"),
    MANUAL("Inclusão manual");

    private final String rotulo;

    OrigemServidor(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
