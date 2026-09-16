package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Resumo de uma rodada da designacao de responsaveis a partir do RH.
 *
 * <p>A rodada e limitada de proposito: o RH cobra uma chamada por unidade e o
 * teto e de seis por segundo, entao varrer o tribunal inteiro de uma vez
 * estouraria o tempo da rota. A tela continua de onde parou usando
 * {@code ultimoId}.
 *
 * @param processadas       unidades examinadas nesta rodada
 * @param ultimoId          id da ultima unidade examinada; e o cursor da proxima
 *                          rodada, e e o que impede o lote de tentar de novo, para
 *                          sempre, as unidades que o RH nao sabe responder
 * @param designados        quantas ganharam responsavel agora
 * @param usuariosCriados   responsaveis que ainda nao existiam no sistema
 * @param papelConcedido    ja existiam, mas sem o papel de magistrado — que e o
 *                          que a designacao exige, porque e a tela do magistrado
 *                          que ela destrava
 * @param semResponsavelNoRh o RH nao aponta ninguem para a unidade
 * @param semEmail          o RH aponta alguem, mas sem e-mail corporativo: sem ele
 *                          a pessoa nao seria reconhecida no login
 * @param restantes         unidades que continuam sem responsavel depois desta
 *                          rodada, incluindo as que o RH nao sabe responder
 */
public record ResponsaveisDoRh(
        int processadas,
        Long ultimoId,
        int designados,
        int usuariosCriados,
        int papelConcedido,
        int semResponsavelNoRh,
        int semEmail,
        long restantes) {}
