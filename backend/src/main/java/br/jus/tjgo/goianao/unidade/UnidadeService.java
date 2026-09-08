package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.UnidadeEgesp;
import br.jus.tjgo.goianao.unidade.dto.UnidadeEgespResposta;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnidadeService {

    private final UnidadeRepository repositorio;
    private final EgespClient egesp;

    public UnidadeService(UnidadeRepository repositorio, EgespClient egesp) {
        this.repositorio = repositorio;
        this.egesp = egesp;
    }

    /** Unidades do EGESP para o administrador escolher (004/RF-1). */
    @Transactional(readOnly = true)
    public List<UnidadeEgespResposta> listarDoEgesp(String filtro) {
        return egesp.listarUnidades(filtro).stream()
                .map(u -> {
                    Optional<UnidadeJudiciaria> local =
                            repositorio.findByNomeCanonico(Texto.canonicalizar(u.nome()));
                    return new UnidadeEgespResposta(
                            u.nome(),
                            u.comarca(),
                            local.map(UnidadeJudiciaria::getId).orElse(null),
                            local.isPresent());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnidadeJudiciaria> listarLocais() {
        return repositorio.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public UnidadeJudiciaria buscar(Long id) {
        return repositorio.findById(id).orElseThrow(
                () -> new NaoEncontradoException("Unidade " + id + " não encontrada."));
    }

    /**
     * Garante o espelho local de uma unidade do EGESP, criando-o na primeira vez.
     * O nome usado e sempre o **nome cru do EGESP**, nunca o texto digitado —
     * e o que mantem a chave de casamento intacta (004/RF-1).
     *
     * @throws RegraDeNegocioException se o nome nao existir no EGESP (o
     *         administrador nao pode cadastrar unidade por texto livre).
     */
    @Transactional
    public UnidadeJudiciaria garantirDoEgesp(String nomeInformado) {
        String canonico = Texto.canonicalizar(nomeInformado);
        if (canonico == null || canonico.isBlank()) {
            throw new RegraDeNegocioException("Informe a unidade judiciária.");
        }

        Optional<UnidadeJudiciaria> local = repositorio.findByNomeCanonico(canonico);
        if (local.isPresent()) {
            return local.get();
        }

        UnidadeEgesp doEgesp = egesp.listarUnidades(null).stream()
                .filter(u -> canonico.equals(Texto.canonicalizar(u.nome())))
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Unidade não encontrada no EGESP: \"" + nomeInformado + "\"."));

        return repositorio.save(new UnidadeJudiciaria(doEgesp.nome()));
    }
}
