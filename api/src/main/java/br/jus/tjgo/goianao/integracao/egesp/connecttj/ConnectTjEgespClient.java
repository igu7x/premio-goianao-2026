package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.LotadoEgesp;
import br.jus.tjgo.goianao.integracao.egesp.ResponsavelEgesp;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import br.jus.tjgo.goianao.integracao.egesp.UnidadeEgesp;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Integracao real com o RH, pela API corporativa do TJGO (feature 010).
 *
 * <p>Duas particularidades da API moldam esta classe. A listagem de lotados e
 * <b>paginada</b> e nao traz e-mail — e o e-mail e a chave da pessoa aqui
 * (DI-24) —, entao cada lotado tem a matricula resolvida em
 * {@code buscar-por-matricula}. E o token vale 5 minutos: fica em cache e e
 * renovado no vencimento e no primeiro 401.
 */
public class ConnectTjEgespClient implements EgespClient {

    private static final Logger log = LoggerFactory.getLogger(ConnectTjEgespClient.class);

    private final ConnectTjProperties props;
    private final RestClient http;
    private final TokenConnectTj token;

    public ConnectTjEgespClient(ConnectTjProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.build();
        this.token = new TokenConnectTj(props, this.http);
    }

    @Override
    public boolean integracaoReal() {
        return true;
    }

    // ------------------------------------------------------------------
    // Unidades
    // ------------------------------------------------------------------

    @Override
    public List<UnidadeEgesp> listarUnidades(String filtro) {
        String nome = Texto.aparar(filtro);
        String uri = "/api/v1/unidades/buscar-unidades-ativas"
                + (nome == null ? "" : "?nome=" + enc(nome));

        RespostasConnectTj.UnidadeAtiva[] ativas =
                buscar(uri, RespostasConnectTj.UnidadeAtiva[].class);
        if (ativas == null) {
            return List.of();
        }
        return List.of(ativas).stream()
                .map(u -> new UnidadeEgesp(u.cdgUnidade(), u.nmeUnidade(), u.nomeComarca(), null))
                .toList();
    }

    @Override
    public List<UnidadeEgesp> hierarquia(long codigoUnidade) {
        RespostasConnectTj.UnidadeHierarquia[] arvore = buscar(
                "/api/v1/unidades/estrutura-hierarquica?codigoUnidade=" + codigoUnidade,
                RespostasConnectTj.UnidadeHierarquia[].class);
        if (arvore == null) {
            return List.of();
        }
        return List.of(arvore).stream()
                .map(u -> new UnidadeEgesp(u.cdgUnidade(), u.nomeUnidade(), u.nomeComarca(),
                        u.cdgUnidadePai()))
                .toList();
    }

    /**
     * Resolvida pela hierarquia, e nao por {@code /unidades/{cod}}: o detalhe
     * nao traz a comarca, e a comarca e o que distingue varas homonimas de
     * comarcas diferentes na tela de sincronizacao.
     */
    @Override
    public Optional<UnidadeEgesp> unidadePorCodigo(long codigoUnidade) {
        return hierarquia(codigoUnidade).stream()
                .filter(u -> u.codigo() != null && u.codigo() == codigoUnidade)
                .findFirst();
    }

    @Override
    public Optional<ResponsavelEgesp> responsavelDaUnidade(long codigoUnidade) {
        RespostasConnectTj.UnidadeDetalhe detalhe = buscar(
                "/api/v1/unidades/" + codigoUnidade, RespostasConnectTj.UnidadeDetalhe.class);
        if (detalhe == null || detalhe.matriculaResponsavel() == null) {
            return Optional.empty();
        }
        return Optional.of(new ResponsavelEgesp(
                detalhe.matriculaResponsavel(), detalhe.nomeServidorResponsavel()));
    }

    // ------------------------------------------------------------------
    // Servidores
    // ------------------------------------------------------------------

    /**
     * Percorre todas as paginas antes de devolver: meia lotacao seria pior do
     * que nenhuma, porque a tela de sincronizacao apontaria como "orfao" quem
     * simplesmente ficou na pagina seguinte.
     */
    @Override
    public List<LotadoEgesp> lotados(long codigoUnidade) {
        List<LotadoEgesp> todos = new ArrayList<>();
        int pagina = 0;
        int totalPaginas = 1;

        while (pagina < totalPaginas) {
            String uri = "/api/v1/unidades/" + codigoUnidade + "/lotados"
                    + "?incluirUnidadesSubordinadas=false"
                    + "&page=" + pagina + "&size=" + props.tamanhoPagina();

            RespostasConnectTj.LotadosPagina resposta =
                    buscar(uri, RespostasConnectTj.LotadosPagina.class);
            if (resposta == null || resposta.content() == null) {
                break;
            }
            resposta.content().stream()
                    .filter(l -> l.cdgOrdem() != null)
                    .map(l -> new LotadoEgesp(l.cdgOrdem(), l.nome(), l.nmeSitfunc(),
                            l.cdgUnidadeLotado()))
                    .forEach(todos::add);

            if (resposta.page() != null && resposta.page().totalPages() != null) {
                totalPaginas = resposta.page().totalPages();
            }
            pagina++;
        }
        return List.copyOf(todos);
    }

    @Override
    public Optional<ServidorEgesp> servidorPorMatricula(long matricula) {
        RespostasConnectTj.Servidor servidor = buscar(
                "/api/v1/servidores/buscar-por-matricula?matricula=" + matricula,
                RespostasConnectTj.Servidor.class);
        return Optional.ofNullable(comEmailDoAd(converter(servidor)));
    }

