package br.jus.tjgo.goianao.layout.dto;

import java.util.List;

/**
 * Panorama dos layouts de uma edicao: o que ja existe, o que falta (003/RF-7) e
 * se ainda e possivel alterar (so em rascunho — 003/RF-8).
 */
public record LayoutsDaEdicaoResposta(
        List<LayoutResposta> layouts,
        List<String> pendencias,
        boolean editavel,
        boolean fonteInstitucionalDisponivel) {}
