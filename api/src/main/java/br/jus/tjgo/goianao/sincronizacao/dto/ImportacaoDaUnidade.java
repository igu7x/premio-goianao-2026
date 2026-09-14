package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Resultado da importacao de uma unidade inteira (010): o que foi criado no
 * cadastro de usuarios e o que entrou na lista de habilitados da edicao.
 *
 * <p>Os numeros vem separados de proposito. "Criei 12 usuarios e habilitei 9"
 * conta uma historia que "12" nao conta: alguem foi removido da lista de
 * proposito, ou veio sem e-mail. Sem isso a importacao parece ter falhado
 * quando na verdade respeitou uma decisao anterior.
 *
 * @param usuariosCriados      pessoas que ainda nao existiam no cadastro
 * @param usuariosAtualizados  ja existiam; receberam os dados do RH, sem mexer
 *                             em papel nem em designacao
 * @param preservadosRemovidos estavam na lista e foram removidos a mao: a
 *                             importacao nao os ressuscita (008)
 * @param semEmail             o RH nao tem e-mail corporativo para eles, entao
 *                             nao seriam reconhecidos no login (DI-24)
 */
public record ImportacaoDaUnidade(
        int lotadosNoRh,
        int usuariosCriados,
        int usuariosAtualizados,
        int habilitadosIncluidos,
        int jaHabilitados,
        int preservadosRemovidos,
        int semEmail,
        int totalAtivos) {}
