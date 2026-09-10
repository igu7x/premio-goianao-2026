package br.jus.tjgo.goianao.magistrado.dto;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.magistrado.MagistradoReconhecido;
import java.util.List;

/** O CPF e opcional: {@code cpf} e {@code cpfFormatado} vem nulos quando nao foi informado. */
public record MagistradoResposta(
        Long id,
        String email,
        String cpf,
        String cpfFormatado,
        String nome,
        List<ReconhecimentoResposta> reconhecimentos) {

    public record ReconhecimentoResposta(Long id, Long unidadeId, String unidadeNome, Selo selo) {}

    public static MagistradoResposta de(MagistradoReconhecido magistrado) {
        return new MagistradoResposta(
                magistrado.getId(),
                magistrado.getEmail(),
                magistrado.getCpf(),
                Cpf.formatar(magistrado.getCpf()),
                magistrado.getNome(),
                magistrado.getReconhecimentos().stream()
                        .map(r -> new ReconhecimentoResposta(
                                r.getId(),
                                r.getUnidade().getId(),
                                r.getUnidade().getNome(),
                                r.getSelo()))
                        .toList());
    }
}
