package br.jus.tjgo.goianao.usuario.dto;

import br.jus.tjgo.goianao.seguranca.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Alteracao de usuario.
 *
 * <p>Sem CPF de proposito: ele e a chave que liga o usuario a tudo que ele ja
 * fez no sistema — reconhecimentos, habilitacoes, certificados emitidos. Mudar
 * o CPF seria trocar a pessoa por outra mantendo o registro, entao o campo
 * simplesmente nao existe aqui.
 *
 * @param senha nova senha; em branco mantem a atual
 */
public record AtualizarUsuarioRequisicao(
        @NotBlank(message = "informe o nome") @Size(max = 200) String nome,
        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail inválido")
        @Size(max = 200) String email,
        @Size(max = 300) String unidadeLotacao,
        @Size(max = 150) String areaAtuacao,
        @NotEmpty(message = "informe ao menos um papel") Set<Papel> papeis,
        @Size(min = 8, max = 100, message = "a senha precisa de ao menos 8 caracteres")
        String senha) {}
