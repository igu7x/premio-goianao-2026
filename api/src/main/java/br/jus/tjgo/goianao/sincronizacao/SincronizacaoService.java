package br.jus.tjgo.goianao.sincronizacao;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.LotadoEgesp;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import br.jus.tjgo.goianao.integracao.egesp.UnidadeEgesp;
import br.jus.tjgo.goianao.servidor.OrigemServidor;
import br.jus.tjgo.goianao.servidor.ServidorHabilitado;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoRepository;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoService;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.servidor.dto.SemeaduraResposta;
import br.jus.tjgo.goianao.sincronizacao.dto.ComparacaoServidores;
import br.jus.tjgo.goianao.sincronizacao.dto.ImportacaoDaUnidade;
import br.jus.tjgo.goianao.sincronizacao.dto.ItemSincronizacao;
import br.jus.tjgo.goianao.sincronizacao.dto.ServidorComparado;
import br.jus.tjgo.goianao.sincronizacao.dto.SituacaoIntegracao;
import br.jus.tjgo.goianao.sincronizacao.dto.UnidadeComparada;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeRepository;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compara o que o RH diz com o que esta no banco e, sob comando, aplica item a
 * item (feature 010).
 *
 * <p><b>Comparar nao grava nada.</b> A separacao existe porque o que esta em
 * jogo e o nome impresso em certificado e quem tem direito de emitir: carga
 * automatica erra em silencio, e aqui o erro so acontece depois de alguem ler e
 * clicar.
 */
@Service
public class SincronizacaoService {

    private final EgespClient egesp;
    private final UnidadeRepository unidades;
    private final ServidorHabilitadoRepository habilitados;
    private final ServidorHabilitadoService servicoDeHabilitados;
    private final EdicaoService edicoes;
    private final UsuarioRepository usuarios;

    public SincronizacaoService(EgespClient egesp, UnidadeRepository unidades,
                                ServidorHabilitadoRepository habilitados,
                                ServidorHabilitadoService servicoDeHabilitados,
                                EdicaoService edicoes,
                                UsuarioRepository usuarios) {
        this.egesp = egesp;
        this.unidades = unidades;
        this.habilitados = habilitados;
        this.servicoDeHabilitados = servicoDeHabilitados;
        this.edicoes = edicoes;
        this.usuarios = usuarios;
    }

    public SituacaoIntegracao situacao() {
        return SituacaoIntegracao.de(egesp.integracaoReal());
    }

    // ------------------------------------------------------------------
    // Comparacao (somente leitura)
    // ------------------------------------------------------------------

    /**
     * A arvore de unidades a partir de um codigo, cruzada com o cadastro local.
     *
     * <p>Inclui as unidades locais que <b>tem codigo</b> e sumiram da arvore:
     * some da API o que foi extinto ou remanejado, e isso precisa ficar visivel.
     * Unidades locais sem codigo ficam de fora — sao as que nunca foram casadas,
     * e apareceriam todas como orfas, virando ruido.
     */
    @Transactional(readOnly = true)
    public List<UnidadeComparada> compararUnidades(long codigoRaiz) {
        List<UnidadeEgesp> daApi = egesp.hierarquia(codigoRaiz);
        List<UnidadeComparada> resultado = new ArrayList<>();
        Set<Long> vistos = new LinkedHashSet<>();

        for (UnidadeEgesp api : daApi) {
            if (api.codigo() == null) {
                continue;
            }
            vistos.add(api.codigo());
            Optional<UnidadeJudiciaria> local = localDe(api);

            if (local.isEmpty()) {
                resultado.add(new UnidadeComparada(ItemSincronizacao.SO_NA_API, api.codigo(),
                        null, null, api.nome(), api.comarca()));
                continue;
            }

            UnidadeJudiciaria unidade = local.get();
            boolean mesmoNome = unidade.getNome().equals(api.nome());
            boolean mesmaComarca = api.comarca() == null
                    || api.comarca().equals(unidade.getComarca());
            boolean casada = api.codigo().equals(unidade.getCodigoSiedos());

            resultado.add(new UnidadeComparada(
                    mesmoNome && mesmaComarca && casada
                            ? ItemSincronizacao.SINCRONIZADO
                            : ItemSincronizacao.DESATUALIZADO,
                    api.codigo(), unidade.getId(), unidade.getNome(), api.nome(),
                    api.comarca()));
        }

        unidades.findAllByOrderByNomeAsc().stream()
                .filter(u -> u.getCodigoSiedos() != null && !vistos.contains(u.getCodigoSiedos()))
                .forEach(u -> resultado.add(new UnidadeComparada(ItemSincronizacao.ORFAO,
                        u.getCodigoSiedos(), u.getId(), u.getNome(), null, u.getComarca())));

        return resultado;
    }

