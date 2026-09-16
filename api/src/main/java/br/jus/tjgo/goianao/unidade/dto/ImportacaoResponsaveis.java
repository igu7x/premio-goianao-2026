package br.jus.tjgo.goianao.unidade.dto;

import java.util.List;

/**
 * Relatorio da planilha de responsaveis pelas unidades.
 *
 * <p>Uma linha ruim nao derruba o lote: ela volta em {@code erros}, com o texto
 * original, e as demais sao gravadas. Planilha de tribunal chega com unidade
 * extinta, e-mail errado e linha em branco no meio — recusar tudo por causa de
 * uma obrigaria a refazer o arquivo inteiro para corrigir um nome.
 *
 * @param designados      unidades que ganharam responsavel
 * @param usuariosCriados magistrados que ainda nao existiam no sistema
 * @param papelConcedido  ja existiam sem o papel de magistrado, que a designacao
 *                        exige
 * @param substituidos    ja tinham outro responsavel; a planilha e ato humano, e
 *                        troca o que estava la
 * @param jaEram          a planilha confirmou quem ja respondia pela unidade
 * @param reconhecimentos selos gravados na edicao; repetir a planilha nao conta
 *                        de novo o que ja estava la
 * @param edicaoAno       em que edicao os selos entraram — a planilha nao diz,
 *                        entao a tela precisa mostrar qual foi
 */
public record ImportacaoResponsaveis(
        int linhasLidas,
        int designados,
        int usuariosCriados,
        int papelConcedido,
        int substituidos,
        int jaEram,
        int reconhecimentos,
        int edicaoAno,
        List<ErroDeLinha> erros) {

    public record ErroDeLinha(int linha, String conteudo, String motivo) {}
}
