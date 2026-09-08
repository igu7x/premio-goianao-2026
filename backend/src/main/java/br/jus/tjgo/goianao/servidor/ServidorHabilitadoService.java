package br.jus.tjgo.goianao.servidor;

import br.jus.tjgo.goianao.comum.Cpf;
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
     * Administrador edita qualquer unidade reconhecida; magistrado so as
     * <b>suas</b> unidades e so enquanto a edicao e a <b>vigente</b> — principio
     * do menor privilegio, com o admin cobrindo o resto.
     */
    public boolean podeEditar(Edicao edicao, Long unidadeId) {
        UsuarioAutenticado usuario = UsuarioAtual.obrigatorio();
        if (usuario.ehAdministrador()) {
            return true;
        }
        if (!edicao.isVigente()) {
            return false;
        }
        return magistrados.reconhecimentosDe(edicao.getId(), usuario.cpf()).stream()
                .anyMatch(r -> r.getUnidade().getId().equals(unidadeId));
    }

    private void exigirPermissao(Edicao edicao, Long unidadeId) {
        if (!podeEditar(edicao, unidadeId)) {
            throw new AcessoNegadoException(
                    "Você só pode editar a lista das unidades pelas quais foi reconhecido,"
                            + " e apenas na edição vigente.");
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

        String autor = UsuarioAtual.cpfOuSistema();
        List<ServidorEgesp> doEgesp = egesp.listarServidoresPorUnidade(unidade.getNome());

        int incluidos = 0;
        int jaExistentes = 0;
        int preservadosRemovidos = 0;

        for (ServidorEgesp servidor : doEgesp) {
            String cpf = Cpf.normalizar(servidor.cpf());
            if (cpf == null || cpf.isBlank()) {
                continue;
            }
            Optional<ServidorHabilitado> existente =
                    repositorio.findByEdicaoIdAndUnidadeIdAndCpf(edicaoId, unidadeId, cpf);

            if (existente.isEmpty()) {
                repositorio.save(new ServidorHabilitado(edicao, unidade, cpf,
                        Texto.aparar(servidor.nome()), OrigemServidor.EGESP, autor));
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
                (int) repositorio.countByEdicaoIdAndUnidadeIdAndAtivoTrue(edicaoId, unidadeId));
    }

    @Transactional
    public ServidorHabilitado incluir(Long edicaoId, Long unidadeId, String cpfBruto, String nome) {
        Edicao edicao = edicoes.buscar(edicaoId);
        UnidadeJudiciaria unidade = unidades.buscar(unidadeId);
        exigirUnidadeReconhecida(edicaoId, unidadeId);
        exigirPermissao(edicao, unidadeId);

        String cpf = Cpf.normalizar(cpfBruto);
        if (!Cpf.valido(cpf)) {
            throw new RegraDeNegocioException("CPF inválido.");
        }
        String nomeLimpo = Texto.aparar(nome);
        if (nomeLimpo == null) {
            throw new RegraDeNegocioException("Informe o nome do servidor.");
        }

        String autor = UsuarioAtual.cpfOuSistema();
        Optional<ServidorHabilitado> existente =
                repositorio.findByEdicaoIdAndUnidadeIdAndCpf(edicaoId, unidadeId, cpf);

        if (existente.isPresent()) {
            ServidorHabilitado servidor = existente.get();
            if (servidor.isAtivo()) {
                throw new ConflitoException(
                        "Este CPF já consta na lista desta unidade nesta edição.");
            }
            // Reativar e uma acao explicita do gestor, diferente da semeadura.
            servidor.reativar(nomeLimpo, OrigemServidor.MANUAL, autor);
            return servidor;
        }

        return repositorio.save(new ServidorHabilitado(edicao, unidade, cpf, nomeLimpo,
                OrigemServidor.MANUAL, autor));
    }

    @Transactional
    public void remover(Long edicaoId, Long unidadeId, String cpfBruto) {
        Edicao edicao = edicoes.buscar(edicaoId);
        exigirPermissao(edicao, unidadeId);

        String cpf = Cpf.normalizar(cpfBruto);
        ServidorHabilitado servidor = repositorio
                .findByEdicaoIdAndUnidadeIdAndCpf(edicaoId, unidadeId, cpf)
                .orElseThrow(() -> new br.jus.tjgo.goianao.comum.erro.NaoEncontradoException(
                        "Servidor não consta na lista desta unidade."));

        servidor.desativar(UsuarioAtual.cpfOuSistema());
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
    public boolean estaHabilitado(Long edicaoId, Long unidadeId, String cpf) {
        return repositorio.existsByEdicaoIdAndUnidadeIdAndCpfAndAtivoTrue(edicaoId, unidadeId, cpf);
    }

    @Transactional(readOnly = true)
    public List<Long> unidadesHabilitadas(Long edicaoId, String cpf) {
        return repositorio.unidadesHabilitadas(edicaoId, cpf);
    }

    @Transactional(readOnly = true)
    public List<Long> edicoesPublicadasHabilitadas(String cpf) {
        return repositorio.edicoesPublicadasHabilitadas(cpf);
    }

    @Transactional(readOnly = true)
    public Optional<String> nomeSalvo(Long edicaoId, Long unidadeId, String cpf) {
        return repositorio.nomeSalvo(edicaoId, unidadeId, cpf);
    }
}
