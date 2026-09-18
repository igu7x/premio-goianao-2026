package br.jus.tjgo.goianao.servidor.dto;

import br.jus.tjgo.goianao.servidor.SemeaduraEmLote;
import java.time.LocalDateTime;

/**
 * Retrato da semeadura em lote, para a tela acompanhar.
 *
 * <p>Imutavel: cada unidade troca o retrato inteiro, e a tela que pergunta no
 * meio nunca ve metade de um numero atualizado.
 *
 * @param unidadeAtual     a que esta sendo semeada agora; e o sinal de vida numa
 *                         operacao que leva minutos
 * @param unidadesComFalha o RH nao respondeu por elas; a fila seguiu, e semear de
 *                         novo as completa sem duplicar ninguem
 * @param ultimaFalha      nome da ultima unidade que falhou, para a tela nomear o
 *                         problema em vez de so contar
 */
public record SituacaoDaSemeadura(
        Estado estado,
        Integer edicaoAno,
        int unidadesTotal,
        int unidadesProcessadas,
        String unidadeAtual,
        int incluidos,
        int jaExistentes,
        int semEmail,
        int unidadesComFalha,
        String ultimaFalha,
        LocalDateTime iniciadaEm,
        LocalDateTime terminadaEm,
        String mensagem) {

    public enum Estado { NUNCA_EXECUTADA, EM_ANDAMENTO, CONCLUIDA, FALHOU }

    public static SituacaoDaSemeadura nuncaExecutada() {
        return new SituacaoDaSemeadura(Estado.NUNCA_EXECUTADA, null, 0, 0, null, 0, 0, 0, 0, null,
                null, null, null);
    }

    public static SituacaoDaSemeadura iniciada(Integer edicaoAno, int total) {
        return new SituacaoDaSemeadura(Estado.EM_ANDAMENTO, edicaoAno, total, 0, null, 0, 0, 0, 0,
                null, SemeaduraEmLote.agora(), null, null);
    }

    public SituacaoDaSemeadura naUnidade(String nome) {
        return new SituacaoDaSemeadura(estado, edicaoAno, unidadesTotal, unidadesProcessadas, nome,
                incluidos, jaExistentes, semEmail, unidadesComFalha, ultimaFalha, iniciadaEm,
                terminadaEm, mensagem);
    }

    public SituacaoDaSemeadura somar(SemeaduraResposta unidade) {
        return new SituacaoDaSemeadura(estado, edicaoAno, unidadesTotal, unidadesProcessadas + 1,
                unidadeAtual, incluidos + unidade.incluidos(), jaExistentes + unidade.jaExistentes(),
                semEmail + unidade.ignoradosSemEmail(), unidadesComFalha, ultimaFalha, iniciadaEm,
                terminadaEm, mensagem);
    }

    public SituacaoDaSemeadura comFalha(String unidade) {
        return new SituacaoDaSemeadura(estado, edicaoAno, unidadesTotal, unidadesProcessadas + 1,
                unidadeAtual, incluidos, jaExistentes, semEmail, unidadesComFalha + 1, unidade,
                iniciadaEm, terminadaEm, mensagem);
    }

    public SituacaoDaSemeadura concluida() {
        return new SituacaoDaSemeadura(Estado.CONCLUIDA, edicaoAno, unidadesTotal,
                unidadesProcessadas, null, incluidos, jaExistentes, semEmail, unidadesComFalha,
                ultimaFalha, iniciadaEm, SemeaduraEmLote.agora(), mensagem);
    }

    public SituacaoDaSemeadura falhou(String motivo) {
        return new SituacaoDaSemeadura(Estado.FALHOU, edicaoAno, unidadesTotal,
                unidadesProcessadas, unidadeAtual, incluidos, jaExistentes, semEmail,
                unidadesComFalha, ultimaFalha, iniciadaEm, SemeaduraEmLote.agora(), motivo);
    }
}
