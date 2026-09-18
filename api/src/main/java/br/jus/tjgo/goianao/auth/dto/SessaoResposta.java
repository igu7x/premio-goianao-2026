package br.jus.tjgo.goianao.auth.dto;

import java.util.List;

/**
 * Resposta do login: token de sessao e a identidade que ele carrega.
 *
 * <p>Desde a feature 011 traz tambem a <b>edicao</b> em que a sessao entrou e as
 * demais edicoes em que a pessoa existe. Os papeis sao os da edicao corrente —
 * trocar de edicao pode mudar o que ela pode fazer, e por isso a troca emite um
 * token novo.
 */
public record SessaoResposta(
        String token,
        long expiraEmSegundos,
        String email,
        String nome,
        List<String> papeis,
        EdicaoDaSessao edicao,
        List<EdicaoDaSessao> edicoesDisponiveis) {}
