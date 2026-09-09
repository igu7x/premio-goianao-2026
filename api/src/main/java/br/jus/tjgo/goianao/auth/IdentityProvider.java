package br.jus.tjgo.goianao.auth;

import java.util.List;

/**
 * Porta de autenticacao. A implementacao atual e um mock; a futura
 * ({@code SsoIdentityProvider}) fara OIDC/OAuth2 com fluxo Authorization Code,
 * lendo CPF e nome das claims do ID token — sem alterar este contrato
 * (001/RF-2, 001/RNF-1).
 */
public interface IdentityProvider {

    /** Autentica e devolve a identidade correspondente ao codigo/credencial. */
    IdentidadeAutenticada autenticar(String credencial);

    /**
     * Identidades disponiveis para escolha na tela de login. Existe apenas
     * enquanto o provedor e mockado; o SSO real devolvera lista vazia.
     */
    List<IdentidadeAutenticada> identidadesDisponiveis();
}