    /**
     * A lista de habilitados de uma edicao x unidade, cruzada com a lotacao do
     * RH. O e-mail de cada lotado e resolvido pela matricula — a listagem de
     * lotados nao o traz, e sem e-mail ninguem e reconhecido no login (DI-24).
     */
    @Transactional(readOnly = true)
    public ComparacaoServidores compararServidores(Long unidadeId, Long edicaoId) {
        UnidadeJudiciaria unidade = unidadeComCodigo(unidadeId);
        edicoes.buscar(edicaoId);

        Map<Long, ServidorEgesp> porMatricula = new HashMap<>();
        Map<String, ServidorEgesp> porEmail = new HashMap<>();
        for (LotadoEgesp lotado : egesp.lotados(unidade.getCodigoSiedos())) {
            ServidorEgesp servidor = egesp.servidorPorMatricula(lotado.matricula())
                    .orElseGet(() -> new ServidorEgesp(null, lotado.nome(), null,
                            lotado.matricula()));
            porMatricula.put(lotado.matricula(), servidor);
            if (Email.valido(servidor.email())) {
                porEmail.put(Email.normalizar(servidor.email()), servidor);
            }
        }

        List<ServidorComparado> itens = new ArrayList<>();
        Set<Long> matriculasJaNaLista = new LinkedHashSet<>();

        for (ServidorHabilitado linha : habilitados
                .findByEdicaoIdAndUnidadeIdOrderByNomeAsc(edicaoId, unidadeId)) {
            ServidorEgesp naApi = porEmail.get(Email.normalizar(linha.getEmail()));
            if (naApi == null && linha.getMatricula() != null) {
                naApi = porMatricula.get(linha.getMatricula());
            }
            if (naApi != null && naApi.matricula() != null) {
                matriculasJaNaLista.add(naApi.matricula());
            }

            ItemSincronizacao situacao;
            if (naApi == null) {
                situacao = linha.isAtivo() ? ItemSincronizacao.ORFAO
                        : ItemSincronizacao.SINCRONIZADO;
            } else if (!Texto.aparar(linha.getNome()).equals(Texto.aparar(naApi.nome()))
                    || linha.getMatricula() == null) {
                situacao = ItemSincronizacao.DESATUALIZADO;
            } else {
                situacao = ItemSincronizacao.SINCRONIZADO;
            }

            itens.add(new ServidorComparado(situacao,
                    linha.getMatricula() != null ? linha.getMatricula()
                            : naApi == null ? null : naApi.matricula(),
                    naApi != null ? naApi.nome() : linha.getNome(),
                    linha.getEmail(), linha.getId(), linha.getOrigem(), false));
        }

        porMatricula.forEach((matricula, servidor) -> {
            if (matriculasJaNaLista.contains(matricula)) {
                return;
            }
            itens.add(new ServidorComparado(ItemSincronizacao.SO_NA_API, matricula,
                    servidor.nome(), servidor.email(), null, null,
                    !Email.valido(servidor.email())));
        });

        return new ComparacaoServidores(unidade.getId(), unidade.getNome(),
                unidade.getCodigoSiedos(),
                egesp.responsavelDaUnidade(unidade.getCodigoSiedos())
                        .map(r -> r.nome()).orElse(null),
                itens);
    }

    // ------------------------------------------------------------------
    // Aplicacao (sempre a pedido, item a item)
    // ------------------------------------------------------------------

    /** Cadastra localmente uma unidade que so existia no RH. */
    @Transactional
    public UnidadeJudiciaria cadastrarUnidade(long codigo) {
        UnidadeEgesp api = egesp.unidadePorCodigo(codigo).orElseThrow(
                () -> new NaoEncontradoException("Unidade " + codigo + " não existe no RH."));

        UnidadeJudiciaria unidade = localDe(api).orElseGet(
                () -> unidades.save(new UnidadeJudiciaria(api.nome())));
        unidade.vincularAoSiedos(api.codigo(), api.comarca());
        return unidade;
    }

    /** Adota nome e comarca do RH numa unidade ja cadastrada. */
    @Transactional
    public UnidadeJudiciaria atualizarUnidade(Long unidadeId) {
        UnidadeJudiciaria unidade = unidadeComCodigo(unidadeId);
        UnidadeEgesp api = egesp.unidadePorCodigo(unidade.getCodigoSiedos()).orElseThrow(
                () -> new NaoEncontradoException("Unidade não existe mais no RH."));

        unidades.findByNomeCanonico(Texto.canonicalizar(api.nome()))
                .filter(outra -> !outra.getId().equals(unidadeId))
                .ifPresent(outra -> {
                    throw new RegraDeNegocioException("Já existe outra unidade cadastrada com o "
                            + "nome \"" + api.nome() + "\". Resolva a duplicidade antes.");
                });

        unidade.renomear(api.nome());
        unidade.vincularAoSiedos(api.codigo(), api.comarca());
        return unidade;
    }

