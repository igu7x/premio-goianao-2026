package br.jus.tjgo.goianao.unidade.dto;

import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;

/**
 * Unidade cadastrada, como sai para quem so precisa escolher uma.
 *
 * <p>Sem o responsavel de proposito: a listagem completa traz nome e e-mail de
 * quem responde por cada unidade, e por isso e so do superadministrador. Aqui
 * vai apenas o que um formulario precisa para oferecer a unidade na lista.
 */
public record UnidadeCadastrada(Long id, String nome, Long codigoSiedos, String comarca) {

    public static UnidadeCadastrada de(UnidadeJudiciaria unidade) {
        return new UnidadeCadastrada(unidade.getId(), unidade.getNome(),
                unidade.getCodigoSiedos(), unidade.getComarca());
    }
}
