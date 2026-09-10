package br.jus.tjgo.goianao.usuario.dto;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.usuario.Usuario;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Usuario como sai na API.
 *
 * <p>O CPF, quando existe, vai <b>mascarado</b> (nulo quando nao foi
 * informado): quem administra usuarios precisa reconhecer a pessoa na lista,
 * nao coletar o documento dela. E o hash da senha nao aparece em campo algum —
 * nem mascarado.
 */
public record UsuarioResposta(
        Long id,
        String cpfMascarado,
        String nome,
        String email,
        String unidadeLotacao,
        String areaAtuacao,
        List<String> papeis,
        boolean ativo,
        boolean temSenha,
        LocalDateTime criadoEm) {

    public static UsuarioResposta de(Usuario usuario) {
        return new UsuarioResposta(
                usuario.getId(),
                Cpf.mascarar(usuario.getCpf()),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getUnidadeLotacao(),
                usuario.getAreaAtuacao(),
                usuario.getPapeis().stream().map(Enum::name).sorted().toList(),
                usuario.isAtivo(),
                usuario.getSenhaHash() != null,
                usuario.getCriadoEm());
    }
}
