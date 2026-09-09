package br.jus.tjgo.goianao.comum;

/** Tipo de reconhecido a quem o certificado se destina. */
public enum TipoCertificado {
    MAGISTRADO("Magistrado"),
    SERVIDOR("Servidor");

    private final String rotulo;

    TipoCertificado(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
