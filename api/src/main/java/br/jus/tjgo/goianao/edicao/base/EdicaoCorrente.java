package br.jus.tjgo.goianao.edicao.base;

import java.util.function.Supplier;

/**
 * Sobre qual edicao esta requisicao esta agindo (feature 011).
 *
 * <p>Guarda o <b>schema</b> da edicao, nao o id: e ele que a conexao precisa, e
 * mante-lo aqui evita uma consulta ao catalogo a cada conexao obtida do pool.
 * Quem traduz id ou ano em schema e {@link CatalogoDeEdicoes}.
 *
 * <p>O valor vive na thread da requisicao, posto por {@code FiltroDaEdicao} e
 * limpo por ele no fim — sempre, inclusive quando a requisicao falha. Thread de
 * pool com valor esquecido significaria a proxima requisicao atendida na base do
 * ano errado, que e exatamente o que esta feature existe para impedir.
 *
 * <p>Fora de requisicao — na subida, numa tarefa em segundo plano, no login que
 * procura a pessoa em todas as edicoes — vale {@link #executarEm}, que repoe o
 * valor anterior ao terminar e por isso pode ser aninhado.
 */
public final class EdicaoCorrente {

    private static final ThreadLocal<String> ATUAL = new ThreadLocal<>();

    /**
     * Schema usado quando a thread nao declarou nenhum: o da edicao vigente.
     *
     * <p>E o fallback de quem chega sem sessao — a pagina publica de
     * verificacao, o login — e tambem o schema que o Hibernate valida na subida.
     * Escrito por {@code BaseDaEdicao} quando a aplicacao sobe e por
     * {@code EdicaoService} quando a vigencia muda; lido de qualquer thread,
     * dai o volatile.
     */
    private static volatile String padrao;

    private EdicaoCorrente() {}

    /** O schema desta thread, ou o da edicao vigente se ela nao declarou um. */
    public static String schema() {
        String atual = ATUAL.get();
        return atual != null ? atual : padrao;
    }

    /** Declara o schema desta thread. {@code null} equivale a {@link #limpar()}. */
    public static void definir(String schema) {
        if (schema == null) {
            ATUAL.remove();
        } else {
            ATUAL.set(schema);
        }
    }

    public static void limpar() {
        ATUAL.remove();
    }

    public static void definirPadrao(String schema) {
        padrao = schema;
    }

    public static String padrao() {
        return padrao;
    }

    /** Roda o trecho na base da edicao indicada e devolve o contexto anterior. */
    public static <T> T executarEm(String schema, Supplier<T> trecho) {
        String anterior = ATUAL.get();
        definir(schema);
        try {
            return trecho.get();
        } finally {
            definir(anterior);
        }
    }

    public static void executarEm(String schema, Runnable trecho) {
        executarEm(schema, () -> {
            trecho.run();
            return null;
        });
    }
}
