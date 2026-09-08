package br.jus.tjgo.goianao.painel;

import br.jus.tjgo.goianao.edicao.StatusEdicao;
import java.util.List;

/** Numeros de uma edicao, para a tela inicial do administrador. */
public record ResumoEdicaoResposta(
        Long edicaoId,
        Integer ano,
        StatusEdicao status,
        boolean vigente,
        long layoutsConfigurados,
        List<String> layoutsPendentes,
        long magistradosReconhecidos,
        long unidadesReconhecidas,
        long servidoresHabilitados,
        long certificadosEmitidos) {}
