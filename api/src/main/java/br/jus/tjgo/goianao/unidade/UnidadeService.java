package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.integracao.egesp.UnidadeEgesp;
import br.jus.tjgo.goianao.unidade.dto.UnidadeEgespResposta;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnidadeService {

    private final UnidadeRepository repositorio;
    private final EgespClient egesp;
    private final UsuarioRepository usuarios;

    public UnidadeService(UnidadeRepository repositorio, EgespClient egesp,
                          UsuarioRepository usuarios) {
        this.repositorio = repositorio;
        this.egesp = egesp;
        this.usuarios = usuarios;
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
        return repositorio.findWithResponsavelById(id).orElseThrow(
                () -> new NaoEncontradoException("Unidade " + id + " não encontrada."));
    }

    // ------------------------------------------------------------------
    // Superior responsavel pela unidade
    // ------------------------------------------------------------------

    /**
     * Designa quem responde pela unidade.
     *
     * <p>E o que passa a dar ao magistrado o direito de gerenciar a lista de
     * servidores habilitados dali. Antes disso, o unico caminho era ter sido
     * <b>reconhecido</b> na unidade — o que amarra duas coisas diferentes: ter
     * vencido o premio e responder pela unidade.
     *
     * <p>Exige o papel MAGISTRADO porque a tela que a designacao destrava e a do
     * magistrado; designar quem nao o tem criaria um responsavel sem lugar
     * algum para exercer a responsabilidade.
     */
    @Transactional
    public UnidadeJudiciaria designarResponsavel(Long unidadeId, Long usuarioId) {
        UnidadeJudiciaria unidade = buscar(unidadeId);
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(
                () -> new NaoEncontradoException("Usuário não encontrado."));

        if (!usuario.isAtivo()) {
            throw new RegraDeNegocioException(
                    "Usuário desativado não pode responder por uma unidade. Reative-o antes.");
        }
        if (!usuario.getPapeis().contains(Papel.MAGISTRADO)) {
            throw new RegraDeNegocioException(
                    "Só quem tem o papel de magistrado pode responder por uma unidade — é a tela "
                    + "dele que a designação libera. Ajuste os papéis em Usuários do sistema.");
        }

        unidade.designarResponsavel(usuario);
        return unidade;
    }

    /** A unidade tem alguem designado? E o que abre a lista de habilitados dela. */
    @Transactional(readOnly = true)
    public boolean temResponsavel(Long unidadeId) {
        return buscar(unidadeId).getResponsavel() != null;
    }

    /** Escopo: a pessoa deste e-mail responde por esta unidade? */
    @Transactional(readOnly = true)
    public boolean ehResponsavel(Long unidadeId, String email) {
        return repositorio.existsByIdAndResponsavelEmail(unidadeId, email);
    }

    @Transactional(readOnly = true)
    public List<UnidadeJudiciaria> unidadesSobResponsabilidade(String email) {
        return repositorio.findByResponsavelEmailOrderByNomeAsc(email);
    }

    @Transactional
    public UnidadeJudiciaria removerResponsavel(Long unidadeId) {
        UnidadeJudiciaria unidade = buscar(unidadeId);
        unidade.designarResponsavel(null);
        return unidade;
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

        // O codigo vem na mesma resposta, e sem ele a unidade fica fora de tudo
        // o que e por codigo: a pagina dela, a planilha de responsaveis e a
        // sincronizacao. E se o codigo ja esta gravado em outra unidade — a
        // mesma, renomeada no RH —, e ela que vale, e nao uma segunda copia.
        if (doEgesp.codigo() != null) {
            Optional<UnidadeJudiciaria> peloCodigo = repositorio.findByCodigoSiedos(doEgesp.codigo());
            if (peloCodigo.isPresent()) {
                return peloCodigo.get();
            }
        }

        UnidadeJudiciaria nova = new UnidadeJudiciaria(doEgesp.nome());
        if (doEgesp.codigo() != null) {
            nova.vincularAoSiedos(doEgesp.codigo(), doEgesp.comarca());
        }
        return repositorio.save(nova);
    }
}
