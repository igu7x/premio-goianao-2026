package br.jus.tjgo.goianao.auth.dto;

import java.util.List;

/**
 * Usuario de teste oferecido na tela de login enquanto o SSO e mockado. Traz os
 * papeis apenas para orientar quem esta testando; a autorizacao real e sempre
 * recalculada no backend a cada login.
 */
public record UsuarioMockResposta(String email, String nome, List<String> papeis) {}
