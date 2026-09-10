package br.jus.tjgo.goianao.auth;

/**
 * Porta consultada pelo {@link PapeisResolver} para decidir o papel MAGISTRADO
 * (001/RF-5). Implementada pelo modulo de magistrados (feature 004) — o pacote
 * de autenticacao nao depende dele diretamente.
 */
public interface MagistradoLookup {

    /** Verdadeiro se o e-mail consta como magistrado reconhecido em alguma edicao. */
    boolean ehMagistradoReconhecido(String email);
}
