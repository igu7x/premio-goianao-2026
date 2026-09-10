package br.jus.tjgo.goianao.magistrado;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("Cadastro de magistrados reconhecidos (feature 004)")
class MagistradoIT extends TesteDeIntegracao {

    /** CPF ficticio, valido so quanto aos digitos; e apenas informativo (DI-24). */
    private static final String CPF_INFORMATIVO = "20450670252";

    private static List<ReconhecimentoRequisicao> umaUnidade() {
        return List.of(new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.OURO));
    }

    @Test
    @DisplayName("CA-1: um magistrado pode ter varias unidades com selos diferentes")
    void variosReconhecimentos() throws Exception {
        Edicao edicao = novaEdicao(2070);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO, "Rafael",
                                CPF_INFORMATIVO, List.of(
                                new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.OURO),
                                new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.BRONZE))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reconhecimentos.length()").value(2))
                .andExpect(jsonPath("$.email").value(EMAIL_MAGISTRADO))
                .andExpect(jsonPath("$.cpfFormatado").value("204.506.702-52"));
    }

    @Test
    @DisplayName("o CPF e opcional: sem ele o cadastro entra, e os campos de CPF vem vazios")
    void cpfOpcional() throws Exception {
        Edicao edicao = novaEdicao(2069);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao("Rafael.Bittencourt@TJGO.example",
                                "Rafael", null, umaUnidade()))))
                .andExpect(status().isCreated())
                // Gravado normalizado: a mesma pessoa nao vira duas por uma maiuscula.
                .andExpect(jsonPath("$.email").value(EMAIL_MAGISTRADO))
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andExpect(jsonPath("$.cpfFormatado").doesNotExist());
    }

    @Test
    @DisplayName("CA-2: a mesma unidade duas vezes para o mesmo magistrado e rejeitada")
    void unidadeDuplicada() throws Exception {
        Edicao edicao = novaEdicao(2071);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO, "Rafael", null,
                                List.of(
                                new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.OURO),
                                new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.BRONZE))))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("o mesmo e-mail duas vezes na edicao e rejeitado, ainda que em outra caixa")
    void emailDuplicadoNaEdicao() throws Exception {
        Edicao edicao = novaEdicao(2068);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao("RAFAEL.Bittencourt@tjgo.example",
                                "Rafael de novo", null,
                                List.of(new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.PRATA))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem",
                        Matchers.containsString("Já existe um magistrado com este e-mail")));
    }

    @Test
    @DisplayName("CPF nao e chave: dois magistrados podem ter o mesmo CPF informativo")
    void cpfNaoEUnico() throws Exception {
        Edicao edicao = novaEdicao(2067);
        atuandoComo(EMAIL_ADMIN);
        magistrados.criar(edicao.getId(), new MagistradoRequisicao(EMAIL_MAGISTRADO, "Rafael",
                CPF_INFORMATIVO, umaUnidade()));

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO_2, "Helena",
                                CPF_INFORMATIVO,
                                List.of(new ReconhecimentoRequisicao(null, UNIDADE_C, Selo.PRATA))))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CA-3: a unidade reconhecida por dois magistrados soma os selos")
    void selosAgregadosPorUnidade() throws Exception {
        Edicao edicao = novaEdicao(2072);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.BRONZE);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO_2, "Helena", UNIDADE_A, Selo.OURO);

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/unidades-reconhecidas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].selos.length()").value(2))
                .andExpect(jsonPath("$[0].maiorSelo").value("OURO"))
                .andExpect(jsonPath("$[0].magistrados").value(2));
    }

    @Test
    @DisplayName("CA-4: e-mail invalido e recusado")
    void emailInvalido() throws Exception {
        Edicao edicao = novaEdicao(2073);

        // Passa pela validacao de formato do DTO, mas nao pela regra de dominio
        // (dominio sem ponto) — e a mensagem que chega a tela.
        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao("fulano@intranet", "Ninguem", null,
                                umaUnidade()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("E-mail inválido."));

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao("nao-e-email", "Ninguem", null,
                                umaUnidade()))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("CA-4b: o CPF e opcional, mas se vier precisa ser valido")
    void cpfInformadoInvalido() throws Exception {
        Edicao edicao = novaEdicao(2066);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO, "Rafael",
                                "11111111111", umaUnidade()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("CPF inválido."));
    }

    @Test
    @DisplayName("editar em rascunho atualiza nome e CPF, mas nao troca o e-mail")
    void edicaoNaoTrocaEmail() throws Exception {
        Edicao edicao = novaEdicao(2065);
        MagistradoReconhecido magistrado = cadastrarMagistrado(
                edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        String caminho = "/api/edicoes/" + edicao.getId() + "/magistrados/" + magistrado.getId();

        mvc.perform(put(caminho)
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO_2, "Rafael",
                                null, umaUnidade()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem",
                        Matchers.containsString("não pode ser alterado")));

        mvc.perform(put(caminho)
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO,
                                "Rafael Siqueira Bittencourt", CPF_INFORMATIVO, umaUnidade()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_MAGISTRADO))
                .andExpect(jsonPath("$.nome").value("Rafael Siqueira Bittencourt"))
                .andExpect(jsonPath("$.cpfFormatado").value("204.506.702-52"));
    }

    @Test
    @DisplayName("CA-5: nao administrador nao cadastra")
    void naoAdminBloqueado() throws Exception {
        Edicao edicao = novaEdicao(2074);

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ESTRANHO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF-1: unidade fora do catalogo do EGESP e recusada")
    void unidadeForaDoEgesp() throws Exception {
        Edicao edicao = novaEdicao(2075);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO, "Rafael", null,
                                List.of(new ReconhecimentoRequisicao(null, "Vara Inventada",
                                        Selo.OURO))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("EGESP")));
    }

    @Test
    @DisplayName("CA-6: a importacao agrupa linhas do mesmo e-mail e reporta so as invalidas")
    void importacaoEmLote() throws Exception {
        Edicao edicao = novaEdicao(2076);

        String csv = "email;nome;unidade;selo;cpf\n"
                + EMAIL_MAGISTRADO + ";Rafael Bittencourt;" + UNIDADE_A + ";Ouro;"
                + CPF_INFORMATIVO + "\n"
                // Mesmo e-mail em outra caixa: agrupa com a linha de cima.
                + "Rafael.Bittencourt@TJGO.example;Rafael Bittencourt;" + UNIDADE_B + ";Bronze\n"
                + EMAIL_MAGISTRADO_2 + ";Helena Aires;Vara Que Nao Existe;Ouro\n"
                + "nao-e-email;E-mail Ruim;" + UNIDADE_C + ";Ouro\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                // Rafael entra com as duas unidades; as outras duas linhas viram erro.
                .andExpect(jsonPath("$.magistradosCriados").value(1))
                .andExpect(jsonPath("$.reconhecimentosCriados").value(2))
                .andExpect(jsonPath("$.criados[0]").value(
                        "Rafael Bittencourt (" + EMAIL_MAGISTRADO + ") - 2 unidade(s)"))
                .andExpect(jsonPath("$.erros.length()").value(2))
                .andExpect(jsonPath("$.erros[0].motivo").value("E-mail inválido ou ausente."));

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(EMAIL_MAGISTRADO))
                .andExpect(jsonPath("$[0].cpfFormatado").value("204.506.702-52"))
                .andExpect(jsonPath("$[0].reconhecimentos.length()").value(2));
    }

    @Test
    @DisplayName("importacao: CPF preenchido e errado recusa a linha; a coluna e opcional")
    void importacaoRecusaCpfInvalido() throws Exception {
        Edicao edicao = novaEdicao(2064);

        String csv = "email;nome;unidade;selo;cpf\n"
                + EMAIL_MAGISTRADO + ";Rafael;" + UNIDADE_A + ";Ouro;11111111111\n"
                + EMAIL_MAGISTRADO_2 + ";Helena;" + UNIDADE_C + ";Prata;\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.magistradosCriados").value(1))
                .andExpect(jsonPath("$.erros.length()").value(1))
                .andExpect(jsonPath("$.erros[0].motivo", Matchers.startsWith("CPF inválido")));
    }

    @Test
    @DisplayName("importacao: planilha no formato antigo (CPF primeiro) e recusada inteira")
    void importacaoFormatoAntigo() throws Exception {
        Edicao edicao = novaEdicao(2063);

        String csv = "cpf;nome;unidade;selo\n" + CPF_INFORMATIVO + ";Rafael;" + UNIDADE_A + ";Ouro\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("formato antigo")));
    }

    @Test
    @DisplayName("RNF-3: magistrado com qualquer linha invalida e rejeitado inteiro")
    void importacaoNaoDeixaMagistradoPelaMetade() throws Exception {
        Edicao edicao = novaEdicao(2077);

        String csv = EMAIL_MAGISTRADO + ";Rafael;" + UNIDADE_A + ";Ouro\n"
                + EMAIL_MAGISTRADO + ";Rafael;" + UNIDADE_A + ";Bronze\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.magistradosCriados").value(0))
                .andExpect(jsonPath("$.erros.length()").value(2));
    }

    @Test
    @DisplayName("RF-11: a importacao em lote fica restrita ao rascunho")
    void importacaoSoEmRascunho() throws Exception {
        Edicao edicao = edicaoComLayouts(2079);
        edicoes.tornarVigente(edicoes.publicar(edicao.getId()).getId());

        String csv = EMAIL_MAGISTRADO + ";Rafael;" + UNIDADE_A + ";Ouro\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("RF-7: editar e remover ficam bloqueados apos publicar")
    void edicaoPublicadaNaoAceitaEdicao() throws Exception {
        Edicao edicao = edicaoComLayouts(2078);
        MagistradoReconhecido magistrado = cadastrarMagistrado(
                edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());

        mvc.perform(delete("/api/edicoes/" + edicao.getId() + "/magistrados/"
                        + magistrado.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isConflict());
    }
}
