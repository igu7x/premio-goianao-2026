package br.jus.tjgo.goianao.publico;

import br.jus.tjgo.goianao.certificado.CertificadoEmitido;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import java.time.LocalDateTime;

/**
 * Resultado da conferencia publica. Traz o minimo necessario para atestar a
 * autenticidade e <b>nao inclui CPF nem e-mail</b> — minimizacao de dado
 * pessoal, ja que o endpoint e aberto (007/RNF-2, CA-5).
 */
public record VerificacaoResposta(
        boolean valido,
        String codigo,
        String nome,
        String unidade,
        Integer edicaoAno,
        Selo selo,
        TipoCertificado tipo,
        LocalDateTime emitidoEm) {

    public static VerificacaoResposta valido(CertificadoEmitido certificado) {
        return new VerificacaoResposta(
                true,
                certificado.getCodigoValidacao(),
                certificado.getNomeEmissor(),
                certificado.getUnidade().getNome(),
                certificado.getEdicao().getAno(),
                certificado.getSelo(),
                certificado.getTipo(),
                certificado.getEmitidoEm());
    }

    /** Resposta uniforme para codigo inexistente: nao revela nada (007/CA-2). */
    public static VerificacaoResposta naoEncontrado(String codigo) {
        return new VerificacaoResposta(false, codigo, null, null, null, null, null, null);
    }
}
