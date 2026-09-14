package br.jus.tjgo.goianao.integracao.egesp;

/**
 * Servidor como o RH o devolve.
 *
 * @param email     e-mail corporativo — a chave da pessoa (DI-24). Quem vier sem
 *                  ele nao entra na lista, porque nao seria reconhecido no login
 * @param cpf       opcional, so informativo
 * @param matricula matricula no RH ({@code cdgOrdem}); e por ela que o e-mail e
 *                  reencontrado na API quando a listagem de lotados nao o traz
 */
public record ServidorEgesp(String email, String nome, String cpf, Long matricula) {

    public ServidorEgesp(String email, String nome, String cpf) {
        this(email, nome, cpf, null);
    }
}
