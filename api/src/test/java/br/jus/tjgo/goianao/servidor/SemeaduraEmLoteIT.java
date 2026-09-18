package br.jus.tjgo.goianao.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.servidor.dto.SituacaoDaSemeadura;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeRepository;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;

/**
 * Semeadura em lote, a fila que a planilha de responsaveis dispara.
 *
 * <p><b>Este teste comita de proposito.</b> A fila roda noutra thread, com a
 * transacao dela: sem o commit, ela nao enxergaria a edicao nem a unidade
 * criadas aqui, e o teste provaria o contrario do que se quer.
 *
 * <p>Comitar num banco compartilhado pelos demais testes cobra o preco de
 * limpar depois — e por isso o ano e o nome da unidade sao exclusivos desta
 * classe, e o {@code @AfterEach} apaga o que ficou. Sem isso, o proximo teste
 * que criasse "1ª Vara Cível" esbarraria no indice unico de nome.
 */
@DisplayName("Semeadura em lote, em segundo plano")
class SemeaduraEmLoteIT extends TesteDeIntegracao {

    private static final int ANO = 2090;
    private static final String UNIDADE = "Vara da Semeadura em Lote";

    @Autowired private SemeaduraEmLote semeadura;
    @Autowired private ServidorHabilitadoService servidores;
    @Autowired private UnidadeRepository unidades;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("a fila semeia as unidades depois que a requisicao ja respondeu")
    void semeiaEmSegundoPlano() {
        Edicao edicao = edicaoVigente(ANO);
        UnidadeJudiciaria unidade = unidades.save(new UnidadeJudiciaria(UNIDADE));
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE, Selo.OURO);
        Long edicaoId = edicao.getId();
        Long unidadeId = unidade.getId();

        // A thread da fila le o banco por conta propria: sem commit, nao veria
        // nada do que foi montado aqui.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        atuandoComo(EMAIL_ADMIN);
        Map<Long, String> fila = new LinkedHashMap<>();
        fila.put(unidadeId, UNIDADE);
        semeadura.semear(edicaoId, edicao.getAno(), fila);

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> !semeadura.emAndamento());

        SituacaoDaSemeadura fim = semeadura.situacao();
        assertThat(fim.estado()).isEqualTo(SituacaoDaSemeadura.Estado.CONCLUIDA);
        assertThat(fim.unidadesTotal()).isEqualTo(1);
        assertThat(fim.incluidos()).isPositive();
        assertThat(fim.unidadesComFalha()).isZero();

        assertThat(servidores.listar(edicaoId, unidadeId))
                .as("a lista existe depois que a fila passou")
                .isNotEmpty();
    }

    /** Na ordem das chaves estrangeiras, e so o que este teste criou. */
    @AfterEach
    void limpar() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
        jdbc.update("delete from servidor_habilitado where edicao_id in"
                + " (select id from edicao where ano = ?)", ANO);
        jdbc.update("delete from reconhecimento where magistrado_id in"
                + " (select id from magistrado_reconhecido where edicao_id in"
                + " (select id from edicao where ano = ?))", ANO);
        jdbc.update("delete from magistrado_reconhecido where edicao_id in"
                + " (select id from edicao where ano = ?)", ANO);
        jdbc.update("delete from layout_certificado where edicao_id in"
                + " (select id from edicao where ano = ?)", ANO);
        jdbc.update("delete from edicao where ano = ?", ANO);
        jdbc.update("delete from unidade_judiciaria where nome = ?", UNIDADE);
    }
}
