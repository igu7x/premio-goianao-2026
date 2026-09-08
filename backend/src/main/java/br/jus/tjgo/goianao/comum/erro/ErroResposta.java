package br.jus.tjgo.goianao.comum.erro;

import java.time.OffsetDateTime;
import java.util.List;

/** Corpo unico de erro da API. */
public record ErroResposta(
        int status,
        String erro,
        String mensagem,
        List<String> detalhes,
        OffsetDateTime momento) {

    public static ErroResposta de(int status, String erro, String mensagem, List<String> detalhes) {
        return new ErroResposta(status, erro, mensagem,
                detalhes == null ? List.of() : detalhes, OffsetDateTime.now());
    }
}
