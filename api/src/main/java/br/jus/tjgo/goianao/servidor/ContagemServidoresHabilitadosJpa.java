package br.jus.tjgo.goianao.servidor;

import br.jus.tjgo.goianao.magistrado.ContagemServidoresHabilitados;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Quantos servidores ativos ha na lista de uma unidade (visao do administrador). */
@Component
public class ContagemServidoresHabilitadosJpa implements ContagemServidoresHabilitados {

    private final ServidorHabilitadoRepository repositorio;

    public ContagemServidoresHabilitadosJpa(ServidorHabilitadoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public long contar(Long edicaoId, Long unidadeId) {
        return repositorio.countByEdicaoIdAndUnidadeIdAndAtivoTrue(edicaoId, unidadeId);
    }
}
