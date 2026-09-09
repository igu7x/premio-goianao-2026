package br.jus.tjgo.goianao.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.jus.tjgo.goianao.config.GoianaoProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * O repositorio e publico, entao o segredo padrao do application.yml e
 * conhecido por qualquer pessoa. Fora de desenvolvimento a aplicacao tem que
 * recusar subir com ele: assinando com chave publica, qualquer um forja a
 * sessao de um administrador, e nada no comportamento do sistema denuncia isso.
 */
@DisplayName("Segredo de assinatura do JWT")
class SegredoJwtTest {

    private static final String SEGREDO_PUBLICO =
            "desenvolvimento-goianao-tjgo-chave-local-nao-use-em-producao";
    private static final String SEGREDO_PROPRIO =
            "um-segredo-de-producao-com-mais-de-trinta-e-dois-bytes";

    @Test
    @DisplayName("recusa subir em producao com o segredo publico do repositorio")
    void recusaSegredoPublicoForaDeDesenvolvimento() {
        MockEnvironment producao = new MockEnvironment();
        producao.setActiveProfiles("postgres");

        assertThatThrownBy(() -> new JwtService(props(SEGREDO_PUBLICO), producao))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOIANAO_JWT_SEGREDO")
                // A mensagem tem que dizer o que fazer, nao so o que houve.
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("aceita o segredo publico em desenvolvimento, onde ele existe para isso")
    void aceitaEmDesenvolvimento() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatCode(() -> new JwtService(props(SEGREDO_PUBLICO), dev)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("com segredo proprio, sobe em qualquer perfil")
    void aceitaSegredoProprio() {
        MockEnvironment producao = new MockEnvironment();
        producao.setActiveProfiles("postgres");

        JwtService servico = new JwtService(props(SEGREDO_PROPRIO), producao);
        assertThat(servico.validade().toHours()).isEqualTo(8);
    }

    @Test
    @DisplayName("sem perfil algum tambem e tratado como producao")
    void semPerfilEhProducao() {
        assertThatThrownBy(() -> new JwtService(props(SEGREDO_PUBLICO), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static GoianaoProperties props(String segredo) {
        return new GoianaoProperties(
                new GoianaoProperties.Jwt(segredo, 8),
                "https://exemplo",
                new GoianaoProperties.Storage("banco", "./target/artes-teste"),
                new GoianaoProperties.Layout(2400, 0.05),
                new GoianaoProperties.Cors(List.of("https://exemplo")),
                new GoianaoProperties.Login(false, false),
                new GoianaoProperties.VerificacaoPublica(30),
                false);
    }
}
