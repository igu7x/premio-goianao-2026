package br.jus.tjgo.goianao.servidor;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.AcessoNegadoException;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import br.jus.tjgo.goianao.servidor.dto.SemeaduraResposta;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServidorHabilitadoService {

    private final ServidorHabilitadoRepository repositorio;
    private final EdicaoService edicoes;
    private final UnidadeService unidades;
    private final MagistradoService magistrados;
    private final EgespClient egesp;

    public ServidorHabilitadoService(ServidorHabilitadoRepository repositorio,
                                     EdicaoService edicoes,
                                     UnidadeService unidades,
                                     MagistradoService magistrados,
                                     EgespClient egesp) {
        this.repositorio = repositorio;
        this.edicoes = edicoes;
        this.unidades = unidades;
        this.magistrados = magistrados;
        this.egesp = egesp;
    }

    // ------------------------------------------------------------------
    // Autorizacao de escopo (008/RF-3, RF-4, RNF-1)
    // ------------------------------------------------------------------

    /**
     * Administrador edita qualquer unidade reconhecida; magistrado edita as
     * <b>suas</b> e so enquanto a edicao e a <b>vigente</b> — principio do menor
     * privilegio, com o admin cobrindo o resto.
     *
     * <p>Uma unidade e "dele" por dois caminhos: por ter sido <b>reconhecido</b>
     * nela naquela edicao, ou por ter sido <b>designado responsavel</b> pela
     * unidade no cadastro do superadministrador. O segundo existe porque
     * responder pela unidade e coisa diferente de ter vencido o premio nela — o
     * juiz de uma vara que nao ganhou nada continua sendo quem sabe quem
     * trabalha ali.
     *
     * <p>A restricao a edicao vigente vale para os dois caminhos: edicao passada
     * e congelada, independentemente de como o escopo foi obtido.
     */
    public boolean podeEditar(Edicao edicao, Long unidadeId) {
        UsuarioAutenticado usuario = UsuarioAtual.obrigatorio();
        if (usuario.ehAdministrador()) {
            return true;
        }
        if (!edicao.isVigente()) {
            return false;
        }
        boolean reconhecido = magistrados.reconhecimentosDe(edicao.getId(), usuario.email())
                .stream()
                .anyMatch(r -> r.getUnidade().getId().equals(unidadeId));

        return reconhecido || unidades.ehResponsavel(unidadeId, usuario.email());
    }

    private void exigirPermissao(Edicao edicao, Long unidadeId) {
        if (!podeEditar(edicao, unidadeId)) {
            throw new AcessoNegadoException(
                    "Você só pode editar a lista das unidades pelas quais foi reconhecido ou "
                            + "pelas quais responde, e apenas na edição vigente.");
        }
    }

    private void exigirUnidadeReconhecida(Long edicaoId, Long unidadeId) {
        if (!magistrados.unidadeEhReconhecida(edicaoId, unidadeId)) {
            throw new RegraDeNegocioException(
                    "A unidade não foi reconhecida nesta edição, então não há lista a gerenciar.");
        }
    }

    // ------------------------------------------------------------------
    // Operacoes
    // ------------------------------------------------------------------

    /**
     * Busca a lotacao no EGESP e <b>mescla</b> com a lista atual (008/RF-1).
     * Nunca remove inclusoes manuais e nunca reativa quem foi removido: o ajuste
     * humano prevalece sobre o RH.
     */
    @Transactional
    public SemeaduraResposta semear(Long edicaoId, Long unidadeId) {
        Edicao edicao = edicoes.buscar(edicaoId);
        UnidadeJudiciaria unidade = unidades.buscar(unidadeId);
        exigirUnidadeReconhecida(edicaoId, unidadeId);
        exigirPermissao(edicao, unidadeId);

        return mesclar(edicao, unidade, egesp.listarServidoresPorUnidade(unidade.getNome()));
    }

    /**
     * Mesma mesclagem, com a lotacao ja em maos.
     *
     * <p>Existe para a importacao da unidade (010), que resolve o e-mail de cada
     * matricula antes de comecar: sem isto, buscar a lotacao de novo dobraria as
     * chamadas a API corporativa para obter exatamente a mesma lista.
     */
    @Transactional
    public SemeaduraResposta semearCom(Long edicaoId, Long unidadeId,
                                       List<ServidorEgesp> doEgesp) {
        Edicao edicao = edicoes.buscar(edicaoId);
        UnidadeJudiciaria unidade = unidades.buscar(unidadeId);
        exigirUnidadeReconhecida(edicaoId, unidadeId);
        exigirPermissao(edicao, unidadeId);
        return mesclar(edicao, unidade, doEgesp);
    }

    private SemeaduraResposta mesclar(Edicao edicao, UnidadeJudiciaria unidade,
                                      List<ServidorEgesp> doEgesp) {
        Long edicaoId = edicao.getId();
        Long unidadeId = unidade.getId();
        String autor = UsuarioAtual.emailOuSistema();

        int incluidos = 0;
        int jaExistentes = 0;
        int preservadosRemovidos = 0;
        int semEmail = 0;

        for (ServidorEgesp servidor : doEgesp) {
            if (!Email.valido(servidor.email())) {
                // Sem e-mail a pessoa nao seria reconhecida no login (DI-24).
                // Conta, em vez de sumir: e o sinal de que o EGESP esta
                // devolvendo cadastro incompleto.
                semEmail++;
                continue;
            }
            String email = Email.normalizar(servidor.email());
            String cpf = Cpf.valido(servidor.cpf()) ? Cpf.normalizar(servidor.cpf()) : null;
            Optional<ServidorHabilitado> existente =
                    repositorio.findByEdicaoIdAndUnidadeIdAndEmail(edicaoId, unidadeId, email);

            if (existente.isEmpty()) {
                ServidorHabilitado novo = repositorio.save(new ServidorHabilitado(edicao, unidade,
                        email, Texto.aparar(servidor.nome()), cpf, OrigemServidor.EGESP, autor));
                // De onde a linha veio no RH: e por ela que a tela de
                // sincronizacao reencontra a pessoa quando o nome muda (010).
                novo.definirMatricula(servidor.matricula());
                incluidos++;
            } else if (existente.get().isAtivo()) {
                jaExistentes++;
            } else {
                preservadosRemovidos++;
            }
        }

        return new SemeaduraResposta(
                doEgesp.size(),
                incluidos,
                jaExistentes,
                preservadosRemovidos,
                semEmail,
                (int) repositorio.countByEdicaoIdAndUnidadeIdAndAtivoTrue(edicaoId, unidadeId));
    }

    /**
     * Inclusao feita a mao pelo administrador ou pelo magistrado responsavel.
     * Origem MANUAL: e o ajuste humano que a semeadura nunca desfaz (008).
     */
    @Transactional
    public ServidorHabilitado incluir(Long edicaoId, Long unidadeId, String emailBruto,
                                      String nome, String cpfBruto) {
        return incluir(edicaoId, unidadeId, emailBruto, nome, cpfBruto, OrigemServidor.MANUAL);
    }

    /**
     * Mesma inclusao, com a origem declarada. A tela de sincronizacao (010) usa
     * {@code EGESP}: o que veio do RH precisa continuar reconhecivel como tal,
     * senao uma pessoa trazida pela sincronizacao passaria a ser tratada como
     * ajuste manual e ficaria protegida de correcoes futuras.
     */
    @Transactional
    public ServidorHabilitado incluir(Long edicaoId, Long unidadeId, String emailBruto,
                                      String nome, String cpfBruto, OrigemServidor origem) {
        Edicao edicao = edicoes.buscar(edicaoId);
        UnidadeJudiciaria unidade = unidades.buscar(unidadeId);
        exigirUnidadeReconhecida(edicaoId, unidadeId);
        exigirPermissao(edicao, unidadeId);

        String email = Email.exigir(emailBruto);
        String cpf = Cpf.opcional(cpfBruto);
        String nomeLimpo = Texto.aparar(nome);
        if (nomeLimpo == null) {
            throw new RegraDeNegocioException("Informe o nome do servidor.");
        }

        String autor = UsuarioAtual.emailOuSistema();
        Optional<ServidorHabilitado> existente =
                repositorio.findByEdicaoIdAndUnidadeIdAndEmail(edicaoId, unidadeId, email);

        if (existente.isPresent()) {
            ServidorHabilitado servidor = existente.get();
            if (servidor.isAtivo()) {
                throw new ConflitoException(
                        "Este e-mail já consta na lista desta unidade nesta edição.");
            }
            // Reativar e uma acao explicita do gestor, diferente da semeadura.
            servidor.reativar(nomeLimpo, cpf, origem, autor);
            return servidor;
        }

        return repositorio.save(new ServidorHabilitado(edicao, unidade, email, nomeLimpo, cpf,
                origem, autor));
    }

    /** Remocao logica, pelo id do item: dado pessoal nao vai na URL. */
    @Transactional
    public void remover(Long edicaoId, Long unidadeId, Long servidorId) {
        Edicao edicao = edicoes.buscar(edicaoId);
        exigirPermissao(edicao, unidadeId);

        ServidorHabilitado servidor = repositorio
                .findByIdAndEdicaoIdAndUnidadeId(servidorId, edicaoId, unidadeId)
                .orElseThrow(() -> new br.jus.tjgo.goianao.comum.erro.NaoEncontradoException(
                        "Servidor não consta na lista desta unidade."));

        servidor.desativar(UsuarioAtual.emailOuSistema());
    }

    @Transactional(readOnly = true)
    public List<ServidorHabilitado> listar(Long edicaoId, Long unidadeId) {
        edicoes.buscar(edicaoId);
        unidades.buscar(unidadeId);
        return repositorio.findByEdicaoIdAndUnidadeIdOrderByNomeAsc(edicaoId, unidadeId);
    }

    // ------------------------------------------------------------------
    // Consultas usadas pela emissao (006)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean estaHabilitado(Long edicaoId, Long unidadeId, String email) {
        return repositorio.existsByEdicaoIdAndUnidadeIdAndEmailAndAtivoTrue(
                edicaoId, unidadeId, email);
    }

    @Transactional(readOnly = true)
    public List<Long> unidadesHabilitadas(Long edicaoId, String email) {
        return repositorio.unidadesHabilitadas(edicaoId, email);
    }

    @Transactional(readOnly = true)
    public List<Long> edicoesPublicadasHabilitadas(String email) {
        return repositorio.edicoesPublicadasHabilitadas(email);
    }

    @Transactional(readOnly = true)
    public Optional<String> nomeSalvo(Long edicaoId, Long unidadeId, String email) {
        return repositorio.nomeSalvo(edicaoId, unidadeId, email);
    }
}
