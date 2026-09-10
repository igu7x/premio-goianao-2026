package br.jus.tjgo.goianao.suporte;

import br.jus.tjgo.goianao.auth.Administrador;
import br.jus.tjgo.goianao.auth.AdministradorRepository;
import br.jus.tjgo.goianao.auth.PapeisResolver;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoRepository;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.edicao.dto.CriarEdicaoRequisicao;
import br.jus.tjgo.goianao.layout.Alinhamento;
import br.jus.tjgo.goianao.layout.AreaCodigo;
import br.jus.tjgo.goianao.layout.AreaQr;
import br.jus.tjgo.goianao.layout.AreaTexto;
import br.jus.tjgo.goianao.layout.LayoutCertificado;
import br.jus.tjgo.goianao.layout.LayoutRepository;
import br.jus.tjgo.goianao.layout.render.ImageStorage;
import br.jus.tjgo.goianao.magistrado.MagistradoReconhecido;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.seguranca.JwtService;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import br.jus.tjgo.goianao.servidor.ServidorHabilitado;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoService;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base dos testes de integracao: sobe o contexto completo, usa o MockMvc real
 * (com o filtro JWT no caminho) e monta fixtures pelos mesmos servicos da
 * aplicacao — inclusive passando pelas guardas de autorizacao.
 *
 * <p>Cada teste roda em transacao com rollback, entao os cenarios nao se
 * contaminam.
 */
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class TesteDeIntegracao {

    /*
     * Identidades de teste, chaveadas pelo e-mail (DI-24). Dominio reservado
     * .example (RFC 2606); os mesmos enderecos do provedor mockado, para que o
     * login mock e o EGESP mock enxerguem as mesmas pessoas.
     */
    public static final String EMAIL_ADMIN = "ana.rebelo@tjgo.example";
    public static final String EMAIL_MAGISTRADO = "rafael.bittencourt@tjgo.example";
    public static final String EMAIL_MAGISTRADO_2 = "helena.aires@tjgo.example";
    public static final String EMAIL_SERVIDOR = "marcos.paula@tjgo.example";
    public static final String EMAIL_SERVIDOR_2 = "juliana.ferreira@tjgo.example";
    public static final String EMAIL_ESTRANHO = "estranho@tjgo.example";

    public static final String UNIDADE_A = "1ª Vara Cível da Comarca de Goiânia";
    public static final String UNIDADE_B = "2ª Vara Cível da Comarca de Goiânia";
    public static final String UNIDADE_C = "3ª Vara Criminal da Comarca de Goiânia";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JwtService jwtService;
    @Autowired protected PapeisResolver papeisResolver;

    @Autowired protected AdministradorRepository administradores;
    @Autowired protected EdicaoRepository edicoesRepo;
    @Autowired protected EdicaoService edicoes;
    @Autowired protected LayoutRepository layouts;
    @Autowired protected ImageStorage storage;
    @Autowired protected MagistradoService magistrados;
    @Autowired protected ServidorHabilitadoService servidores;
    @Autowired protected UnidadeService unidades;
    @Autowired protected br.jus.tjgo.goianao.certificado.CertificadoEmitidoRepository certificados;

    @BeforeEach
    void prepararAdministrador() {
        if (!administradores.existsByEmail(EMAIL_ADMIN)) {
            administradores.save(new Administrador(EMAIL_ADMIN, "Ana Cristina Marques Rebelo"));
        }
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Autenticacao
    // ------------------------------------------------------------------

    /** Token real, com os papeis resolvidos a partir do estado atual do banco. */
    protected String token(String email) {
        return token(email, "Usuario " + email);
    }

    protected String token(String email, String nome) {
        return jwtService.gerar(new UsuarioAutenticado(email, nome, papeisResolver.resolver(email)));
    }

    protected String bearer(String email) {
        return "Bearer " + token(email);
    }

    /**
     * Alguns servicos leem a identidade do contexto (auditoria, escopo do
     * magistrado). Quando a fixture os chama direto, o contexto precisa existir.
     */
    protected void atuandoComo(String email) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(email, "Usuario " + email,
                EnumSet.copyOf(papeisResolver.resolver(email)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.authorities()));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    protected Edicao novaEdicao(int ano) {
        atuandoComo(EMAIL_ADMIN);
        return edicoes.criar(new CriarEdicaoRequisicao(ano, "Edicao de teste " + ano));
    }

    /** Cria a edicao ja com as 8 combinacoes de layout, pronta para publicar. */
    protected Edicao edicaoComLayouts(int ano) {
        Edicao edicao = novaEdicao(ano);
        criarTodosOsLayouts(edicao);
        return edicao;
    }

    protected Edicao edicaoPublicada(int ano) {
        Edicao edicao = edicaoComLayouts(ano);
        return edicoes.publicar(edicao.getId());
    }

    protected Edicao edicaoVigente(int ano) {
        Edicao edicao = edicaoPublicada(ano);
        return edicoes.tornarVigente(edicao.getId());
    }

    protected void criarTodosOsLayouts(Edicao edicao) {
        for (Selo selo : Selo.values()) {
            for (TipoCertificado tipo : TipoCertificado.values()) {
                criarLayout(edicao, selo, tipo);
            }
        }
    }

    protected LayoutCertificado criarLayout(Edicao edicao, Selo selo, TipoCertificado tipo) {
        String referencia = storage.salvar(ArteDeTeste.valida(), "png");
        return layouts.save(new LayoutCertificado(
                edicao, selo, tipo, referencia, ArteDeTeste.LARGURA, ArteDeTeste.ALTURA,
                new AreaTexto(454, 1150, 2600, 150, Alinhamento.CENTRO),
                new AreaTexto(454, 1495, 2600, 110, Alinhamento.CENTRO),
                new AreaCodigo(520, 2230, 1000, 50, Alinhamento.ESQUERDA,
                        new AreaQr(280, 2080, 200))));
    }

    /** Cadastra o magistrado sem CPF: desde a DI-24 ele e opcional. */
    protected MagistradoReconhecido cadastrarMagistrado(Long edicaoId, String email, String nome,
                                                        String unidade, Selo selo) {
        atuandoComo(EMAIL_ADMIN);
        return magistrados.criar(edicaoId, new MagistradoRequisicao(email, nome, null,
                List.of(new ReconhecimentoRequisicao(null, unidade, selo))));
    }

    protected MagistradoReconhecido cadastrarMagistrado(Long edicaoId, String email, String nome,
                                                        List<ReconhecimentoRequisicao> itens) {
        atuandoComo(EMAIL_ADMIN);
        return magistrados.criar(edicaoId, new MagistradoRequisicao(email, nome, null, itens));
    }

    protected UnidadeJudiciaria unidade(String nome) {
        atuandoComo(EMAIL_ADMIN);
        return unidades.garantirDoEgesp(nome);
    }

    /** Habilita o servidor sem CPF e devolve o item criado (a remocao e pelo id). */
    protected ServidorHabilitado habilitarServidor(Long edicaoId, Long unidadeId, String email,
                                                   String nome) {
        atuandoComo(EMAIL_ADMIN);
        return servidores.incluir(edicaoId, unidadeId, email, nome, null);
    }

    protected String corpo(Object objeto) {
        try {
            return json.writeValueAsString(objeto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
