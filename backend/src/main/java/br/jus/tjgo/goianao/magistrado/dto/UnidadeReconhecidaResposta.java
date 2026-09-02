package br.jus.tjgo.goianao.magistrado.dto;

import br.jus.tjgo.goianao.comum.Selo;
import java.util.List;

/**
 * Unidade reconhecida em uma edicao, com todos os selos que recebeu e o maior
 * deles (004/RF-9). E o insumo da regra do maior selo na emissao do servidor
 * (006/RF-4) e do gerenciamento da lista de habilitados (008).
 */
public record UnidadeReconhecidaResposta(
        Long unidadeId,
        String nome,
        List<Selo> selos,
        Selo maiorSelo,
        int magistrados,
        long servidoresHabilitados) {}
