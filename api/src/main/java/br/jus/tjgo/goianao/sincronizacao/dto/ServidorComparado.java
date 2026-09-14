package br.jus.tjgo.goianao.sincronizacao.dto;

import br.jus.tjgo.goianao.servidor.OrigemServidor;

/**
 * Um servidor visto dos dois lados, dentro de uma edicao x unidade.
 *
 * <p>A tela e exclusiva do superadministrador, que ja pode editar qualquer
 * lista: por isso o e-mail vai inteiro (DI-10). O {@code servidorHabilitadoId}
 * e o que a remocao usa — dado pessoal nao vai na URL.
 *
 * @param origem nulo quando a pessoa ainda nao esta na lista da edicao
 */
public record ServidorComparado(
        ItemSincronizacao situacao,
        Long matricula,
        String nome,
        String email,
        Long servidorHabilitadoId,
        OrigemServidor origem,
        boolean semEmailNaApi) {}
