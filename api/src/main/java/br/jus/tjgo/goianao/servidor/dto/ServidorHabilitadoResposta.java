package br.jus.tjgo.goianao.servidor.dto;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.servidor.OrigemServidor;
import br.jus.tjgo.goianao.servidor.ServidorHabilitado;
import java.time.LocalDateTime;

/**
 * Item da lista de habilitados.
 *
 * <p>O CPF completo so acompanha a resposta de quem pode <b>editar</b> aquela
 * lista — e ele que identifica o servidor na remocao. Para quem apenas consulta
 * (um magistrado olhando a unidade de outro, por exemplo) vai somente a versao
 * mascarada: a lista existe para dizer <i>quem pode emitir</i>, nao para
 * distribuir CPF de servidor (008/RNF-2).
 */
public record ServidorHabilitadoResposta(
        Long id,
        String cpf,
        String cpfMascarado,
        String nome,
        OrigemServidor origem,
        boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static ServidorHabilitadoResposta de(ServidorHabilitado servidor, boolean comCpf) {
        return new ServidorHabilitadoResposta(
                servidor.getId(),
                comCpf ? servidor.getCpf() : null,
                Cpf.mascarar(servidor.getCpf()),
                servidor.getNome(),
                servidor.getOrigem(),
                servidor.isAtivo(),
                servidor.getCriadoEm(),
                servidor.getAtualizadoEm());
    }
}
