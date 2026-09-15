package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.integracao.egesp.LotadoEgesp;
import br.jus.tjgo.goianao.integracao.egesp.UnidadeEgesp;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Conversa com a API corporativa <b>de verdade</b>.
 *
 * <p>Fica desligado por padrao e so roda quando as quatro variaveis existem no
 * ambiente — nenhuma credencial entra no repositorio, que e publico. E o teste
 * que responde "a integracao funciona mesmo?", coisa que mock nenhum responde:
 * ele exercita token, paginacao e a resolucao do e-mail pela matricula contra o
 * servidor real.
 *
 * <p>Para rodar:
 *
 * <pre>
 * $env:GOIANAO_CONNECTTJ_URL="https://connecttj-api-stag.tjgo.jus.br"
 * $env:GOIANAO_CONNECTTJ_TOKEN_URL="https://sso.tjgo.jus.br/auth/realms/DG-TST/protocol/openid-connect/token"
 * $env:GOIANAO_CONNECTTJ_CLIENT_ID="..."
 * $env:GOIANAO_CONNECTTJ_SECRET="..."
 * mvn -o test -Dtest=ConnectTjRealIT
 * </pre>
 */
@DisplayName("API corporativa de verdade (so com credenciais no ambiente)")
@EnabledIfEnvironmentVariable(named = "GOIANAO_CONNECTTJ_CLIENT_ID", matches = ".+")
class ConnectTjRealIT {

    /** SGJT: a area de tecnologia, conjunto pequeno e conhecido para conferir. */
    private static final long CODIGO_SGJT = 901_190_605L;

    private ConnectTjEgespClient cliente() {
        ConnectTjProperties props = new ConnectTjProperties(
                System.getenv("GOIANAO_CONNECTTJ_URL"),
                System.getenv("GOIANAO_CONNECTTJ_TOKEN_URL"),
                System.getenv("GOIANAO_CONNECTTJ_CLIENT_ID"),
                System.getenv("GOIANAO_CONNECTTJ_SECRET"),
                100,
                "tjgo.jus.br",
                6);
        assertThat(props.habilitado())
                .as("as quatro variaveis precisam estar definidas")
                .isTrue();
        return new ConnectTjEgespClient(props, RestClient.builder());
    }

    @Test
    @DisplayName("a hierarquia da SGJT volta com codigo, nome e comarca")
    void hierarquia() {
        List<UnidadeEgesp> arvore = cliente().hierarquia(CODIGO_SGJT);

        assertThat(arvore).isNotEmpty();
        assertThat(arvore).allSatisfy(u -> {
            assertThat(u.codigo()).isNotNull();
            assertThat(u.nome()).isNotBlank();
        });
        assertThat(arvore).anyMatch(u -> u.codigo() == CODIGO_SGJT);
    }

    /**
     * O que importa aqui nao e quem esta lotado, e sim que <b>a lotacao vem
     * inteira</b>: uma unidade com mais gente do que cabe numa pagina precisa
     * devolver todas, senao a tela de sincronizacao aponta como orfao quem
     * ficou na pagina seguinte.
     */
    @Test
    @DisplayName("os lotados vem com matricula e nome, com as paginas ja percorridas")
    void lotados() {
        List<UnidadeEgesp> arvore = cliente().hierarquia(CODIGO_SGJT);
        UnidadeEgesp comGente = arvore.stream()
                .filter(u -> !cliente().lotados(u.codigo()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("nenhuma unidade da SGJT tem lotados"));

        List<LotadoEgesp> lotados = cliente().lotados(comGente.codigo());

        assertThat(lotados).isNotEmpty();
        assertThat(lotados).allSatisfy(l -> {
            assertThat(l.matricula()).isPositive();
            assertThat(l.nome()).isNotBlank();
        });
    }

    /**
     * A resolucao do e-mail pela matricula e o que sustenta o desenho todo: a
     * listagem de lotados nao traz e-mail, e sem e-mail ninguem e reconhecido no
     * login (DI-24). O teste aceita que <b>parte</b> das pessoas nao tenha
     * e-mail no RH — e o caso de residentes e estagiarios —, mas exige que
     * alguem tenha, senao a integracao nao serve para nada.
     */
    @Test
    @DisplayName("a matricula resolve o e-mail de pelo menos parte dos lotados")
    void emailPorMatricula() {
        ConnectTjEgespClient cliente = cliente();
        UnidadeEgesp comGente = cliente.hierarquia(CODIGO_SGJT).stream()
                .filter(u -> !cliente.lotados(u.codigo()).isEmpty())
                .findFirst()
                .orElseThrow();

        long comEmail = cliente.lotados(comGente.codigo()).stream()
                .map(l -> cliente.servidorPorMatricula(l.matricula()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(s -> Email.valido(s.email()))
                .count();

        assertThat(comEmail)
                .as("ninguem da unidade %s tem e-mail no RH", comGente.nome())
                .isPositive();
    }
}