    /**
     * Inclui na lista da edicao alguem que o RH aponta como lotado na unidade.
     * Passa pelo servico de habilitados de proposito: e la que moram as guardas
     * de escopo e a regra de nao ressuscitar quem foi removido (008).
     */
    @Transactional
    public ServidorHabilitado incluirServidor(Long edicaoId, Long unidadeId, long matricula) {
        UnidadeJudiciaria unidade = unidadeComCodigo(unidadeId);
        ServidorEgesp servidor = egesp.servidorPorMatricula(matricula).orElseThrow(
                () -> new NaoEncontradoException(
                        "Matrícula " + matricula + " não encontrada no RH."));

        if (!Email.valido(servidor.email())) {
            throw new RegraDeNegocioException("O RH não tem e-mail corporativo para "
                    + Texto.aparar(servidor.nome()) + " (matrícula " + matricula + "). "
                    + "Sem e-mail a pessoa não é reconhecida no login e não conseguiria emitir.");
        }

        ServidorHabilitado incluido = servicoDeHabilitados.incluir(edicaoId, unidade.getId(),
                servidor.email(), servidor.nome(), servidor.cpf(), OrigemServidor.EGESP);
        incluido.definirMatricula(matricula);
        return incluido;
    }

    /**
     * Traz a unidade inteira de uma vez: cria quem falta no cadastro de
     * usuarios e habilita todos na edicao.
     *
     * <p>Duas coisas que a rotina <b>nao</b> faz, e ambas de proposito. Nao
     * mexe no papel de quem ja existe — o RH sabe onde a pessoa trabalha, nao o
     * que ela pode fazer no premio; um administrador importado como servidor
     * perderia acesso. E nao ressuscita quem foi removido da lista a mao: o
     * ajuste humano prevalece sobre o RH (008), e o numero de preservados vai
     * na resposta para que a diferenca nao pareca falha.
     */
    @Transactional
    public ImportacaoDaUnidade importarUnidade(Long unidadeId, Long edicaoId) {
        UnidadeJudiciaria unidade = unidadeComCodigo(unidadeId);
        edicoes.buscar(edicaoId);

        List<ServidorEgesp> doRh = new ArrayList<>();
        for (LotadoEgesp lotado : egesp.lotados(unidade.getCodigoSiedos())) {
            egesp.servidorPorMatricula(lotado.matricula())
                    .map(s -> Email.valido(s.email()) ? s
                            : new ServidorEgesp(null, lotado.nome(), s.cpf(), lotado.matricula()))
                    .ifPresentOrElse(doRh::add,
                            () -> doRh.add(new ServidorEgesp(null, lotado.nome(), null,
                                    lotado.matricula())));
        }

        int criados = 0;
        int atualizados = 0;
        for (ServidorEgesp servidor : doRh) {
            if (!Email.valido(servidor.email())) {
                continue;
            }
            String email = Email.normalizar(servidor.email());
            String cpf = Cpf.valido(servidor.cpf()) ? Cpf.normalizar(servidor.cpf()) : null;
            Optional<Usuario> jaCadastrado = usuarios.findByEmailIgnoreCase(email);

            if (jaCadastrado.isEmpty()) {
                Usuario novo = new Usuario(email, Texto.aparar(servidor.nome()), cpf,
                        EnumSet.of(Papel.SERVIDOR));
                novo.atualizarPeloRh(Texto.aparar(servidor.nome()), cpf, servidor.matricula(),
                        loginDe(email), unidade.getNome());
                usuarios.save(novo);
                criados++;
            } else {
                jaCadastrado.get().atualizarPeloRh(Texto.aparar(servidor.nome()), cpf,
                        servidor.matricula(), loginDe(email), unidade.getNome());
                atualizados++;
            }
        }

        SemeaduraResposta lista = servicoDeHabilitados.semearCom(edicaoId, unidadeId, doRh);

        return new ImportacaoDaUnidade(doRh.size(), criados, atualizados, lista.incluidos(),
                lista.jaExistentes(), lista.preservadosRemovidos(), lista.ignoradosSemEmail(),
                lista.totalAtivos());
    }

    /** O login de rede e o que vem antes do arroba. */
    private String loginDe(String email) {
        int arroba = email.indexOf('@');
        return arroba < 1 ? null : email.substring(0, arroba);
    }

    /** Remocao logica de quem nao consta mais na lotacao do RH. */
    @Transactional
    public void desvincular(Long servidorHabilitadoId) {
        ServidorHabilitado linha = habilitados.findById(servidorHabilitadoId).orElseThrow(
                () -> new NaoEncontradoException("Servidor não consta em nenhuma lista."));
        servicoDeHabilitados.remover(linha.getEdicao().getId(), linha.getUnidade().getId(),
                servidorHabilitadoId);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Primeiro pelo codigo; depois pelo nome, que e como o cadastro antigo nasceu. */
    private Optional<UnidadeJudiciaria> localDe(UnidadeEgesp api) {
        Optional<UnidadeJudiciaria> porCodigo = unidades.findByCodigoSiedos(api.codigo());
        if (porCodigo.isPresent()) {
            return porCodigo;
        }
        return unidades.findByNomeCanonico(Texto.canonicalizar(api.nome()));
    }

    private UnidadeJudiciaria unidadeComCodigo(Long unidadeId) {
        UnidadeJudiciaria unidade = unidades.findById(unidadeId).orElseThrow(
                () -> new NaoEncontradoException("Unidade " + unidadeId + " não encontrada."));
        if (unidade.getCodigoSiedos() == null) {
            throw new RegraDeNegocioException("Esta unidade ainda não foi casada com o RH. "
                    + "Sincronize as unidades primeiro.");
        }
        return unidade;
    }
}
