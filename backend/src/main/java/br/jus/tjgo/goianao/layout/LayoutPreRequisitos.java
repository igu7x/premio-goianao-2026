package br.jus.tjgo.goianao.layout;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.PreRequisitosPublicacao;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Implementa a pre-condicao de publicacao (002/RF-3b): a edicao so publica com
 * as 8 combinacoes selo x tipo configuradas. E isso que garante que qualquer
 * reconhecimento incluido depois na vigente (feature 009) encontre layout.
 */
@Component
public class LayoutPreRequisitos implements PreRequisitosPublicacao {

    private final LayoutRepository repositorio;

    public LayoutPreRequisitos(LayoutRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<String> pendencias(Long edicaoId) {
        List<String> faltantes = new ArrayList<>();
        for (Selo selo : Selo.values()) {
            for (TipoCertificado tipo : TipoCertificado.values()) {
                if (!repositorio.existsByEdicaoIdAndSeloAndTipo(edicaoId, selo, tipo)) {
                    faltantes.add(selo.rotulo() + " / " + tipo.rotulo());
                }
            }
        }
        return faltantes;
    }
}
