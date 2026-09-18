package br.jus.tjgo.goianao.edicao;

import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.edicao.base.BaseDaEdicao;
import br.jus.tjgo.goianao.edicao.base.EdicaoCorrente;
import br.jus.tjgo.goianao.edicao.base.NomeDeSchema;
import br.jus.tjgo.goianao.edicao.dto.AtualizarEdicaoRequisicao;
import br.jus.tjgo.goianao.edicao.dto.CriarEdicaoRequisicao;
import br.jus.tjgo.goianao.usuario.SuperadminsDaEdicao;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EdicaoService {

    private final EdicaoRepository repositorio;
    private final PreRequisitosPublicacao preRequisitos;
    private final BaseDaEdicao bases;
    private final SuperadminsDaEdicao superadmins;

    public EdicaoService(EdicaoRepository repositorio, PreRequisitosPublicacao preRequisitos,
                         BaseDaEdicao bases, SuperadminsDaEdicao superadmins) {
        this.repositorio = repositorio;
        this.preRequisitos = preRequisitos;
        this.bases = bases;
        this.superadmins = superadmins;
    }

    /**
     * Cria a edicao <b>e a base dela</b> (011/RF-1, RF-2).
     *
     * <p>Nao e transacional, e nao poderia ser: criar o schema e aplicar nele o
     * changelog sao comandos de estrutura, fora do alcance de um rollback. A
     * ordem e escolhida por causa disso — a base vem antes da linha no catalogo,
     * de modo que uma falha no meio deixe no maximo um schema vazio, que a
     * proxima tentativa reaproveita, e nunca uma edicao apontando para uma base
     * que nao existe.
     *
     * <p>A base nasce vazia: nada da edicao anterior e copiado. A unica coisa que
     * atravessa sao os superadministradores, sem os quais a edicao nova nao teria
     * quem a administrasse (011/RF-3).
     */
    public Edicao criar(CriarEdicaoRequisicao requisicao) {
        if (repositorio.existsByAno(requisicao.ano())) {
            throw new ConflitoException("Já existe uma edição para o ano " + requisicao.ano() + ".");
        }

        String schema = NomeDeSchema.paraAno(requisicao.ano());
        bases.prepararSchema(schema);

        // Toda edicao nasce em RASCUNHO (002/CA-1).
        Edicao nova = repositorio.save(
                new Edicao(requisicao.ano(), Texto.aparar(requisicao.descricao()), schema));

        superadmins.semear(EdicaoCorrente.schema(), schema);
        return nova;
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
     *
     * <p>Nao e transacional: os layouts sao conferidos na base <b>da edicao que
     * se publica</b>, que pode nao ser a da sessao — a tela de edicoes age sobre
     * todas (011). Uma transacao aberta aqui prenderia a conferencia a base da
     * sessao.
     */
    public Edicao publicar(Long id) {
        Edicao edicao = buscar(id);
        if (edicao.estaPublicada()) {
            throw new ConflitoException("A edição " + edicao.getAno() + " já está publicada.");
        }

        List<String> pendencias = pendenciasParaPublicar(id);
        if (!pendencias.isEmpty()) {
            throw new ConflitoException(
                    "A edição não pode ser publicada: faltam layouts de certificado.", pendencias);
        }

        edicao.publicar();
        return repositorio.save(edicao);
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

        // A vigente e a base padrao do sistema: e nela que caem a verificacao
        // publica, a tela de login e tudo o que chega sem sessao (011).
        EdicaoCorrente.definirPadrao(edicao.getSchemaDados());
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

    /**
     * Pendencias de layout de uma edicao, para o frontend antecipar o bloqueio.
     *
     * <p>Conferidas na base da propria edicao, e por isso fora de transacao:
     * quem abre a transacao e a consulta, ja dentro do schema certo.
     */
    public List<String> pendenciasParaPublicar(Long id) {
        Edicao edicao = buscar(id);
        return EdicaoCorrente.executarEm(edicao.getSchemaDados(),
                () -> preRequisitos.pendencias(id));
    }
}
