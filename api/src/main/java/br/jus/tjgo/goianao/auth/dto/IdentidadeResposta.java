package br.jus.tjgo.goianao.auth.dto;

import java.util.List;

/** Identidade do usuario autenticado (GET /api/auth/me), com a edicao da sessao. */
public record IdentidadeResposta(String email, String nome, List<String> papeis,
                                 EdicaoDaSessao edicao,
                                 List<EdicaoDaSessao> edicoesDisponiveis) {}
