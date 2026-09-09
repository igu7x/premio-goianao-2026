package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.auth.MagistradoLookup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fecha o lookup de Magistrado da feature 001 contra o cadastro real (004/T-008):
 * quem consta como reconhecido em qualquer edicao recebe o papel MAGISTRADO.
 */
@Component
public class MagistradoLookupJpa implements MagistradoLookup {

    private final MagistradoRepository repositorio;

    public MagistradoLookupJpa(MagistradoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean ehMagistradoReconhecido(String cpf) {
        return cpf != null && repositorio.existsByCpf(cpf);
    }
}
