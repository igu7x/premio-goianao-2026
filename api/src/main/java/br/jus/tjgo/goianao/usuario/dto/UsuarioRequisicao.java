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
 * @param email          e-mail corporativo — a chave do dominio (DI-24): e o
 *                       que o SSO entrega, e por ele o sistema acha os
 *                       reconhecimentos, habilitacoes e certificados da pessoa
 * @param cpf            opcional, so informativo
 * @param unidadeLotacao unidade onde a pessoa esta lotada
 * @param areaAtuacao    area do magistrado (Civel, Criminal...); ignorada para
 *                       os demais papeis
 * @param senha          opcional — sem ela o usuario existe mas so entra pelo
 *                       SSO
 */
public record UsuarioRequisicao(
        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail inválido")
        @Size(max = 200) String email,
        @NotBlank(message = "informe o nome") @Size(max = 200) String nome,
        String cpf,
        @Size(max = 300) String unidadeLotacao,
        @Size(max = 150) String areaAtuacao,
        @NotEmpty(message = "informe ao menos um papel") Set<Papel> papeis,
        @Size(min = 8, max = 100, message = "a senha precisa de ao menos 8 caracteres")
        String senha) {}
