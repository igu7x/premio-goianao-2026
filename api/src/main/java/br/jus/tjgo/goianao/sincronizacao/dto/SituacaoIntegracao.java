package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * O que a tela precisa saber antes de oferecer qualquer botao.
 *
 * <p>Nao devolve endereco nem credencial: o repositorio e publico e a resposta
 * passa pelo navegador. Dizer "ligada" ou "desligada" basta para a tela decidir
 * o que desenhar.
 */
public record SituacaoIntegracao(boolean ligada, String origemDosDados) {

    public static SituacaoIntegracao de(boolean integracaoReal) {
        return new SituacaoIntegracao(integracaoReal,
                integracaoReal ? "API corporativa do TJGO" : "dados de demonstração (mock)");
    }
}
