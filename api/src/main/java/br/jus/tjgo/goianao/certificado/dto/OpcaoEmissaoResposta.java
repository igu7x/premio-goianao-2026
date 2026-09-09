package br.jus.tjgo.goianao.certificado.dto;

import br.jus.tjgo.goianao.comum.Selo;
import java.time.LocalDateTime;

/**
 * Uma opcao de emissao: sempre <b>uma por unidade</b> (005/RF-2, 006/CA-4).
 *
 * <p>{@code layoutDisponivel} falso significa que a edicao nao tem arte para
 * aquele selo/tipo — o frontend desabilita a acao em vez de deixar o usuario
 * esbarrar no erro.
 */
public record OpcaoEmissaoResposta(
        Long unidadeId,
        String unidadeNome,
        Selo selo,
        boolean layoutDisponivel,
        boolean jaEmitido,
        String codigoValidacao,
        LocalDateTime emitidoEm,
        int totalEmissoes) {}
