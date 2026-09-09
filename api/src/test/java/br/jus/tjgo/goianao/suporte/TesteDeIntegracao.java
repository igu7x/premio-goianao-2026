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

    public static final String CPF_ADMIN = "10120230100";
    public static final String CPF_MAGISTRADO = "20450670252";
    public static final String CPF_MAGISTRADO_2 = "30980140323";
    public static final String CPF_SERVIDOR = "50760980578";
    public static final String CPF_SERVIDOR_2 = "60840310641";
    public static final String CPF_ESTRANHO = "90170360954";

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
        if (!administradores.existsByCpf(CPF_ADMIN)) {
            administradores.save(new Administrador(CPF_ADMIN, "Ana Cristina Marques Rebelo"));
        }
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Autenticacao
    // ------------------------------------------------------------------

    /** Token real, com os papeis resolvidos a partir do estado atual do banco. */
    protected String token(String cpf) {
        String nome = "Usuario " + cpf;
        return jwtService.gerar(new UsuarioAutenticado(cpf, nome, papeisResolver.resolver(cpf)));
    }

    protected String token(String cpf, String nome) {
        return jwtService.gerar(new UsuarioAutenticado(cpf, nome, papeisResolver.resolver(cpf)));
    }

    protected String bearer(String cpf) {
        return "Bearer " + token(cpf);
    }

    /**
     * Alguns servicos leem a identidade do contexto (auditoria, escopo do
     * magistrado). Quando a fixture os chama direto, o contexto precisa existir.
     */
    protected void atuandoComo(String cpf) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(cpf, "Usuario " + cpf,
                EnumSet.copyOf(papeisResolver.resolver(cpf)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.authorities()));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    protected Edicao novaEdicao(int ano) {
        atuandoComo(CPF_ADMIN);
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

    protected MagistradoReconhecido cadastrarMagistrado(Long edicaoId, String cpf, String nome,
                                                        String unidade, Selo selo) {
        atuandoComo(CPF_ADMIN);
        return magistrados.criar(edicaoId, new MagistradoRequisicao(cpf, nome,
                List.of(new ReconhecimentoRequisicao(null, unidade, selo))));
    }

    protected MagistradoReconhecido cadastrarMagistrado(Long edicaoId, String cpf, String nome,
                                                        List<ReconhecimentoRequisicao> itens) {
        atuandoComo(CPF_ADMIN);
        return magistrados.criar(edicaoId, new MagistradoRequisicao(cpf, nome, itens));
    }

    protected UnidadeJudiciaria unidade(String nome) {
        atuandoComo(CPF_ADMIN);
        return unidades.garantirDoEgesp(nome);
    }

    protected void habilitarServidor(Long edicaoId, Long unidadeId, String cpf, String nome) {
        atuandoComo(CPF_ADMIN);
        servidores.incluir(edicaoId, unidadeId, cpf, nome);
    }

    protected String corpo(Object objeto) {
        try {
            return json.writeValueAsString(objeto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
