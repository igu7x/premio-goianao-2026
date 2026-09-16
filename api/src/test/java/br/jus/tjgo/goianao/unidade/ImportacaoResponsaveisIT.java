package br.jus.tjgo.goianao.unidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.magistrado.MagistradoRepository;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Planilha de magistrados responsaveis pelas unidades, com o selo de cada um.
 *
 * <p>O que estes testes protegem e a independencia das linhas: planilha de
 * tribunal chega com unidade extinta e e-mail errado no meio, e recusar o
 * arquivo inteiro obrigaria a refaze-lo para corrigir um nome.
 */
@DisplayName("Planilha de responsaveis pelas unidades")
class ImportacaoResponsaveisIT extends TesteDeIntegracao {

    private static final String EMAIL_SUPER = "super.responsaveis@tjgo.example";
    private static final long CODIGO = 600_000_009L;

    @Autowired private UsuarioRepository usuarios;
    @Autowired private UnidadeRepository unidades;
    @Autowired private MagistradoRepository reconhecidos;

    private Edicao edicao;
    private UnidadeJudiciaria unidade;

    @BeforeEach
    void cenario() {
        if (usuarios.findByEmailIgnoreCase(EMAIL_SUPER).isEmpty()) {
            usuarios.save(new Usuario(EMAIL_SUPER, "Super dos Responsaveis", null,
                    Set.of(Papel.SUPERADMIN, Papel.ADMINISTRADOR)));
        }
        edicao = novaEdicao(2081);
        unidade = unidade(UNIDADE_A);
        unidade.vincularAoSiedos(CODIGO, "Goiânia");
        unidades.saveAndFlush(unidade);
    }

