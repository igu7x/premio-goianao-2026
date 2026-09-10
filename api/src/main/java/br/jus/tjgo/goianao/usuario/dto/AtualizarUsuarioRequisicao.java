package br.jus.tjgo.goianao.usuario.dto;

import br.jus.tjgo.goianao.seguranca.Papel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Alteracao de usuario.
 *
 * <p>Sem e-mail de proposito: ele e a chave que liga o usuario a tudo que ele
 * ja fez no sistema — reconhecimentos, habilitacoes, certificados emitidos
 * (DI-24). Mudar o e-mail seria trocar a pessoa por outra mantendo o registro,
 * entao o campo simplesmente nao existe aqui.
 *
 * @param cpf   opcional; em branco mantem o atual
 * @param senha nova senha; em branco mantem a atual
 */
public record AtualizarUsuarioRequisicao(
        @NotBlank(message = "informe o nome") @Size(max = 200) String nome,
        String cpf,
        @Size(max = 300) String unidadeLotacao,
        @Size(max = 150) String areaAtuacao,
        @NotEmpty(message = "informe ao menos um papel") Set<Papel> papeis,
        @Size(min = 8, max = 100, message = "a senha precisa de ao menos 8 caracteres")
        String senha) {}
