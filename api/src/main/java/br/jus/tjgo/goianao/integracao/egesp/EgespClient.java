package br.jus.tjgo.goianao.integracao.egesp;

import java.util.List;
import java.util.Optional;

/**
 * Porta de acesso ao RH do tribunal (EGESP/SIEDOS, hoje exposto pela API
 * ConnectTJ). Isolada atras de interface para que a troca do mock pela
 * integracao real nao afete o dominio (constituicao, principio 7).
 *
 * <p><b>Nunca e chamada na emissao.</b> Serve para listar unidades (004/RF-1),
 * semear a lista de servidores habilitados (008/RF-1), alimentar a tela de
 * sincronizacao (010) e atualizar o cadastro de quem acabou de entrar. A
 * emissao le a lista ja persistida (006/RNF-2), o que mantem a reemissao de
 * edicoes antigas fiel ao que valia naquela epoca.
 */
public interface EgespClient {

    /** Unidades judiciarias do TJGO; {@code filtro} opcional por trecho do nome. */
    List<UnidadeEgesp> listarUnidades(String filtro);

    /** Servidores lotados na unidade, identificada pelo nome vindo do RH. */
    List<ServidorEgesp> listarServidoresPorUnidade(String nomeUnidade);

    /** A unidade e toda a sua arvore de subordinadas. */
    List<UnidadeEgesp> hierarquia(long codigoUnidade);

    /**
     * <b>Todo</b> o organograma do tribunal, numa consulta so.
     *
     * <p>E o que permite ver as unidades judiciarias: elas nao penduram na
     * Presidencia — cada vara fica sob a sua comarca —, entao varrer a partir de
     * um codigo raiz deixaria a maior parte de fora. Sao mais de duas mil
     * unidades em oito niveis.
     */
    List<UnidadeEgesp> organogramaCompleto();

    Optional<UnidadeEgesp> unidadePorCodigo(long codigoUnidade);

    /** Responsavel que o RH registra para a unidade; entra na tela como sugestao. */
    default Optional<ResponsavelEgesp> responsavelDaUnidade(long codigoUnidade) {
        return Optional.empty();
    }

    /** Lotados diretos da unidade, ja com todas as paginas percorridas. */
    List<LotadoEgesp> lotados(long codigoUnidade);

    Optional<ServidorEgesp> servidorPorMatricula(long matricula);

    /**
     * Busca pessoas por trecho do nome ou pela matricula, para escolha em tela.
     * Digitar e-mail a mao e o erro mais caro do sistema: ele so aparece quando
     * a pessoa tenta emitir e nao acha nada seu (DI-24).
     */
    default List<ServidorEgesp> procurarPessoas(String termo) {
        return List.of();
    }

    /** {@code loginAd} e o prefixo do e-mail corporativo. */
    Optional<ServidorEgesp> servidorPorLogin(String loginAd);

    /** Falso quando a integracao real nao esta configurada e vale o mock. */
    default boolean integracaoReal() {
        return false;
    }
}
