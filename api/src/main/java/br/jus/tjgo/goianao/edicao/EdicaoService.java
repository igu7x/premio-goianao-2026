package br.jus.tjgo.goianao.edicao;

import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.edicao.dto.AtualizarEdicaoRequisicao;
import br.jus.tjgo.goianao.edicao.dto.CriarEdicaoRequisicao;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EdicaoService {

    private final EdicaoRepository repositorio;
    private final PreRequisitosPublicacao preRequisitos;

    public EdicaoService(EdicaoRepository repositorio, PreRequisitosPublicacao preRequisitos) {
        this.repositorio = repositorio;
        this.preRequisitos = preRequisitos;
    }

    @Transactional
    public Edicao criar(CriarEdicaoRequisicao requisicao) {
        if (repositorio.existsByAno(requisicao.ano())) {
            throw new ConflitoException("Já existe uma edição para o ano " + requisicao.ano() + ".");
        }
        // Toda edicao nasce em RASCUNHO (002/CA-1).
        return repositorio.save(new Edicao(requisicao.ano(), Texto.aparar(requisicao.descricao())));
    }

    @Transactional
    public Edicao atualizar(Long id, AtualizarEdicaoRequisicao requisicao) {
        Edicao edicao = buscar(id);
        edicao.alterarDescricao(Texto.aparar(requisicao.descricao()));
        return edicao;
    }

    /**
     * RASCUNHO -> PUBLICADA. So procede com os 8 layouts (4 selos x 2 tipos)
     * configurados: e o que garante que qualquer selo incluido depois na vigente
     * (feature 009) tenha layout para emitir (002/RF-3b).
     */
    @Transactional
    public Edicao publicar(Long id) {
        Edicao edicao = buscar(id);
        if (edicao.estaPublicada()) {
            throw new ConflitoException("A edição " + edicao.getAno() + " já está publicada.");
        }

        List<String> pendencias = preRequisitos.pendencias(id);
        if (!pendencias.isEmpty()) {
            throw new ConflitoException(
                    "A edição não pode ser publicada: faltam layouts de certificado.", pendencias);
        }

        edicao.publicar();
        return edicao;
    }

    /**
     * Marca a edicao como vigente (padrao). A anterior deixa de ser vigente mas
     * **permanece publicada e emitivel** (002/RF-5, CA-3).
     */
    @Transactional
    public Edicao tornarVigente(Long id) {
        Edicao edicao = buscar(id);
        if (!edicao.estaPublicada()) {
            throw new ConflitoException(
                    "Somente uma edição publicada pode ser definida como vigente.");
        }
        if (edicao.isVigente()) {
            return edicao;
        }

        // Desmarcar pela entidade (e nao por um UPDATE em massa) mantem o
        // contexto de persistencia coerente: quem ja tinha a edicao anterior
        // carregada enxerga o novo estado. O flush intermediario evita esbarrar
        // no indice unico durante a troca.
        repositorio.findByVigenteTrue().ifPresent(anterior -> anterior.definirVigencia(false));
        repositorio.flush();

        edicao.definirVigencia(true);
        return edicao;
    }

    @Transactional(readOnly = true)
    public List<Edicao> listar() {
        return repositorio.findAllByOrderByAnoDesc();
    }

    @Transactional(readOnly = true)
    public List<Edicao> listarPublicadas() {
        return repositorio.findByStatusOrderByAnoDesc(StatusEdicao.PUBLICADA);
    }

    @Transactional(readOnly = true)
    public Optional<Edicao> vigente() {
        return repositorio.findByVigenteTrue();
    }

    @Transactional(readOnly = true)
    public Edicao buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Edição " + id + " não encontrada."));
    }

    /**
     * Resolve a edicao alvo de uma emissao: a informada ou, na ausencia, a
     * vigente (constituicao, principio 8).
     */
    @Transactional(readOnly = true)
    public Edicao resolverAlvo(Long edicaoId) {
        if (edicaoId != null) {
            return buscar(edicaoId);
        }
        return vigente().orElseThrow(() -> new NaoEncontradoException(
                "Nenhuma edição vigente definida. Selecione uma edição."));
    }

    /** Pendencias de layout de uma edicao, para o frontend antecipar o bloqueio. */
    @Transactional(readOnly = true)
    public List<String> pendenciasParaPublicar(Long id) {
        buscar(id);
        return preRequisitos.pendencias(id);
    }
}
