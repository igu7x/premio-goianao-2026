package br.jus.tjgo.goianao.comum.erro;

import java.util.List;

/**
 * Estado incompativel com a operacao (duplicidade, edicao nao elegivel,
 * pre-requisito ausente) -> HTTP 409. Pode carregar detalhes item a item, como
 * a lista de layouts pendentes ao publicar uma edicao (002/RF-3b).
 */
public class ConflitoException extends RuntimeException {

    private final transient List<String> detalhes;

    public ConflitoException(String mensagem) {
        this(mensagem, List.of());
    }

    public ConflitoException(String mensagem, List<String> detalhes) {
        super(mensagem);
        this.detalhes = detalhes == null ? List.of() : List.copyOf(detalhes);
    }

    public List<String> detalhes() {
        return detalhes;
    }
}
