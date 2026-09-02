package br.jus.tjgo.goianao.seguranca;

/**
 * Perfis de acesso (constituicao, principio 6). Um mesmo CPF pode acumular mais
 * de um papel; nesse caso as capacidades sao a **uniao** dos papeis, sem
 * "atuar como" (001/RF-3).
 */
public enum Papel {
    ADMINISTRADOR,
    MAGISTRADO,
    SERVIDOR;

    /** Nome da authority no Spring Security ({@code ROLE_ADMINISTRADOR}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
