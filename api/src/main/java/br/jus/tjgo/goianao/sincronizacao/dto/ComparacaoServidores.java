package br.jus.tjgo.goianao.sincronizacao.dto;

import java.util.List;

/**
 * A lista de habilitados de uma edicao x unidade, cruzada com a lotacao do RH.
 *
 * @param responsavelSugerido quem o RH registra como responsavel pela unidade.
 *                            Sugestao: designar continua sendo ato do
 *                            superadministrador (DI-23)
 */
public record ComparacaoServidores(
        Long unidadeId,
        String unidadeNome,
        Long codigo,
        String responsavelSugerido,
        List<ServidorComparado> servidores) {}
