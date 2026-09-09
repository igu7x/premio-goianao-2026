package br.jus.tjgo.goianao.servidor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncluirServidorRequisicao(
        @NotBlank(message = "informe o CPF") String cpf,

        @NotBlank(message = "informe o nome")
        @Size(max = 200, message = "nome deve ter no máximo 200 caracteres")
        String nome) {}
