package br.jus.tjgo.goianao.auth.dto;

import java.util.List;

/** Identidade do usuario autenticado (GET /api/auth/me). */
public record IdentidadeResposta(String email, String nome, List<String> papeis) {}
