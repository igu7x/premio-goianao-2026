package br.jus.tjgo.goianao.magistrado.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MagistradoRequisicao(
        @NotBlank(message = "informe o CPF") String cpf,

        @NotBlank(message = "informe o nome")
        @Size(max = 200, message = "nome deve ter no máximo 200 caracteres")
        String nome,

        @NotEmpty(message = "informe ao menos um reconhecimento (unidade e selo)")
        @Valid List<ReconhecimentoRequisicao> reconhecimentos) {}
