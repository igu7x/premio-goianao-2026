package br.jus.tjgo.goianao.sincronizacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.LotadoEgesp;
import br.jus.tjgo.goianao.integracao.egesp.MockEgespClient;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.servidor.OrigemServidor;
import br.jus.tjgo.goianao.servidor.ServidorHabilitado;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoRepository;
import br.jus.tjgo.goianao.sincronizacao.dto.ItemSincronizacao;
import br.jus.tjgo.goianao.sincronizacao.dto.ServidorComparado;
import br.jus.tjgo.goianao.sincronizacao.dto.UnidadeComparada;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeRepository;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Sincronizacao com o RH (feature 010), exercitada contra o EGESP mockado.
 *
 * <p>O ponto que estes testes protegem e o contrato da tela: <b>comparar nao
 * grava</b>, e cada alteracao tem de ser pedida. Um motor de comparacao que
 * gravasse sozinho renomearia unidade impressa em certificado.
 */
@DisplayName("Sincronizacao com o RH (feature 010)")
class SincronizacaoIT extends TesteDeIntegracao {

    private static final String EMAIL_SUPER = "super.sincronizacao@tjgo.example";
    private static final long CODIGO_UNIDADE_A = MockEgespClient.CODIGO_RAIZ + 1;

    @Autowired private UsuarioRepository usuarios;
    @Autowired private UnidadeRepository unidadesRepo;
    @Autowired private ServidorHabilitadoRepository habilitados;
    @Autowired private EgespClient egesp;

    @BeforeEach
    void criarSuperadministrador() {
        if (usuarios.findByEmailIgnoreCase(EMAIL_SUPER).isEmpty()) {
            usuarios.save(new Usuario(EMAIL_SUPER, "Super da Sincronizacao", null,
                    Set.of(Papel.SUPERADMIN, Papel.ADMINISTRADOR)));
        }
    }

    // ------------------------------------------------------------------
    // Acesso
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a tela e exclusiva do superadministrador")
    void somenteSuperadmin() throws Exception {
        mvc.perform(get("/api/sincronizacao/situacao").header(HttpHeaders.AUTHORIZATION,
                        bearer(EMAIL_ADMIN)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/sincronizacao/situacao").header(HttpHeaders.AUTHORIZATION,
                        bearer(EMAIL_SUPER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ligada").value(false));
    }

    // ------------------------------------------------------------------
    // Unidades
    // ------------------------------------------------------------------

    @Test
    @DisplayName("unidade que so existe no RH aparece como SO_NA_API e nao e criada pela comparacao")
    void comparacaoNaoGrava() throws Exception {
        long antes = unidadesRepo.count();

        List<UnidadeComparada> comparadas = compararUnidades();

        assertThat(comparadas).isNotEmpty();
        assertThat(comparadas).anyMatch(u -> u.situacao() == ItemSincronizacao.SO_NA_API);
        assertThat(unidadesRepo.count())
                .as("comparar e leitura: nada pode ter sido gravado")
                .isEqualTo(antes);
    }

    @Test
    @DisplayName("unidade cadastrada pelo nome, sem codigo, aparece desatualizada ate ser casada")
    void casamentoPeloNomeDepoisPeloCodigo() throws Exception {
        UnidadeJudiciaria local = unidade(UNIDADE_A);
        assertThat(local.getCodigoSiedos()).isNull();

        assertThat(situacaoDe(compararUnidades(), CODIGO_UNIDADE_A))
                .isEqualTo(ItemSincronizacao.DESATUALIZADO);

        mvc.perform(post("/api/sincronizacao/unidades")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("codigo", CODIGO_UNIDADE_A))))
                .andExpect(status().isCreated());

        assertThat(unidadesRepo.findByCodigoSiedos(CODIGO_UNIDADE_A))
                .as("a unidade existente foi casada, e nao duplicada")
                .isPresent()
                .get()
                .extracting(UnidadeJudiciaria::getId)
                .isEqualTo(local.getId());
        assertThat(situacaoDe(compararUnidades(), CODIGO_UNIDADE_A))
                .isEqualTo(ItemSincronizacao.SINCRONIZADO);
    }

    @Test
    @DisplayName("unidade que nao existia no banco e criada com codigo e comarca")
    void cadastraUnidadeNova() throws Exception {
        mvc.perform(post("/api/sincronizacao/unidades")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("codigo", CODIGO_UNIDADE_A))))
                .andExpect(status().isCreated());

