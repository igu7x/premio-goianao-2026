package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.jus.tjgo.goianao.integracao.egesp.LotadoEgesp;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * O adaptador da API corporativa, contra um servidor simulado.
 *
 * <p>Dois comportamentos merecem teste porque falham em silencio: a paginacao
 * dos lotados — meia lotacao faria a tela apontar como "orfao" quem ficou na
 * pagina seguinte — e a renovacao do token, que vale 5 minutos e vence no meio
 * de uma varredura.
 */
@DisplayName("Adaptador da API corporativa (ConnectTJ)")
class ConnectTjEgespClientTest {

    private static final String API = "https://rh.example";
    private static final String TOKEN_URL = "https://sso.example/token";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;
    private ConnectTjEgespClient cliente;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ConnectTjEgespClient(
                new ConnectTjProperties(API, TOKEN_URL, "goianao", "segredo", 2, "tjgo.jus.br"),
                builder);
    }

    private void esperarToken() {
        servidor.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"abc\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("percorre todas as paginas de lotados antes de devolver")
    void percorreTodasAsPaginas() {
        esperarToken();
        servidor.expect(requestTo(API + "/api/v1/unidades/77/lotados"
                        + "?incluirUnidadesSubordinadas=false&page=0&size=2"))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("""
                        {"content":[
                          {"cdgOrdem":1,"nome":"Primeira","nmeSitfunc":"ESTATUTARIO"},
                          {"cdgOrdem":2,"nome":"Segunda","nmeSitfunc":"ESTATUTARIO"}],
                         "page":{"size":2,"number":0,"totalElements":3,"totalPages":2}}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(API + "/api/v1/unidades/77/lotados"
                        + "?incluirUnidadesSubordinadas=false&page=1&size=2"))
                .andRespond(withSuccess("""
                        {"content":[
                          {"cdgOrdem":3,"nome":"Terceira","nmeSitfunc":"ESTATUTARIO"}],
                         "page":{"size":2,"number":1,"totalElements":3,"totalPages":2}}
                        """, MediaType.APPLICATION_JSON));

        List<LotadoEgesp> lotados = cliente.lotados(77);

        assertThat(lotados).extracting(LotadoEgesp::nome)
                .containsExactly("Primeira", "Segunda", "Terceira");
        servidor.verify();
    }

    @Test
    @DisplayName("renova o token uma vez quando a API responde 401")
    void renovaTokenNo401() {
        esperarToken();
        servidor.expect(requestTo(API + "/api/v1/servidores/buscar-por-matricula?matricula=5"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        esperarToken();
        servidor.expect(requestTo(API + "/api/v1/servidores/buscar-por-matricula?matricula=5"))
                .andRespond(withSuccess("""
                        {"cdgOrdem":5,"nome":"Fulano de Teste","nmrCpf":"00640156100",
                         "endEmail":"fulano@tjgo.jus.br"}
                        """, MediaType.APPLICATION_JSON));

        Optional<ServidorEgesp> servidorEncontrado = cliente.servidorPorMatricula(5);

        assertThat(servidorEncontrado).isPresent().get()
                .satisfies(s -> {
                    assertThat(s.email()).isEqualTo("fulano@tjgo.jus.br");
                    assertThat(s.matricula()).isEqualTo(5L);
                });
        servidor.verify();
    }

    @Test
    @DisplayName("sem e-mail no RH, reconstroi o endereco pelo login do AD")
    void recuperaEmailPeloAd() {
        esperarToken();
        servidor.expect(requestTo(API + "/api/v1/servidores/buscar-por-matricula?matricula=7"))
                .andRespond(withSuccess("""
                        {"cdgOrdem":7,"nome":"Residente de Teste","nmrCpf":"00640156100",
                         "endEmail":null}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(API + "/api/v1/ad/usuarios?cpf=00640156100"))
                .andRespond(withSuccess("""
                        [{"cn":"Residente de Teste","samaccountname":"rteste",
                          "cPFNumber":"00640156100","useraccountcontrolLabel":"ATIVA"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(cliente.servidorPorMatricula(7)).isPresent().get()
                .satisfies(s -> assertThat(s.email()).isEqualTo("rteste@tjgo.jus.br"));
        servidor.verify();
    }

    @Test
    @DisplayName("sem e-mail e sem conta no AD, a pessoa segue sem e-mail — e nao inventamos um")
    void semAdContinuaSemEmail() {
        esperarToken();
        servidor.expect(requestTo(API + "/api/v1/servidores/buscar-por-matricula?matricula=8"))
                .andRespond(withSuccess("""
                        {"cdgOrdem":8,"nome":"Sem Conta","nmrCpf":"00640156100","endEmail":null}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(API + "/api/v1/ad/usuarios?cpf=00640156100"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(cliente.servidorPorMatricula(8)).isPresent().get()
                .satisfies(s -> assertThat(s.email()).isNull());
        servidor.verify();
    }

    @Test
    @DisplayName("404 vira vazio, nao erro: matricula inexistente e resposta legitima")
    void naoEncontradoViraVazio() {
        esperarToken();
        servidor.expect(requestTo(API + "/api/v1/servidores/buscar-por-matricula?matricula=9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(cliente.servidorPorMatricula(9)).isEmpty();
        servidor.verify();
    }
}
