package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import java.util.List;

/**
 * Espelho dos payloads da API corporativa. Mantido a mao e minimo: so os campos
 * que o Goianao usa. O que a API acrescentar no futuro e ignorado — e o
 * comportamento padrao do Jackson no Spring Boot.
 */
final class RespostasConnectTj {

    private RespostasConnectTj() {}

    /** Item de {@code /unidades/estrutura-hierarquica}. */
    record UnidadeHierarquia(
            Long cdgUnidade,
            String nomeUnidade,
            Long cdgUnidadePai,
            String nomeUnidadePai,
            String nomeComarca,
            Integer nivelHierarquico) {}

    /** {@code /unidades/{cod}} — traz o responsavel, mas nao a comarca. */
    record UnidadeDetalhe(
            Long codUnidade,
            String nome,
            Long matriculaResponsavel,
            String nomeServidorResponsavel) {}

    /** Item de {@code /unidades/buscar-unidades-ativas}. */
    record UnidadeAtiva(Long cdgUnidade, String nmeUnidade, String nomeComarca) {}

    /** Item de {@code /unidades/{cod}/lotados} — sem e-mail e sem CPF. */
    record Lotado(
            Long cdgOrdem,
            String nome,
            String nmeSitfunc,
            Long cdgUnidadeLotado) {}

    record Pagina(Integer size, Integer number, Long totalElements, Integer totalPages) {}

    record LotadosPagina(List<Lotado> content, Pagina page) {}

    /** {@code /servidores/buscar-por-matricula} e o miolo da busca por login. */
    record Servidor(
            Long cdgOrdem,
            String nome,
            String nmrCpf,
            Long cdgUnidadeLotacao,
            String nmeUnidadeLotado,
            String endEmail,
            String nmeSitfunc) {}

    record BuscaPorLogin(Servidor servidor, String mensagem) {}

    /**
     * Conta no AD, de {@code /ad/usuarios}. Nao traz e-mail — traz o
     * {@code samaccountname}, que e o login, e o e-mail corporativo e o login
     * mais o dominio.
     */
    record ContaAd(
            String samaccountname,
            String cPFNumber,
            String matricula,
            String useraccountcontrolLabel) {}
}
