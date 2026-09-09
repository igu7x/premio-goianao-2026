package br.jus.tjgo.goianao.auth.dto;

import java.util.List;

/** Resposta do login: token de sessao e a identidade que ele carrega. */
public record SessaoResposta(
        String token,
        long expiraEmSegundos,
        String cpf,
        String nome,
        List<String> papeis) {}
