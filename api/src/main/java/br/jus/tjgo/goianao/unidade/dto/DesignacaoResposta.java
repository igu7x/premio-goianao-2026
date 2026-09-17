package br.jus.tjgo.goianao.unidade.dto;

import br.jus.tjgo.goianao.servidor.dto.SemeaduraResposta;

/**
 * Resultado de designar quem responde pela unidade.
 *
 * <p>Designar deixou de ser so gravar o responsavel: a lista de servidores
 * habilitados da unidade e semeada no mesmo ato, para que ele encontre a equipe
 * pronta em vez de uma tela vazia. A semeadura vai junto na resposta porque a
 * tela precisa dizer quantos entraram — e, quando o RH nao responde, dizer isso
 * sem fingir que a designacao falhou: ela foi gravada.
 *
 * @param semeadura resultado da semeadura, ou nulo quando ela nao aconteceu
 * @param aviso     por que a lista nao foi semeada; nulo quando foi
 */
public record DesignacaoResposta(
        UnidadeResposta unidade,
        SemeaduraResposta semeadura,
        String aviso) {}
