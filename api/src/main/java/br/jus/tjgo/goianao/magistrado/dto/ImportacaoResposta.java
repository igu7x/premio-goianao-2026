package br.jus.tjgo.goianao.magistrado.dto;

import java.util.List;

/**
 * Relatorio da importacao em lote (004/RF-11). A transacao e <b>por
 * magistrado</b>: um CPF com qualquer linha invalida e rejeitado inteiro e
 * reportado, enquanto os demais sao persistidos — assim o lote nao se perde por
 * um erro isolado nem fica pela metade (004/RNF-3).
 */
public record ImportacaoResposta(
        int linhasLidas,
        int magistradosCriados,
        int reconhecimentosCriados,
        List<String> criados,
        List<ErroDeLinha> erros) {

    public record ErroDeLinha(int linha, String conteudo, String motivo) {}
}
