package br.jus.tjgo.goianao.servidor.dto;

import java.util.List;

/**
 * Lista de uma unidade em uma edicao, junto do que o usuario corrente pode
 * fazer com ela — o frontend so reflete a decisao ja tomada no backend
 * (008/RNF-1).
 */
public record ListaHabilitadosResposta(
        Long edicaoId,
        Integer edicaoAno,
        Long unidadeId,
        String unidadeNome,
        boolean podeEditar,
        boolean podeSemear,
        List<ServidorHabilitadoResposta> servidores) {}
