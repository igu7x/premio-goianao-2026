package br.jus.tjgo.goianao.magistrado;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.certificado.dto.EmitirRequisicao;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.servidor.dto.IncluirServidorRequisicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Inclusao de reconhecimentos na edicao vigente (feature 009)")
class InclusaoNaVigenteIT extends TesteDeIntegracao {

    @Test
    @DisplayName("CA-1: na vigente, o admin adiciona um magistrado que faltou, e ele ja emite")
    void adicionaMagistradoNaVigente() throws Exception {
        Edicao edicao = vigenteComUmMagistrado(2120);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO_2, "Helena Aires", null,
                                List.of(new ReconhecimentoRequisicao(null, UNIDADE_C,
                                        Selo.DIAMANTE))))))
                .andExpect(status().isCreated());

        UnidadeJudiciaria unidade = unidade(UNIDADE_C);
        mvc.perform(post("/api/magistrado/certificados/emitir")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO_2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmitirRequisicao(edicao.getId(), unidade.getId()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA-2: na vigente, adiciona uma nova unidade a um magistrado ja cadastrado")
    void adicionaReconhecimento() throws Exception {
        Edicao edicao = vigenteComUmMagistrado(2121);
        MagistradoReconhecido magistrado = magistrados.listar(edicao.getId()).get(0);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados/"
                        + magistrado.getId() + "/reconhecimentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.PRATA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reconhecimentos.length()").value(2));

        mvc.perform(get("/api/edicoes/" + edicao.getId() + "/unidades-reconhecidas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("CA-3: repetir a unidade do mesmo magistrado da 409")
    void unidadeDuplicada() throws Exception {
        Edicao edicao = vigenteComUmMagistrado(2122);
        MagistradoReconhecido magistrado = magistrados.listar(edicao.getId()).get(0);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados/"
                        + magistrado.getId() + "/reconhecimentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new ReconhecimentoRequisicao(null, UNIDADE_A, Selo.PRATA))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CA-4: edicao publicada porem nao vigente permanece congelada")
    void publicadaNaoVigenteBloqueia() throws Exception {
        Edicao anterior = vigenteComUmMagistrado(2123);
        edicaoVigente(2124);
        // Mexer na anterior exige estar nela (011/RF-6); o congelamento vale mesmo assim.
        usando(anterior);

        mvc.perform(post("/api/edicoes/" + anterior.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO_2, "Helena", null,
                                List.of(new ReconhecimentoRequisicao(null, UNIDADE_C,
                                        Selo.PRATA))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("congelado")));
    }

    @Test
    @DisplayName("CA-5: a unidade nova ja fica gerenciavel na lista de servidores")
    void unidadeNovaEntraNa008() throws Exception {
        Edicao edicao = vigenteComUmMagistrado(2125);
        MagistradoReconhecido magistrado = magistrados.listar(edicao.getId()).get(0);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados/"
                        + magistrado.getId() + "/reconhecimentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.PRATA))))
                .andExpect(status().isCreated());

        UnidadeJudiciaria nova = unidade(UNIDADE_B);
        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/unidades/" + nova.getId()
                        + "/servidores/semear")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/unidades/" + nova.getId()
                        + "/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(EMAIL_SERVIDOR, "Marcos", null))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CA-6: nao administrador nao inclui")
    void naoAdminBloqueado() throws Exception {
        Edicao edicao = vigenteComUmMagistrado(2126);
        MagistradoReconhecido magistrado = magistrados.listar(edicao.getId()).get(0);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados/"
                        + magistrado.getId() + "/reconhecimentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ESTRANHO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.PRATA))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-7: em rascunho, a inclusao continua funcionando (regressao da 004)")
    void rascunhoContinuaAceitando() throws Exception {
        Edicao edicao = novaEdicao(2127);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        MagistradoReconhecido magistrado = magistrados.listar(edicao.getId()).get(0);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados/"
                        + magistrado.getId() + "/reconhecimentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new ReconhecimentoRequisicao(null, UNIDADE_B, Selo.PRATA))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RF-3: mesmo na vigente, editar e remover seguem bloqueados")
    void inclusaoNaoAbreEdicao() throws Exception {
        Edicao edicao = vigenteComUmMagistrado(2128);
        MagistradoReconhecido magistrado = magistrados.listar(edicao.getId()).get(0);

        mvc.perform(put("/api/edicoes/" + edicao.getId() + "/magistrados/" + magistrado.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO, "Outro Nome", null,
                                List.of(new ReconhecimentoRequisicao(null, UNIDADE_A,
                                        Selo.DIAMANTE))))))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/edicoes/" + edicao.getId() + "/magistrados/" + magistrado.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("elevar o maior selo na vigente vale para as emissoes seguintes")
    void maiorSeloRecalculado() throws Exception {
        Edicao edicao = vigenteComUmMagistrado(2129);
        UnidadeJudiciaria unidade = unidade(UNIDADE_A);
        habilitarServidor(edicao.getId(), unidade.getId(), EMAIL_SERVIDOR, "Marcos");

        mvc.perform(get("/api/servidor/certificados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR)))
                .andExpect(jsonPath("$[0].selo").value("OURO"));

        // Novo magistrado reconhece a mesma unidade com Diamante.
        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/magistrados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new MagistradoRequisicao(EMAIL_MAGISTRADO_2, "Helena", null,
                                List.of(new ReconhecimentoRequisicao(null, UNIDADE_A,
                                        Selo.DIAMANTE))))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/servidor/certificados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SERVIDOR)))
                .andExpect(jsonPath("$[0].selo").value("DIAMANTE"));
    }

    private Edicao vigenteComUmMagistrado(int ano) {
        Edicao edicao = edicaoComLayouts(ano);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael Siqueira Bittencourt",
                UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());
        return edicoes.tornarVigente(edicao.getId());
    }
}