    @Test
    @DisplayName("cria o magistrado, designa pela unidade e grava o selo na edicao")
    void criaDesignaEReconhece() throws Exception {
        importar("""
                nome;email;unidade;selo
                Igor Freitas;ifccteixeira@tjgo.example;%d;diamante
                """.formatted(CODIGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designados").value(1))
                .andExpect(jsonPath("$.usuariosCriados").value(1))
                .andExpect(jsonPath("$.reconhecimentos").value(1))
                .andExpect(jsonPath("$.edicaoAno").value(2081))
                .andExpect(jsonPath("$.erros.length()").value(0));

        Usuario criado = usuarios.findByEmailIgnoreCase("ifccteixeira@tjgo.example").orElseThrow();
        assertThat(criado.getPapeis()).contains(Papel.MAGISTRADO);
        assertThat(criado.getUnidadeLotacao())
                .as("quem responde pela unidade fica lotado nela")
                .isEqualTo(UNIDADE_A);
        assertThat(unidades.findById(unidade.getId()).orElseThrow().getResponsavel().getId())
                .isEqualTo(criado.getId());

        assertThat(reconhecidos.findByEdicaoIdAndEmail(edicao.getId(),
                        "ifccteixeira@tjgo.example"))
                .isPresent()
                .get()
                .satisfies(m -> assertThat(m.getReconhecimentos())
                        .singleElement()
                        .satisfies(r -> assertThat(r.getSelo()).isEqualTo(Selo.DIAMANTE)));
    }

    @Test
    @DisplayName("aceita os quatro selos, sem acento e sem caixa")
    void aceitaOsQuatroSelos() throws Exception {
        for (String texto : new String[] {"bronze", "PRATA", "Ouro", "diamante"}) {
            UnidadeJudiciaria outra = unidades.save(new UnidadeJudiciaria("Unidade do " + texto));
            outra.vincularAoSiedos(700_000L + texto.length() * 11L, "Goiânia");
            unidades.saveAndFlush(outra);

            importar("""
                    nome;email;unidade;selo
                    Magistrado do %s;selo.%s@tjgo.example;%d;%s
                    """.formatted(texto, texto.toLowerCase(java.util.Locale.ROOT),
                        outra.getCodigoSiedos(), texto))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.erros.length()").value(0))
                    .andExpect(jsonPath("$.reconhecimentos").value(1));
        }
    }

    @Test
    @DisplayName("selo inexistente vira erro de linha, com o texto que veio no arquivo")
    void seloInvalido() throws Exception {
        importar("""
                nome;email;unidade;selo
                Fulano de Tal;fulano@tjgo.example;%d;platina
                """.formatted(CODIGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designados").value(0))
                .andExpect(jsonPath("$.erros[0].motivo",
                        org.hamcrest.Matchers.containsString("platina")));

        assertThat(usuarios.findByEmailIgnoreCase("fulano@tjgo.example"))
                .as("linha rejeitada não cria usuário")
                .isEmpty();
    }

    /**
     * O ponto do relatorio: a linha ruim volta com o motivo e o texto original,
     * e a boa e gravada assim mesmo.
     */
    @Test
    @DisplayName("linha ruim nao derruba o lote: volta no relatorio e as demais sao gravadas")
    void linhaRuimNaoDerrubaOLote() throws Exception {
        importar("""
                nome;email;unidade;selo
                Alguem;alguem@tjgo.example;999999999;ouro
                Fulano;email-invalido;%d;ouro
                Magistrada Valida;valida@tjgo.example;%d;prata
                """.formatted(CODIGO, CODIGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designados").value(1))
                .andExpect(jsonPath("$.erros.length()").value(2))
                .andExpect(jsonPath("$.erros[0].motivo",
                        org.hamcrest.Matchers.containsString("não está cadastrada")))
                .andExpect(jsonPath("$.erros[1].motivo",
                        org.hamcrest.Matchers.containsString("inválido")));

        assertThat(usuarios.findByEmailIgnoreCase("valida@tjgo.example")).isPresent();
    }

    @Test
    @DisplayName("subir a mesma planilha de novo nao duplica nem conta em dobro")
    void reenvioNaoDuplica() throws Exception {
        String csv = """
                nome;email;unidade;selo
                Igor Freitas;repetido@tjgo.example;%d;ouro
                """.formatted(CODIGO);

        importar(csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.reconhecimentos").value(1));

        importar(csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.reconhecimentos").value(0))
                .andExpect(jsonPath("$.jaEram").value(1))
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    @Test
    @DisplayName("quem ja existe sem o papel de magistrado ganha o papel, que a designacao exige")
    void concedePapelAQuemJaExiste() throws Exception {
        usuarios.save(new Usuario("ja.existe@tjgo.example", "Ja Existe", null,
                Set.of(Papel.SERVIDOR)));

        importar("""
                nome;email;unidade;selo
                Ja Existe;ja.existe@tjgo.example;%d;bronze
                """.formatted(CODIGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuariosCriados").value(0))
                .andExpect(jsonPath("$.papelConcedido").value(1))
                .andExpect(jsonPath("$.designados").value(1));

        assertThat(usuarios.findByEmailIgnoreCase("ja.existe@tjgo.example").orElseThrow()
                        .getPapeis())
                .as("o papel anterior não se perde: papéis acumulam")
                .contains(Papel.SERVIDOR, Papel.MAGISTRADO);
    }

    @Test
    @DisplayName("a planilha troca o responsavel anterior, e o resumo diz quantos")
    void substituiResponsavelAnterior() throws Exception {
        Usuario anterior = usuarios.save(new Usuario("anterior@tjgo.example", "Responsavel Antigo",
                null, Set.of(Papel.MAGISTRADO)));
        unidade.designarResponsavel(anterior);
        unidades.saveAndFlush(unidade);

        importar("""
                nome;email;unidade;selo
                Responsavel Novo;novo@tjgo.example;%d;prata
                """.formatted(CODIGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.substituidos").value(1))
                .andExpect(jsonPath("$.designados").value(1));

        assertThat(unidades.findById(unidade.getId()).orElseThrow().getResponsavel().getEmail())
                .isEqualTo("novo@tjgo.example");
    }

    @Test
    @DisplayName("usuario desativado nao e reativado por planilha")
    void naoReativaDesativado() throws Exception {
        Usuario inativo = usuarios.save(new Usuario("inativo@tjgo.example", "Fora do Ar", null,
                Set.of(Papel.MAGISTRADO)));
        inativo.desativar();
        usuarios.saveAndFlush(inativo);

        importar("""
                nome;email;unidade;selo
                Fora do Ar;inativo@tjgo.example;%d;ouro
                """.formatted(CODIGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designados").value(0))
                .andExpect(jsonPath("$.erros[0].motivo",
                        org.hamcrest.Matchers.containsString("desativado")));
    }

    @Test
    @DisplayName("a importacao e exclusiva do superadministrador")
    void exigeSuperadmin() throws Exception {
        mvc.perform(multipart("/api/unidades/responsaveis/importar")
                        .file(arquivo("nome;email;unidade;selo\n"))
                        .param("edicaoId", edicao.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isForbidden());
    }

    private ResultActions importar(String csv) throws Exception {
        return mvc.perform(multipart("/api/unidades/responsaveis/importar")
                .file(arquivo(csv))
                .param("edicaoId", edicao.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)));
    }

    private MockMultipartFile arquivo(String csv) {
        return new MockMultipartFile("arquivo", "responsaveis.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
    }
}
