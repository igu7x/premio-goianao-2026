package br.jus.tjgo.goianao.servidor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.servidor.dto.IncluirServidorRequisicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Lista de servidores habilitados (feature 008)")
class ServidorHabilitadoIT extends TesteDeIntegracao {

    @Test
    @DisplayName("CA-1: semear traz os servidores da unidade segundo o EGESP")
    void semeadura() throws Exception {
        Cenario cenario = cenarioVigente(2110);

        mvc.perform(post(base(cenario) + "/semear")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incluidos").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.totalAtivos").value(org.hamcrest.Matchers.greaterThan(0)));

        mvc.perform(get(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servidores[0].origem").value("EGESP"))
                .andExpect(jsonPath("$.servidores[0].cpfMascarado")
                        .value(org.hamcrest.Matchers.startsWith("***.")));
    }

    @Test
    @DisplayName("CA-2: inclusao manual entra com origem MANUAL")
    void inclusaoManual() throws Exception {
        Cenario cenario = cenarioVigente(2111);

        mvc.perform(post(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(CPF_SERVIDOR, "Marcos"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origem").value("MANUAL"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("CA-6: CPF repetido na mesma unidade e edicao e rejeitado")
    void cpfDuplicado() throws Exception {
        Cenario cenario = cenarioVigente(2112);
        habilitarServidor(cenario.edicao.getId(), cenario.unidade.getId(), CPF_SERVIDOR, "Marcos");

        mvc.perform(post(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(CPF_SERVIDOR, "Marcos"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CA-3: o magistrado edita a lista da sua unidade na edicao vigente")
    void magistradoEditaNaVigente() throws Exception {
        Cenario cenario = cenarioVigente(2113);
        habilitarServidor(cenario.edicao.getId(), cenario.unidade.getId(), CPF_SERVIDOR, "Marcos");

        mvc.perform(delete(base(cenario) + "/" + CPF_SERVIDOR)
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO)))
                .andExpect(status().isNoContent());

        // Remocao logica: o item permanece para auditoria, porem inativo.
        Assertions.assertThat(servidores.estaHabilitado(
                cenario.edicao.getId(), cenario.unidade.getId(), CPF_SERVIDOR)).isFalse();
    }

    @Test
    @DisplayName("CA-4: em edicao publicada porem nao vigente, so o admin edita")
    void magistradoBloqueadoForaDaVigente() throws Exception {
        Cenario anterior = cenarioVigente(2114);
        edicaoVigente(2115); // a anterior deixa de ser vigente

        mvc.perform(post(base(anterior))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(CPF_SERVIDOR, "Marcos"))))
                .andExpect(status().isForbidden());

        mvc.perform(post(base(anterior))
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(CPF_SERVIDOR, "Marcos"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CA-5: o magistrado nao edita lista de unidade que nao e dele")
    void magistradoNaoEditaUnidadeAlheia() throws Exception {
        Edicao edicao = edicaoComLayouts(2116);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO_2, "Helena", UNIDADE_C, Selo.PRATA);
        edicoes.tornarVigente(edicoes.publicar(edicao.getId()).getId());

        UnidadeJudiciaria alheia = unidade(UNIDADE_C);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/unidades/" + alheia.getId()
                        + "/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(CPF_SERVIDOR, "Marcos"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ressemear mescla: nao remove inclusao manual nem reativa quem saiu")
    void ressemearPreservaAjustes() throws Exception {
        Cenario cenario = cenarioVigente(2117);
        Long edicaoId = cenario.edicao.getId();
        Long unidadeId = cenario.unidade.getId();

        atuandoComo(CPF_ADMIN);
        servidores.semear(edicaoId, unidadeId);

        // Um ajuste manual de cada tipo: uma inclusao e uma remocao.
        servidores.incluir(edicaoId, unidadeId, CPF_SERVIDOR_2, "Juliana Prado Ferreira");
        String cpfDoEgesp = servidores.listar(edicaoId, unidadeId).stream()
                .filter(s -> s.getOrigem() == OrigemServidor.EGESP)
                .findFirst().orElseThrow().getCpf();
        servidores.remover(edicaoId, unidadeId, cpfDoEgesp);

        mvc.perform(post(base(cenario) + "/semear")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incluidos").value(0))
                .andExpect(jsonPath("$.preservadosRemovidos").value(1));

        Assertions.assertThat(servidores.estaHabilitado(edicaoId, unidadeId, cpfDoEgesp)).isFalse();
        Assertions.assertThat(servidores.estaHabilitado(edicaoId, unidadeId, CPF_SERVIDOR_2))
                .isTrue();
    }

    @Test
    @DisplayName("unidade nao reconhecida na edicao nao tem lista a gerenciar")
    void unidadeNaoReconhecida() throws Exception {
        Cenario cenario = cenarioVigente(2118);
        UnidadeJudiciaria semReconhecimento = unidade(UNIDADE_B);

        mvc.perform(post("/api/edicoes/" + cenario.edicao.getId() + "/unidades/"
                        + semReconhecimento.getId() + "/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(CPF_SERVIDOR, "Marcos"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("o magistrado ve as listas das suas unidades na vigente")
    void visaoDoMagistrado() throws Exception {
        cenarioVigente(2119);

        mvc.perform(get("/api/magistrado/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unidadeNome").value(UNIDADE_A))
                .andExpect(jsonPath("$[0].podeEditar").value(true));
    }

    @Test
    @DisplayName("RNF-2: o CPF completo só vai para quem pode editar a lista")
    void cpfSoParaQuemEdita() throws Exception {
        Edicao edicao = edicaoComLayouts(2130);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO_2, "Helena", UNIDADE_C, Selo.PRATA);
        edicoes.tornarVigente(edicoes.publicar(edicao.getId()).getId());

        UnidadeJudiciaria unidadeA = unidade(UNIDADE_A);
        habilitarServidor(edicao.getId(), unidadeA.getId(), CPF_SERVIDOR, "Marcos");

        String caminho = "/api/edicoes/" + edicao.getId()
                + "/unidades/" + unidadeA.getId() + "/servidores";

        // O dono da unidade precisa do CPF: e por ele que a remocao acontece.
        mvc.perform(get(caminho).header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeEditar").value(true))
                .andExpect(jsonPath("$.servidores[0].cpf").value(CPF_SERVIDOR));

        // Quem so consulta recebe apenas o mascarado.
        mvc.perform(get(caminho).header(HttpHeaders.AUTHORIZATION, bearer(CPF_MAGISTRADO_2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeEditar").value(false))
                .andExpect(jsonPath("$.servidores[0].cpf").doesNotExist())
                .andExpect(jsonPath("$.servidores[0].cpfMascarado")
                        .value(org.hamcrest.Matchers.startsWith("***.")));
    }

    private record Cenario(Edicao edicao, UnidadeJudiciaria unidade) {}

    private String base(Cenario cenario) {
        return "/api/edicoes/" + cenario.edicao.getId()
                + "/unidades/" + cenario.unidade.getId() + "/servidores";
    }

    private Cenario cenarioVigente(int ano) {
        Edicao edicao = edicaoComLayouts(ano);
        cadastrarMagistrado(edicao.getId(), CPF_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());
        edicoes.tornarVigente(edicao.getId());
        return new Cenario(edicoes.buscar(edicao.getId()), unidade(UNIDADE_A));
    }
}
