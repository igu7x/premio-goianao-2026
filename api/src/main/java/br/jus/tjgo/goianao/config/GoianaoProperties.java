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
        Login login,
        VerificacaoPublica verificacaoPublica,
        boolean dadosDemo) {

    public GoianaoProperties {
        login = login == null ? new Login(false, false) : login;
    }

    public record Jwt(String segredo, int expiracaoHoras) {}

    /**
     * Quais portas de entrada existem <b>neste</b> ambiente, alem do SSO.
     *
     * <p>Ambas nascem <b>desligadas</b>, e isso e deliberado: esquecer de
     * definir a variavel em producao deixa o sistema no estado seguro, enquanto
     * o padrao inverso deixaria uma porta aberta que ninguem pediu para abrir.
     *
     * @param senha login por e-mail e senha do cadastro proprio. Existe em
     *              desenvolvimento e em homologacao, onde nem todo mundo tem
     *              conta no Keycloak de teste. Em producao vale so o SSO.
     * @param mock  login por identidade de teste, que <b>dispensa credencial</b>:
     *              basta informar o CPF e o sistema emite a sessao. Serve ao
     *              desenvolvimento sem SSO e a nada mais — habilita-lo num
     *              ambiente alcancavel de fora entrega o sistema a quem quiser,
     *              porque {@code /api/auth/usuarios-mock} lista as identidades
     *              e uma delas e administrador.
     */
    public record Login(boolean senha, boolean mock) {}

    /**
     * Armazenamento das artes. {@code tipo} vale {@code banco} (padrao) ou
     * {@code filesystem}; {@code dir} so e usado no segundo caso.
     *
     * O padrao e o banco porque o pod do OpenShift nao tem disco que sobreviva
     * ao restart, e porque a arte faz parte da autenticidade do certificado:
     * uma reemissao feita daqui a anos precisa dela, entao ela tem que estar no
     * mesmo backup da tabela de emissoes.
     */
    public record Storage(String tipo, String dir) {}

    /**
     * Padrao esperado da arte do certificado: A4 paisagem a 300 DPI
     * (~3508x2480 px), conforme 003/RNF-4.
     */
    public record Layout(int larguraMinima, double toleranciaProporcao) {}

    public record Cors(List<String> origens) {}

    public record VerificacaoPublica(int consultasPorMinuto) {}
}
