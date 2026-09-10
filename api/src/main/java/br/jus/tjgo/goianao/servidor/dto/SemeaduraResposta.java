package br.jus.tjgo.goianao.servidor.dto;

/**
 * Resultado da semeadura a partir do EGESP. Ressemear <b>mescla</b>: acrescenta
 * quem faltava e nao desfaz ajustes manuais — nem remove inclusoes MANUAL, nem
 * reativa quem foi removido de proposito (008, ponto resolvido).
 *
 * @param ignoradosSemEmail servidores que o EGESP devolveu sem e-mail valido.
 *                          O e-mail e a chave da pessoa (DI-24): sem ele nao ha
 *                          como a pessoa ser reconhecida no login, entao ela
 *                          nao entra — e o numero aparece, em vez de sumir.
 */
public record SemeaduraResposta(
        int retornadosPeloEgesp,
        int incluidos,
        int jaExistentes,
        int preservadosRemovidos,
        int ignoradosSemEmail,
        int totalAtivos) {}
