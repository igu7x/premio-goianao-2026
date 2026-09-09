package br.jus.tjgo.goianao.layout.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.config.GoianaoProperties;
import br.jus.tjgo.goianao.suporte.ArteDeTeste;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Validacao da arte enviada (003/RNF-4)")
class ValidadorDeArteTest {

    private final ValidadorDeArte validador = new ValidadorDeArte(new GoianaoProperties(
            new GoianaoProperties.Jwt("x".repeat(40), 8),
            "http://localhost",
            new GoianaoProperties.Storage("banco", "./target/artes-teste"),
            new GoianaoProperties.Layout(2400, 0.05),
            new GoianaoProperties.Cors(List.of("http://localhost")),
            new GoianaoProperties.Login(false, false),
            new GoianaoProperties.VerificacaoPublica(30),
            false));

    @Test
    @DisplayName("aceita A4 paisagem a 300 DPI")
    void aceitaPadrao() {
        DimensoesArte dimensoes = validador.validar(ArteDeTeste.valida(), "image/png");

        assertThat(dimensoes.largura()).isEqualTo(ArteDeTeste.LARGURA);
        assertThat(dimensoes.altura()).isEqualTo(ArteDeTeste.ALTURA);
    }

    @Test
    @DisplayName("recusa retrato: as coordenadas do layout pressupoem paisagem")
    void recusaRetrato() {
        assertThatThrownBy(() -> validador.validar(ArteDeTeste.retrato(), "image/png"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("paisagem");
    }

    @Test
    @DisplayName("recusa resolucao abaixo do minimo")
    void recusaBaixaResolucao() {
        assertThatThrownBy(() -> validador.validar(ArteDeTeste.baixaResolucao(), "image/png"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Resolução insuficiente");
    }

    @Test
    @DisplayName("recusa proporcao fora do A4")
    void recusaProporcaoErrada() {
        assertThatThrownBy(() -> validador.validar(ArteDeTeste.proporcaoErrada(), "image/png"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Proporção");
    }

    @Test
    @DisplayName("recusa formato nao suportado e arquivo que nao e imagem")
    void recusaFormato() {
        assertThatThrownBy(() -> validador.validar(ArteDeTeste.valida(), "application/pdf"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("PNG ou JPEG");

        assertThatThrownBy(() -> validador.validar("nao sou imagem".getBytes(), "image/png"))
                .isInstanceOf(RegraDeNegocioException.class);
    }
}