    /**
     * Completa o e-mail pelo AD quando o RH nao o tem.
     *
     * <p>Nao e caso raro: numa unidade real medida em 15/09/2026, cinco de oito
     * lotados vieram sem {@code endEmail} — residentes, nomeados em comissao e
     * ate estatutarios. Sem e-mail a pessoa nao e reconhecida no login (DI-24) e
     * ficaria de fora da lista de habilitados, ou seja, sem certificado.
     *
     * <p>O AD nao devolve e-mail, devolve o {@code samaccountname} — o login. E
     * o login e o prefixo do e-mail corporativo (confirmado em 2026-09-14), o
     * que permite reconstruir o endereco. Com isso a cobertura foi a 100% nas
     * duas unidades medidas.
     */
    private ServidorEgesp comEmailDoAd(ServidorEgesp servidor) {
        if (servidor == null || Email.valido(servidor.email()) || servidor.cpf() == null) {
            return servidor;
        }
        RespostasConnectTj.ContaAd[] contas = buscar(
                "/api/v1/ad/usuarios?cpf=" + enc(servidor.cpf()),
                RespostasConnectTj.ContaAd[].class);
        if (contas == null || contas.length == 0 || contas[0].samaccountname() == null) {
            return servidor;
        }
        String email = contas[0].samaccountname().trim() + "@" + props.dominioEmail();
        return new ServidorEgesp(email, servidor.nome(), servidor.cpf(), servidor.matricula());
    }

    /**
     * O e-mail <b>nao</b> e completado pelo AD aqui: seriam varias chamadas a
     * cada tecla digitada na busca. Quem for escolhido passa por
     * {@link #servidorPorMatricula}, que completa.
     */
    @Override
    public List<ServidorEgesp> procurarPessoas(String termo) {
        String busca = Texto.aparar(termo);
        if (busca == null || busca.length() < 3) {
            return List.of();
        }
        RespostasConnectTj.Servidor[] achados = buscar(
                "/api/v1/servidores/buscar-servidores-por-nome-ou-matricula?termo=" + enc(busca)
                        + "&incluirAposentados=false",
                RespostasConnectTj.Servidor[].class);
        if (achados == null) {
            return List.of();
        }
        return List.of(achados).stream().map(this::converter).filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<ServidorEgesp> servidorPorLogin(String loginAd) {
        String login = Texto.aparar(loginAd);
        if (login == null) {
            return Optional.empty();
        }
        RespostasConnectTj.BuscaPorLogin resposta = buscar(
                "/api/v1/servidores/buscar-servidor-por-login-ad?loginAd=" + enc(login),
                RespostasConnectTj.BuscaPorLogin.class);
        if (resposta == null || resposta.servidor() == null) {
            return Optional.empty();
        }

        ServidorEgesp servidor = converter(resposta.servidor());
        if (!Email.valido(servidor.email())) {
            // Aqui o login ja e conhecido: da para montar o e-mail sem ir ao AD.
            servidor = new ServidorEgesp(login + "@" + props.dominioEmail(), servidor.nome(),
                    servidor.cpf(), servidor.matricula());
        }
        return Optional.of(servidor);
    }

    /**
     * Lotacao da unidade pelo nome — o caminho que a semeadura ja usava (008).
     * Resolve o nome em codigo e, dai, cai no fluxo por codigo.
     */
    @Override
    public List<ServidorEgesp> listarServidoresPorUnidade(String nomeUnidade) {
        String canonico = Texto.canonicalizar(nomeUnidade);
        if (canonico == null || canonico.isBlank()) {
            return List.of();
        }
        Optional<UnidadeEgesp> unidade = listarUnidades(nomeUnidade).stream()
                .filter(u -> canonico.equals(Texto.canonicalizar(u.nome())))
                .findFirst();
        if (unidade.isEmpty() || unidade.get().codigo() == null) {
            return List.of();
        }

        List<ServidorEgesp> servidores = new ArrayList<>();
        for (LotadoEgesp lotado : lotados(unidade.get().codigo())) {
            servidorPorMatricula(lotado.matricula())
                    .map(s -> s.email() == null
                            ? new ServidorEgesp(null, lotado.nome(), s.cpf(), lotado.matricula())
                            : s)
                    .ifPresent(servidores::add);
        }
        return List.copyOf(servidores);
    }

    private ServidorEgesp converter(RespostasConnectTj.Servidor servidor) {
        if (servidor == null) {
            return null;
        }
        return new ServidorEgesp(servidor.endEmail(), servidor.nome(), servidor.nmrCpf(),
                servidor.cdgOrdem());
    }

    // ------------------------------------------------------------------
    // Transporte
    // ------------------------------------------------------------------

    /**
     * Uma retentativa no 401, nunca um laco: se o token novo tambem for
     * recusado, o problema e de credencial ou de permissao do client, e insistir
     * so transformaria erro de configuracao em tempestade de requisicoes.
     */
    private <T> T buscar(String caminho, Class<T> tipo) {
        try {
            return chamar(caminho, tipo);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("Token da API corporativa recusado; renovando e tentando outra vez.");
            token.invalidar();
            try {
                return chamar(caminho, tipo);
            } catch (RuntimeException falha) {
                throw new ConnectTjException(
                        "A API corporativa recusou as credenciais do sistema.", falha);
            }
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RuntimeException e) {
            throw new ConnectTjException("Falha ao consultar a API corporativa.", e);
        }
    }

    private <T> T chamar(String caminho, Class<T> tipo) {
        return http.get()
                .uri(props.url() + caminho)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.obter())
                .retrieve()
                .body(tipo);
    }

    /** Usado só para valores que vão na query string. */
    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /** Ponto de extensão para os testes do adaptador. */
    Supplier<String> tokenAtual() {
        return token::obter;
    }
}
