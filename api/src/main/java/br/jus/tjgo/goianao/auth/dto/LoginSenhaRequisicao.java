package br.jus.tjgo.goianao.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciais do login por e-mail e senha. */
public record LoginSenhaRequisicao(
        @NotBlank(message = "informe o e-mail") String email,
        @NotBlank(message = "informe a senha") String senha) {}