        assertThat(unidadesRepo.findByCodigoSiedos(CODIGO_UNIDADE_A))
                .isPresent()
                .get()
                .satisfies(u -> {
                    assertThat(u.getNome()).isEqualTo(UNIDADE_A);
                    assertThat(u.getComarca()).isEqualTo("Goiânia");
                });
    }

    // ------------------------------------------------------------------
    // Servidores
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lotado do RH fora da lista aparece como SO_NA_API e entra por acao explicita")
    void incluiServidorPelaMatricula() throws Exception {
        Cenario cenario = cenarioComUnidadeCasada();
        LotadoEgesp lotado = egesp.lotados(CODIGO_UNIDADE_A).get(0);

        List<ServidorComparado> antes = compararServidores(cenario);
        assertThat(antes).anyMatch(s -> s.situacao() == ItemSincronizacao.SO_NA_API
                && s.matricula().equals(lotado.matricula()));

        mvc.perform(post("/api/sincronizacao/unidades/" + cenario.unidadeId() + "/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("edicaoId", cenario.edicaoId(),
                                "matricula", lotado.matricula()))))
                .andExpect(status().isCreated());

        ServidorHabilitado gravado = habilitados
                .findByEdicaoIdAndUnidadeIdOrderByNomeAsc(cenario.edicaoId(), cenario.unidadeId())
                .stream()
                .filter(s -> lotado.matricula() == (s.getMatricula() == null ? -1 : s.getMatricula()))
                .findFirst()
                .orElseThrow();

        assertThat(gravado.getEmail())
                .as("o e-mail foi resolvido pela matricula: sem ele ninguem emite (DI-24)")
                .isNotBlank();
        assertThat(gravado.getOrigem()).isEqualTo(OrigemServidor.EGESP);
    }

    @Test
    @DisplayName("quem esta na lista e sumiu do RH vira orfao e some por desvinculacao")
    void desvinculaOrfao() throws Exception {
        Cenario cenario = cenarioComUnidadeCasada();
        ServidorHabilitado estranho = habilitarServidor(cenario.edicaoId(), cenario.unidadeId(),
                EMAIL_ESTRANHO, "Quem Saiu da Unidade");

        ServidorComparado orfao = compararServidores(cenario).stream()
                .filter(s -> EMAIL_ESTRANHO.equals(s.email()))
                .findFirst()
                .orElseThrow();
        assertThat(orfao.situacao()).isEqualTo(ItemSincronizacao.ORFAO);
        assertThat(orfao.origem())
                .as("inclusao manual precisa ser reconhecivel na tela, para nao sair por rotina")
                .isEqualTo(OrigemServidor.MANUAL);

        mvc.perform(delete("/api/sincronizacao/servidores/" + estranho.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isNoContent());

        assertThat(habilitados.findById(estranho.getId()))
                .get()
                .satisfies(s -> assertThat(s.isAtivo())
                        .as("remocao e logica: o historico fica")
                        .isFalse());
    }

    @Test
    @DisplayName("importar a unidade cria os usuarios e habilita todos de uma vez")
    void importaUnidadeInteira() throws Exception {
        Cenario cenario = cenarioComUnidadeCasada();
        long usuariosAntes = usuarios.count();

        String resposta = mvc.perform(
                        post("/api/sincronizacao/unidades/" + cenario.unidadeId() + "/importar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(Map.of("edicaoId", cenario.edicaoId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var resumo = json.readTree(resposta);
        assertThat(resumo.get("usuariosCriados").asInt()).isPositive();
        assertThat(resumo.get("habilitadosIncluidos").asInt()).isPositive();
        assertThat(usuarios.count()).isGreaterThan(usuariosAntes);

        assertThat(habilitados.findByEdicaoIdAndUnidadeIdOrderByNomeAsc(
                        cenario.edicaoId(), cenario.unidadeId()))
                .as("todo mundo que entrou veio com e-mail: sem ele ninguem emite")
                .allSatisfy(s -> assertThat(s.getEmail()).isNotBlank());
    }

    @Test
    @DisplayName("importar nao ressuscita quem foi removido da lista a mao")
    void importarNaoReativaRemovido() throws Exception {
        Cenario cenario = cenarioComUnidadeCasada();
        mvc.perform(post("/api/sincronizacao/unidades/" + cenario.unidadeId() + "/importar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("edicaoId", cenario.edicaoId()))))
                .andExpect(status().isOk());

        ServidorHabilitado alguem = habilitados.findByEdicaoIdAndUnidadeIdOrderByNomeAsc(
                cenario.edicaoId(), cenario.unidadeId()).get(0);
        atuandoComo(EMAIL_ADMIN);
        servidores.remover(cenario.edicaoId(), cenario.unidadeId(), alguem.getId());

        String resposta = mvc.perform(
                        post("/api/sincronizacao/unidades/" + cenario.unidadeId() + "/importar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(Map.of("edicaoId", cenario.edicaoId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(resposta).get("preservadosRemovidos").asInt())
                .as("o ajuste humano prevalece sobre o RH (008)")
                .isPositive();
        assertThat(habilitados.findById(alguem.getId())).get()
                .satisfies(s -> assertThat(s.isAtivo()).isFalse());
    }

    @Test
    @DisplayName("unidade sem codigo nao pode comparar servidores")
    void exigeUnidadeCasada() throws Exception {
        Edicao edicao = edicaoVigente(2036);
        UnidadeJudiciaria local = unidade(UNIDADE_B);

        mvc.perform(get("/api/sincronizacao/unidades/" + local.getId() + "/servidores")
                        .param("edicaoId", edicao.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private record Cenario(Long edicaoId, Long unidadeId) {}

    /** Edicao vigente com a unidade reconhecida e ja casada com o RH. */
    private Cenario cenarioComUnidadeCasada() {
        Edicao edicao = edicaoVigente(2035);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Magistrado de Teste",
                UNIDADE_A, Selo.OURO);
        UnidadeJudiciaria local = unidadesRepo.findByNomeCanonico(
                br.jus.tjgo.goianao.comum.Texto.canonicalizar(UNIDADE_A)).orElseThrow();
        local.vincularAoSiedos(CODIGO_UNIDADE_A, "Goiânia");
        atuandoComo(EMAIL_ADMIN);
        return new Cenario(edicao.getId(), local.getId());
    }

    private List<UnidadeComparada> compararUnidades() throws Exception {
        String resposta = mvc.perform(get("/api/sincronizacao/unidades")
                        .param("codigo", Long.toString(MockEgespClient.CODIGO_RAIZ))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(resposta, json.getTypeFactory()
                .constructCollectionType(List.class, UnidadeComparada.class));
    }

    private List<ServidorComparado> compararServidores(Cenario cenario) throws Exception {
        String resposta = mvc.perform(
                        get("/api/sincronizacao/unidades/" + cenario.unidadeId() + "/servidores")
                                .param("edicaoId", cenario.edicaoId().toString())
                                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var no = json.readTree(resposta).get("servidores");
        return json.readValue(no.toString(), json.getTypeFactory()
                .constructCollectionType(List.class, ServidorComparado.class));
    }

    private ItemSincronizacao situacaoDe(List<UnidadeComparada> comparadas, long codigo) {
        return comparadas.stream()
                .filter(u -> u.codigo() != null && u.codigo() == codigo)
                .findFirst()
                .orElseThrow()
                .situacao();
    }
}
