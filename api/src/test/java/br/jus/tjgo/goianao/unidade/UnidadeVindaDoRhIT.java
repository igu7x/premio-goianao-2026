package br.jus.tjgo.goianao.unidade;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.integracao.egesp.MockEgespClient;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Unidade criada a partir do nome, quando o magistrado e reconhecido numa
 * unidade que ainda nao existe no sistema.
 *
 * <p>Ela precisa nascer com o codigo do SIEDOS. Sem ele, a unidade fica fora de
 * tudo o que hoje e por codigo: a pagina da unidade nao consulta o RH, a
 * planilha de responsaveis nao a encontra e a sincronizacao a mostra como
 * desatualizada — embora o RH tenha devolvido o codigo na mesma consulta.
 */
@DisplayName("Unidade criada pelo nome vindo do RH")
class UnidadeVindaDoRhIT extends TesteDeIntegracao {

    @Autowired private UnidadeService servico;
    @Autowired private UnidadeRepository unidades;

    @Test
    @DisplayName("nasce com o codigo e a comarca que o RH devolveu")
    void nasceComCodigo() {
        UnidadeJudiciaria criada = servico.garantirDoEgesp(UNIDADE_A);

        UnidadeJudiciaria gravada = unidades.findById(criada.getId()).orElseThrow();
        assertThat(gravada.getCodigoSiedos()).isEqualTo(MockEgespClient.CODIGO_RAIZ + 1);
        assertThat(gravada.getComarca()).isEqualTo("Goiânia");
    }
}
