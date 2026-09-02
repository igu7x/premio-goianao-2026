package br.jus.tjgo.goianao.demo;

import br.jus.tjgo.goianao.auth.Administrador;
import br.jus.tjgo.goianao.auth.AdministradorRepository;
import br.jus.tjgo.goianao.auth.MockIdentityProvider;
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
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoService;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final LayoutRepository layouts;
    private final ImageStorage storage;
    private final GeradorArteDemo arte;

    public DadosDemo(AdministradorRepository administradores,
                     EdicaoRepository edicoesRepo,
                     EdicaoService edicoes,
                     MagistradoService magistrados,
                     ServidorHabilitadoService servidores,
                     UnidadeService unidades,
                     LayoutRepository layouts,
                     ImageStorage storage,
                     GeradorArteDemo arte) {
        this.administradores = administradores;
        this.edicoesRepo = edicoesRepo;
        this.edicoes = edicoes;
        this.magistrados = magistrados;
        this.servidores = servidores;
        this.unidades = unidades;
        this.layouts = layouts;
        this.storage = storage;
        this.arte = arte;
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

            exportarArtesDeExemplo(anoVigente);
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
                MockIdentityProvider.CPF_ADMIN,
                "Carga de demonstração",
                EnumSet.of(Papel.ADMINISTRADOR));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(carga, null, carga.authorities()));
    }

    @Transactional
    public void criarAdministradores() {
        administradores.save(new Administrador(
                MockIdentityProvider.CPF_ADMIN, "Ana Cristina Marques Rebelo"));
        // Acumula ADMINISTRADOR e MAGISTRADO: exercita a uniao de papeis (001/CA-4).
        administradores.save(new Administrador(
                MockIdentityProvider.CPF_ADMIN_MAGISTRADO, "Otávio Lemos Peixoto"));
    }

    private Edicao criarEdicaoCompleta(int ano) {
        Edicao edicao = edicoes.criar(new CriarEdicaoRequisicao(ano,
                "Edição " + ano + " do Prêmio Goianão — reconhecimento das unidades "
                        + "judiciárias de destaque do TJGO."));

        criarLayouts(edicao, ano);
        criarReconhecidos(edicao.getId());
        return edicao;
    }

    /** As 8 combinacoes selo x tipo: pre-condicao para publicar (002/RF-3b). */
    private void criarLayouts(Edicao edicao, int ano) {
        for (Selo selo : Selo.values()) {
            for (TipoCertificado tipo : TipoCertificado.values()) {
                String referencia = storage.salvar(arte.gerar(ano, selo, tipo), "png");
                layouts.save(new LayoutCertificado(
                        edicao, selo, tipo, referencia,
                        GeradorArteDemo.LARGURA, GeradorArteDemo.ALTURA,
                        areaNome(), areaUnidade(), areaCodigo()));
            }
        }
    }

    // Caixas calibradas sobre a arte de demonstracao. Elas sao o ponto de
    // partida do editor visual: o administrador arrasta e redimensiona a partir
    // daqui quando sobe a arte definitiva.
    private AreaTexto areaNome() {
        return new AreaTexto(454, 1163, 2600, 124, Alinhamento.CENTRO);
    }

    private AreaTexto areaUnidade() {
        return new AreaTexto(454, 1504, 2600, 92, Alinhamento.CENTRO);
    }

    private AreaCodigo areaCodigo() {
        return new AreaCodigo(520, 2230, 1000, 50, Alinhamento.ESQUERDA,
                new AreaQr(280, 2080, 200));
    }

    /**
     * Cenario montado de proposito: a 1a Vara Civel recebe Ouro de um magistrado
     * e Bronze de outro, para exercitar a regra do maior selo na emissao do
     * servidor (006/CA-1).
     */
    private void criarReconhecidos(Long edicaoId) {
        magistrados.criar(edicaoId, new MagistradoRequisicao(
                MockIdentityProvider.CPF_MAGISTRADO_1,
                "Rafael Siqueira Bittencourt",
                List.of(
                        new ReconhecimentoRequisicao(null, VARA_CIVEL_1, Selo.OURO),
                        new ReconhecimentoRequisicao(null, VARA_CIVEL_2, Selo.BRONZE),
                        new ReconhecimentoRequisicao(null, JUIZADO_ANAPOLIS, Selo.PRATA))));

        magistrados.criar(edicaoId, new MagistradoRequisicao(
                MockIdentityProvider.CPF_MAGISTRADO_2,
                "Helena Vasconcelos Aires",
                List.of(new ReconhecimentoRequisicao(null, VARA_CRIMINAL_3, Selo.DIAMANTE))));

        magistrados.criar(edicaoId, new MagistradoRequisicao(
                MockIdentityProvider.CPF_ADMIN_MAGISTRADO,
                "Otávio Lemos Peixoto",
                List.of(new ReconhecimentoRequisicao(null, VARA_CIVEL_1, Selo.BRONZE))));
    }

    private void semearListas(Long edicaoId) {
        for (String nome : List.of(VARA_CIVEL_1, VARA_CIVEL_2, VARA_CRIMINAL_3, JUIZADO_ANAPOLIS)) {
            UnidadeJudiciaria unidade = unidades.garantirDoEgesp(nome);
            servidores.semear(edicaoId, unidade.getId());
        }
    }

    /**
     * Grava as artes geradas em {@code data/artes-exemplo} para que seja possivel
     * testar o upload e o editor visual com arquivos reais, no formato correto.
     */
    private void exportarArtesDeExemplo(int ano) {
        Path destino = Path.of("data", "artes-exemplo");
        try {
            Files.createDirectories(destino);
            for (Selo selo : Selo.values()) {
                for (TipoCertificado tipo : TipoCertificado.values()) {
                    String nome = String.format("arte-%d-%s-%s.png", ano,
                            selo.name().toLowerCase(), tipo.name().toLowerCase());
                    Path arquivo = destino.resolve(nome);
                    if (!Files.exists(arquivo)) {
                        Files.write(arquivo, arte.gerar(ano, selo, tipo));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Nao foi possivel exportar as artes de exemplo: {}", e.getMessage());
        }
    }
}
