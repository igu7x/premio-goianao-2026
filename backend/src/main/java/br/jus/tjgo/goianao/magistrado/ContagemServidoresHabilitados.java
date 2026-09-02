package br.jus.tjgo.goianao.magistrado;

/**
 * Porta para enriquecer a visao de unidades reconhecidas com quantos servidores
 * ja estao habilitados (feature 008). Mantida aqui para que o pacote de
 * magistrados nao dependa do pacote de servidores.
 */
public interface ContagemServidoresHabilitados {

    long contar(Long edicaoId, Long unidadeId);
}
