package br.jus.tjgo.goianao.servidor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email e-mail corporativo — e por ele que o servidor e reconhecido no
 *              login (DI-24)
 * @param cpf   opcional, so informativo
 */
public record IncluirServidorRequisicao(
        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail inválido")
        @Size(max = 200, message = "e-mail deve ter no máximo 200 caracteres")
        String email,

        @NotBlank(message = "informe o nome")
        @Size(max = 200, message = "nome deve ter no máximo 200 caracteres")
        String nome,

        String cpf) {}
