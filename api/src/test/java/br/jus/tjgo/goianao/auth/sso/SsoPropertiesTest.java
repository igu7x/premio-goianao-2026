package br.jus.tjgo.goianao.auth.sso;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Normalizacao dos enderecos do SSO.
 *
 * <p>Cobre a falha que custou um deploy em homologacao: a URL do Keycloak veio
 * sem {@code https://}, o {@code Location} do redirecionamento saiu relativo, e
 * o navegador pediu
 * {@code /api/auth/sso/sso.tjgo.jus.br/realms/...} a nossa propria API, que
 * respondeu 404. Nada no erro apontava para a variavel de ambiente.
 */
@DisplayName("Enderecos do SSO")
class SsoPropertiesTest {

    private static SsoProperties com(String url, String redirectUri, String urlFrontend) {
        return new SsoProperties(url, "tjgo.gov-tst", "goianao-stag", "segredo",
                redirectUri, urlFrontend, List.of(), null);
    }

    @Test
    @DisplayName("completa com https o endereco que veio sem esquema")
    void completaEsquemaAusente() {
        SsoProperties props = com("sso.tjgo.jus.br", "goianao-api.tjgo.jus.br/api/auth/sso/callback",
                "goianao.tjgo.jus.br");

        assertThat(props.url()).isEqualTo("https://sso.tjgo.jus.br");
        assertThat(props.redirectUri())
                .isEqualTo("https://goianao-api.tjgo.jus.br/api/auth/sso/callback");
        assertThat(props.urlFrontend()).isEqualTo("https://goianao.tjgo.jus.br");
    }

    @Test
    @DisplayName("a URL de autorizacao fica absoluta — e o que impede o 404 na propria API")
    void urlDeAutorizacaoAbsoluta() {
        assertThat(com("sso.tjgo.jus.br", null, null).urlAutorizacao())
                .startsWith("https://sso.tjgo.jus.br/realms/tjgo.gov-tst");
    }

    @Test
    @DisplayName("nao mexe no endereco que ja tem esquema")
    void preservaEsquemaExistente() {
        assertThat(com("https://sso.tjgo.jus.br/auth", null, null).url())
                .isEqualTo("https://sso.tjgo.jus.br/auth");
        assertThat(com("http://localhost:8180", null, null).url())
                .isEqualTo("http://localhost:8180");
    }

    @Test
    @DisplayName("barra final e removida para nao gerar // no meio do caminho")
    void removeBarraFinal() {
        assertThat(com("https://sso.tjgo.jus.br/", null, null).issuer())
                .isEqualTo("https://sso.tjgo.jus.br/realms/tjgo.gov-tst");
    }

    @Test
    @DisplayName("ausencia de configuracao continua sendo ausencia, nao https vazio")
    void naoInventaEnderecoQuandoVazio() {
        SsoProperties props = com(null, "", null);

        assertThat(props.url()).isNull();
        assertThat(props.redirectUri()).isEmpty();
        assertThat(props.habilitado()).isFalse();
    }
}
