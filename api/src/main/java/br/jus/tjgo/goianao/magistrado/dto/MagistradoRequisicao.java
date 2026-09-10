package br.jus.tjgo.goianao.magistrado.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param email e-mail corporativo do magistrado — e por ele que o login o
 *              reconhece (DI-24)
 * @param cpf   opcional, so informativo
 */
public record MagistradoRequisicao(
        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail inválido")
        @Size(max = 200, message = "e-mail deve ter no máximo 200 caracteres")
        String email,

        @NotBlank(message = "informe o nome")
        @Size(max = 200, message = "nome deve ter no máximo 200 caracteres")
        String nome,

        String cpf,

        @NotEmpty(message = "informe ao menos um reconhecimento (unidade e selo)")
        @Valid List<ReconhecimentoRequisicao> reconhecimentos) {}
