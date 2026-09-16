package br.jus.tjgo.goianao.usuario.atualizacao;

import br.jus.tjgo.goianao.sincronizacao.dto.LotacaoAplicada;
import java.time.LocalDateTime;

/**
 * Retrato da atualizacao da base de usuarios, para a tela acompanhar.
 *
 * <p>Imutavel: cada passo da varredura troca o retrato inteiro, e a tela que
 * pergunta no meio nunca ve metade de um numero atualizado.
 *
 * @param unidadeAtual     a que esta sendo varrida agora; e o que da sinal de vida
 *                         numa operacao que pode levar meia hora
 * @param unidadesComFalha o RH nao respondeu por elas; a varredura seguiu, e
 *                         rodar de novo as completa sem duplicar ninguem
 * @param semEmail         pessoas que ficaram de fora por nao ter e-mail nem no RH
 *                         nem no AD — sem ele o login nao as reconheceria
 */
public record SituacaoDaAtualizacao(
        Estado estado,
        EscopoDaAtualizacao escopo,
        int unidadesTotal,
        int unidadesProcessadas,
        String unidadeAtual,
        int pessoas,
        int criados,
        int atualizados,
        int semEmail,
        int unidadesComFalha,
        LocalDateTime iniciadaEm,
        LocalDateTime terminadaEm,
        String mensagem) {

    public enum Estado { NUNCA_EXECUTADA, EM_ANDAMENTO, CONCLUIDA, FALHOU }

    static SituacaoDaAtualizacao nuncaExecutada() {
        return new SituacaoDaAtualizacao(Estado.NUNCA_EXECUTADA, null, 0, 0, null, 0, 0, 0, 0, 0,
                null, null, null);
    }

    static SituacaoDaAtualizacao iniciada(EscopoDaAtualizacao escopo) {
        return new SituacaoDaAtualizacao(Estado.EM_ANDAMENTO, escopo, 0, 0, null, 0, 0, 0, 0, 0,
                AtualizacaoDaBaseDeUsuarios.agora(), null, null);
    }

    SituacaoDaAtualizacao comTotal(int total) {
        return new SituacaoDaAtualizacao(estado, escopo, total, unidadesProcessadas, unidadeAtual,
                pessoas, criados, atualizados, semEmail, unidadesComFalha, iniciadaEm, terminadaEm,
                mensagem);
    }

    SituacaoDaAtualizacao naUnidade(String nome) {
        return new SituacaoDaAtualizacao(estado, escopo, unidadesTotal, unidadesProcessadas, nome,
                pessoas, criados, atualizados, semEmail, unidadesComFalha, iniciadaEm, terminadaEm,
                mensagem);
    }

    SituacaoDaAtualizacao somar(LotacaoAplicada unidade) {
        return new SituacaoDaAtualizacao(estado, escopo, unidadesTotal, unidadesProcessadas + 1,
                unidadeAtual, pessoas + unidade.lotadosNoRh(), criados + unidade.criados(),
                atualizados + unidade.atualizados(), semEmail + unidade.semEmail(),
                unidadesComFalha, iniciadaEm, terminadaEm, mensagem);
    }

    SituacaoDaAtualizacao comFalhaNaUnidade() {
        return new SituacaoDaAtualizacao(estado, escopo, unidadesTotal, unidadesProcessadas + 1,
                unidadeAtual, pessoas, criados, atualizados, semEmail, unidadesComFalha + 1,
                iniciadaEm, terminadaEm, mensagem);
    }

    SituacaoDaAtualizacao concluida() {
        return new SituacaoDaAtualizacao(Estado.CONCLUIDA, escopo, unidadesTotal,
                unidadesProcessadas, null, pessoas, criados, atualizados, semEmail,
                unidadesComFalha, iniciadaEm, AtualizacaoDaBaseDeUsuarios.agora(), mensagem);
    }

    SituacaoDaAtualizacao falhou(String motivo) {
        return new SituacaoDaAtualizacao(Estado.FALHOU, escopo, unidadesTotal,
                unidadesProcessadas, unidadeAtual, pessoas, criados, atualizados, semEmail,
                unidadesComFalha, iniciadaEm, AtualizacaoDaBaseDeUsuarios.agora(), motivo);
    }
}
