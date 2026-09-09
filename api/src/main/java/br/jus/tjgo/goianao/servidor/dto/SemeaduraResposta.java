package br.jus.tjgo.goianao.servidor.dto;

/**
 * Resultado da semeadura a partir do EGESP. Ressemear <b>mescla</b>: acrescenta
 * quem faltava e nao desfaz ajustes manuais — nem remove inclusoes MANUAL, nem
 * reativa quem foi removido de proposito (008, ponto resolvido).
 */
public record SemeaduraResposta(
        int retornadosPeloEgesp,
        int incluidos,
        int jaExistentes,
        int preservadosRemovidos,
        int totalAtivos) {}
