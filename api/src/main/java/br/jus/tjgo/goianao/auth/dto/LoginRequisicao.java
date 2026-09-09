package br.jus.tjgo.goianao.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credencial do login mockado. Com o SSO real, este corpo sera substituido pelo
 * codigo de autorizacao do fluxo OIDC — o resto do contrato permanece.
 */
public record LoginRequisicao(@NotBlank(message = "informe o usuário") String credencial) {}
