package br.jus.tjgo.goianao.servidor.dto;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.servidor.OrigemServidor;
import br.jus.tjgo.goianao.servidor.ServidorHabilitado;
import java.time.LocalDateTime;

/**
 * Item da lista de habilitados.
 *
 * <p>O e-mail completo so acompanha a resposta de quem pode <b>editar</b>
 * aquela lista. Para quem apenas consulta (um magistrado olhando a unidade de
 * outro, por exemplo) vai somente a versao mascarada: a lista existe para dizer
 * <i>quem pode emitir</i>, nao para distribuir contato de servidor (008/RNF-2,
 * DI-10). O CPF, opcional, sai sempre mascarado.
 *
 * <p>A remocao e pelo {@code id}: dado pessoal nao vai na URL.
 */
public record ServidorHabilitadoResposta(
        Long id,
        String email,
        String emailMascarado,
        String cpfMascarado,
        String nome,
        OrigemServidor origem,
        boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static ServidorHabilitadoResposta de(ServidorHabilitado servidor, boolean completo) {
        return new ServidorHabilitadoResposta(
                servidor.getId(),
                completo ? servidor.getEmail() : null,
                Email.mascarar(servidor.getEmail()),
                Cpf.mascarar(servidor.getCpf()),
                servidor.getNome(),
                servidor.getOrigem(),
                servidor.isAtivo(),
                servidor.getCriadoEm(),
                servidor.getAtualizadoEm());
    }
}
