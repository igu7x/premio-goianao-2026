package br.jus.tjgo.goianao.auth.sso;

/**
 * Evento publicado quando alguem entra pelo SSO. Existe para que a atualizacao
 * cadastral pelo RH aconteca <b>depois</b> e <b>fora</b> do caminho do login: o
 * navegador ja foi redirecionado com a sessao pronta quando ela roda.
 */
public record LoginPeloSso(String email) {}
