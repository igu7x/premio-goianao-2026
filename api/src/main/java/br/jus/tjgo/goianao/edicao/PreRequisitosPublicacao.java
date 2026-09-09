package br.jus.tjgo.goianao.edicao;

import java.util.List;

/**
 * Porta consultada por {@code EdicaoService.publicar} (002/RF-3b, 009/T-001).
 * Mantem o pacote {@code edicao} independente do pacote {@code layout}: a
 * implementacao vive la e informa quais combinacoes selo x tipo faltam.
 */
public interface PreRequisitosPublicacao {

    /** Descricao das pendencias que impedem publicar; vazia quando esta tudo pronto. */
    List<String> pendencias(Long edicaoId);
}
