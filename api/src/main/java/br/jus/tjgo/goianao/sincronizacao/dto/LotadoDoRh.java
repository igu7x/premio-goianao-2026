package br.jus.tjgo.goianao.sincronizacao.dto;

/**
 * Uma pessoa que o RH aponta como lotada na unidade, cruzada com o cadastro de
 * usuarios — sem edicao nenhuma no meio.
 *
 * <p>E diferente de {@code ServidorComparado}: aquele responde "esta pessoa pode
 * emitir nesta edicao?", e este responde "esta pessoa existe no sistema e esta
 * lotada onde deveria?". Sao duas perguntas, e misturar as duas foi o que fez a
 * tela de sincronizacao exigir uma edicao para tudo.
 *
 * @param semEmail     nem o RH nem o AD tem e-mail corporativo: sem ele a pessoa
 *                     nao seria reconhecida no login, entao nao da para cadastrar
 * @param jaCadastrada ja existe usuario com este e-mail
 * @param lotacaoCerta o usuario existe e ja aponta para esta unidade
 */
public record LotadoDoRh(
        Long matricula,
        String nome,
        String email,
        boolean semEmail,
        boolean jaCadastrada,
        boolean lotacaoCerta) {}
