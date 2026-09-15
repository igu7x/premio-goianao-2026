package br.jus.tjgo.goianao.demo;

import br.jus.tjgo.goianao.auth.Administrador;
import br.jus.tjgo.goianao.auth.AdministradorRepository;
import br.jus.tjgo.goianao.auth.MockIdentityProvider;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoRepository;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.edicao.dto.CriarEdicaoRequisicao;
import br.jus.tjgo.goianao.layout.LayoutService;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoService;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import java.time.Year;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga de demonstracao, ativada por {@code goianao.dados-demo=true} (perfil
 * dev). Monta um cenario completo — edicao publicada e vigente, layouts,
 * reconhecidos e listas de servidores semeadas — para que o sistema possa ser
 * percorrido de ponta a ponta sem cadastro manual.
 *
 * <p>Ela e idempotente: se ja houver alguma edicao, nao faz nada.
 */
@Component
@ConditionalOnProperty(name = "goianao.dados-demo", havingValue = "true")
public class DadosDemo {

    private static final Logger log = LoggerFactory.getLogger(DadosDemo.class);

    private static final String VARA_CIVEL_1 = "1ª Vara Cível da Comarca de Goiânia";
    private static final String VARA_CIVEL_2 = "2ª Vara Cível da Comarca de Goiânia";
    private static final String VARA_CRIMINAL_3 = "3ª Vara Criminal da Comarca de Goiânia";
    private static final String JUIZADO_ANAPOLIS = "Juizado Especial Cível da Comarca de Anápolis";

    private final AdministradorRepository administradores;
    private final EdicaoRepository edicoesRepo;
    private final EdicaoService edicoes;
    private final MagistradoService magistrados;
    private final ServidorHabilitadoService servidores;
    private final UnidadeService unidades;
    private final LayoutService layouts;

    public DadosDemo(AdministradorRepository administradores,
                     EdicaoRepository edicoesRepo,
                     EdicaoService edicoes,
                     MagistradoService magistrados,
                     ServidorHabilitadoService servidores,
                     UnidadeService unidades,
                     LayoutService layouts) {
        this.administradores = administradores;
        this.edicoesRepo = edicoesRepo;
        this.edicoes = edicoes;
        this.magistrados = magistrados;
        this.servidores = servidores;
        this.unidades = unidades;
        this.layouts = layouts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void semear() {
        if (edicoesRepo.count() > 0) {
            log.info("Dados de demonstracao ja presentes; nada a fazer.");
            return;
        }
        try {
            autenticarComoAdministradorDeCarga();
            log.info("Gerando dados de demonstracao (pode levar alguns segundos).");
            criarAdministradores();

            int anoVigente = Year.now().getValue() - 1;
            Edicao publicada = criarEdicaoCompleta(anoVigente);
            edicoes.publicar(publicada.getId());
            edicoes.tornarVigente(publicada.getId());
            semearListas(publicada.getId());

            criarEdicaoCompleta(anoVigente + 1);

            log.info("Dados de demonstracao prontos. Edicao vigente: {}.", anoVigente);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar os dados de demonstracao.", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * A carga usa os mesmos servicos que a aplicacao — inclusive as guardas de
     * autorizacao. Por isso ela se apresenta como o administrador de referencia
     * em vez de escrever direto no banco.
     */
    private void autenticarComoAdministradorDeCarga() {
        UsuarioAutenticado carga = new UsuarioAutenticado(
                MockIdentityProvider.EMAIL_ADMIN,
                "Carga de demonstração",
                EnumSet.of(Papel.ADMINISTRADOR));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(carga, null, carga.authorities()));
    }

    @Transactional
    public void criarAdministradores() {
        administradores.save(new Administrador(
                MockIdentityProvider.EMAIL_ADMIN, "Ana Cristina Marques Rebelo"));
        // Acumula ADMINISTRADOR e MAGISTRADO: exercita a uniao de papeis (001/CA-4).
        administradores.save(new Administrador(
                MockIdentityProvider.EMAIL_ADMIN_MAGISTRADO, "Otávio Lemos Peixoto"));
    }

    private Edicao criarEdicaoCompleta(int ano) {
        Edicao edicao = edicoes.criar(new CriarEdicaoRequisicao(ano,
                "Edição " + ano + " do Prêmio Goianão — reconhecimento das unidades "
                        + "judiciárias de destaque do TJGO."));

        criarLayouts(edicao);
        criarReconhecidos(edicao.getId());
        return edicao;
    }

    /**
     * As 8 combinacoes selo x tipo: pre-condicao para publicar (002/RF-3b).
     *
     * Usa o mesmo caminho que o botao "usar as artes padrao" da tela, para que a
     * carga de demonstracao nao possa divergir do que o administrador recebe.
     */
    private void criarLayouts(Edicao edicao) {
        layouts.aplicarArtesPadrao(edicao.getId());
    }

    /**
     * Cenario montado de proposito: a 1a Vara Civel recebe Ouro de um magistrado
     * e Bronze de outro, para exercitar a regra do maior selo na emissao do
     * servidor (006/CA-1).
     */
    private void criarReconhecidos(Long edicaoId) {
        magistrados.criar(edicaoId, new MagistradoRequisicao(
                MockIdentityProvider.EMAIL_MAGISTRADO_1,
                "Rafael Siqueira Bittencourt",
                MockIdentityProvider.CPF_MAGISTRADO_1,
                List.of(
                        new ReconhecimentoRequisicao(null, VARA_CIVEL_1, Selo.OURO),
                        new ReconhecimentoRequisicao(null, VARA_CIVEL_2, Selo.BRONZE),
                        new ReconhecimentoRequisicao(null, JUIZADO_ANAPOLIS, Selo.PRATA))));

        magistrados.criar(edicaoId, new MagistradoRequisicao(
                MockIdentityProvider.EMAIL_MAGISTRADO_2,
                "Helena Vasconcelos Aires",
                MockIdentityProvider.CPF_MAGISTRADO_2,
                List.of(new ReconhecimentoRequisicao(null, VARA_CRIMINAL_3, Selo.DIAMANTE))));

        magistrados.criar(edicaoId, new MagistradoRequisicao(
                MockIdentityProvider.EMAIL_ADMIN_MAGISTRADO,
                "Otávio Lemos Peixoto",
                MockIdentityProvider.CPF_ADMIN_MAGISTRADO,
                List.of(new ReconhecimentoRequisicao(null, VARA_CIVEL_1, Selo.BRONZE))));
    }

    private void semearListas(Long edicaoId) {
        for (String nome : List.of(VARA_CIVEL_1, VARA_CIVEL_2, VARA_CRIMINAL_3, JUIZADO_ANAPOLIS)) {
            UnidadeJudiciaria unidade = unidades.garantirDoEgesp(nome);
            servidores.semear(edicaoId, unidade.getId());
        }
    }

}
