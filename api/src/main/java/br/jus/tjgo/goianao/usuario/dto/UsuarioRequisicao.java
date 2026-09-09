package br.jus.tjgo.goianao.usuario.dto;

import br.jus.tjgo.goianao.seguranca.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Cadastro de usuario pelo superadministrador.
 *
 * @param cpf            chave do dominio: sem ele o usuario entra e nao acha
 *                       nada seu, porque reconhecimento, habilitacao e
 *                       certificado sao todos indexados por CPF
 * @param unidadeLotacao unidade onde a pessoa esta lotada
 * @param areaAtuacao    area do magistrado (Civel, Criminal...); ignorada para
 *                       os demais papeis
 * @param senha          opcional — sem ela o usuario existe mas so entrara pelo
 *                       SSO, quando ele chegar
 */
public record UsuarioRequisicao(
        @NotBlank(message = "informe o CPF") String cpf,
        @NotBlank(message = "informe o nome") @Size(max = 200) String nome,
        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail inválido")
        @Size(max = 200) String email,
        @Size(max = 300) String unidadeLotacao,
        @Size(max = 150) String areaAtuacao,
        @NotEmpty(message = "informe ao menos um papel") Set<Papel> papeis,
        @Size(min = 8, max = 100, message = "a senha precisa de ao menos 8 caracteres")
        String senha) {}
