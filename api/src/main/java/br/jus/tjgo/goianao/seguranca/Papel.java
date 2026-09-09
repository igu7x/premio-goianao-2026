package br.jus.tjgo.goianao.seguranca;

/**
 * Perfis de acesso (constituicao, principio 6). Um mesmo CPF pode acumular mais
 * de um papel; nesse caso as capacidades sao a <b>uniao</b> dos papeis, sem
 * "atuar como" (001/RF-3).
 *
 * <p>{@link #SUPERADMIN} e o unico que administra o cadastro de usuarios, e
 * <b>contem</b> {@link #ADMINISTRADOR}: quem e superadministrador recebe
 * tambem a authority de administrador, para que as guardas ja escritas
 * continuem valendo sem espalhar excecao pelo codigo. Ver
 * {@link UsuarioAutenticado#authorities()}.
 */
public enum Papel {
    SUPERADMIN,
    ADMINISTRADOR,
    MAGISTRADO,
    SERVIDOR;

    /** Nome da authority no Spring Security ({@code ROLE_ADMINISTRADOR}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
