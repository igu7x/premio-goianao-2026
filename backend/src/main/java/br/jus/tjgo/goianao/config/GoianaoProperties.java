package br.jus.tjgo.goianao.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracoes do sistema, agrupadas sob o prefixo {@code goianao}. */
@ConfigurationProperties(prefix = "goianao")
public record GoianaoProperties(
        Jwt jwt,
        String baseVerificacao,
        Storage storage,
        Layout layout,
        Cors cors,
        VerificacaoPublica verificacaoPublica,
        boolean dadosDemo) {

    public record Jwt(String segredo, int expiracaoHoras) {}

    public record Storage(String dir) {}

    /**
     * Padrao esperado da arte do certificado: A4 paisagem a 300 DPI
     * (~3508x2480 px), conforme 003/RNF-4.
     */
    public record Layout(int larguraMinima, double toleranciaProporcao) {}

    public record Cors(List<String> origens) {}

    public record VerificacaoPublica(int consultasPorMinuto) {}
}
