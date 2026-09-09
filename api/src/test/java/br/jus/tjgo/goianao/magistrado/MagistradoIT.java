package br.jus.tjgo.goianao.magistrado;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    @DisplayName("CA-1: um magistrado pode ter varias unidades com selos diferentes")
    void variosReconhecimentos() throws Exception {
        Edicao edicao = novaEdicao(2070);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(CPF_MAGISTRADO, "Rafael", List.of(
                                new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.OURO),
                                new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.BRONZE))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reconhecimentos.length()").value(2))
                .andExpect(jsonPath("$.cpfFormatado").value("204.506.702-52"));
    }

    @Test
    @DisplayName("CA-2: a mesma unidade duas vezes para o mesmo magistrado e rejeitada")
    void unidadeDuplicada() throws Exception {
        Edicao edicao = novaEdicao(2071);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(CPF_MAGISTRADO, "Rafael", List.of(
                                new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.OURO),
                                new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.BRONZE))))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CA-3: a unidade reconhecida por dois magistrados soma os selos")
    void selosAgregadosPorUnidade() throws Exception {
        Edicao edicao = novaEdicao(2072);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO, "Rafael", UNIDADE_A, Selo.BRONZE);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO_2, "Helena", UNIDADE_A, Selo.OURO);

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/unidades-reconhecidas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].selos.length()").value(2))
                .andExpect(jsonPath("$[0].maiorSelo").value("OURO"))
                .andExpect(jsonPath("$[0].magistrados").value(2));
    }

    @Test
    @DisplayName("CA-4: CPF invalido e recusado")
    void cpfInvalido() throws Exception {
        Edicao edicao = novaEdicao(2073);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao("11111111111", "Ninguem",
                                List.of(new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.OURO))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("CPF inválido."));
    }

    @Test
    @DisplayName("CA-5: nao administrador nao cadastra")
    void naoAdminBloqueado() throws Exception {
        Edicao edicao = novaEdicao(2074);

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ESTRANHO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF-1: unidade fora do catalogo do EGESP e recusada")
    void unidadeForaDoEgesp() throws Exception {
        Edicao edicao = novaEdicao(2075);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(CPF_MAGISTRADO, "Rafael", List.of(
                                new ReconhecimentoRequisicao(null, "Vara Inventada", Selo.OURO))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("EGESP")));
    }

    @Test
    @DisplayName("CA-6: a importacao agrupa linhas do mesmo CPF e reporta so as invalidas")
    void importacaoEmLote() throws Exception {
        Edicao edicao = novaEdicao(2076);

        String csv = "cpf;nome;unidade;selo\n"
                + CPF_MAGISTRADO + ";Rafael Bittencourt;" + UNIDADE_A + ";Ouro\n"
                + CPF_MAGISTRADO + ";Rafael Bittencourt;" + UNIDADE_B + ";Bronze\n"
                + CPF_MAGISTRADO_2 + ";Helena Aires;Vara Que Nao Existe;Ouro\n"
                + "11111111111;CPF Ruim;" + UNIDADE_C + ";Ouro\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                // Rafael entra com as duas unidades; as outras duas linhas viram erro.
                .andExpect(jsonPath("$.magistradosCriados").value(1))
                .andExpect(jsonPath("$.reconhecimentosCriados").value(2))
                .andExpect(jsonPath("$.erros.length()").value(2))
                .andExpect(jsonPath("$.erros[0].motivo").value("CPF inválido ou ausente."));

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reconhecimentos.length()").value(2));
    }

    @Test
    @DisplayName("RNF-3: magistrado com qualquer linha invalida e rejeitado inteiro")
    void importacaoNaoDeixaMagistradoPelaMetade() throws Exception {
        Edicao edicao = novaEdicao(2077);

        String csv = CPF_MAGISTRADO + ";Rafael;" + UNIDADE_A + ";Ouro\n"
                + CPF_MAGISTRADO + ";Rafael;" + UNIDADE_A + ";Bronze\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.magistradosCriados").value(0))
                .andExpect(jsonPath("$.erros.length()").value(2));
    }

    @Test
    @DisplayName("RF-11: a importacao em lote fica restrita ao rascunho")
    void importacaoSoEmRascunho() throws Exception {
        Edicao edicao = edicaoComLayouts(2079);
        edicoes.tornarVigente(edicoes.publicar(edicao.getId()).getId());

        String csv = CPF_MAGISTRADO + ";Rafael;" + UNIDADE_A + ";Ouro\n";

        mvc.perform(multipart("/api/edicoes/" + edicao.getId() + "/magistrados/importar")
                        .file(new MockMultipartFile("arquivo", "lote.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("RF-7: editar e remover ficam bloqueados apos publicar")
    void edicaoPublicadaNaoAceitaEdicao() throws Exception {
        Edicao edicao = edicaoComLayouts(2078);
        MagistradoReconhecido magistrado = cadastrarMagistrado(
                edicao.getId(), CPF_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());

        mvc.perform(delete("/api/edicoes/" + edicao.getId() + "/magistrados/"
                        + magistrado.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isConflict());
    }
}
