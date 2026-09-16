package br.jus.tjgo.goianao.servidor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.servidor.dto.IncluirServidorRequisicao;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incluidos").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.totalAtivos").value(Matchers.greaterThan(0)))
                // O EGESP mockado entrega e-mail para todos: nada fica de fora.
                .andExpect(jsonPath("$.ignoradosSemEmail").value(0));

        mvc.perform(get(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servidores[0].origem").value("EGESP"))
                .andExpect(jsonPath("$.servidores[0].email")
                        .value(Matchers.endsWith("@tjgo.example")))
                .andExpect(jsonPath("$.servidores[0].emailMascarado")
                        .value(Matchers.containsString("***@tjgo.example")))
                // O CPF do EGESP entra como dado informativo, sempre mascarado.
                .andExpect(jsonPath("$.servidores[0].cpfMascarado")
                        .value(Matchers.startsWith("***.")));
    }

    @Test
    @DisplayName("CA-2: inclusao manual entra com origem MANUAL, pelo e-mail e sem CPF")
    void inclusaoManual() throws Exception {
        Cenario cenario = cenarioVigente(2111);

        mvc.perform(post(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(
                                "Marcos.Paula@TJGO.example", "Marcos", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.origem").value("MANUAL"))
                .andExpect(jsonPath("$.ativo").value(true))
                // Gravado normalizado (DI-24).
                .andExpect(jsonPath("$.email").value(EMAIL_SERVIDOR))
                .andExpect(jsonPath("$.cpfMascarado").doesNotExist());
    }

    @Test
    @DisplayName("o CPF e opcional; se vier, precisa ser valido e sai mascarado")
    void cpfOpcional() throws Exception {
        Cenario cenario = cenarioVigente(2131);

        mvc.perform(post(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(
                                EMAIL_SERVIDOR, "Marcos", "11111111111"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("CPF inválido."));

        mvc.perform(post(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(
                                EMAIL_SERVIDOR, "Marcos", "507.609.805-78"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpfMascarado").value("***.609.805-**"));
    }

    @Test
    @DisplayName("CA-6: e-mail repetido na mesma unidade e edicao e rejeitado, em qualquer caixa")
    void emailDuplicado() throws Exception {
        Cenario cenario = cenarioVigente(2112);
        habilitarServidor(cenario.edicao.getId(), cenario.unidade.getId(), EMAIL_SERVIDOR, "Marcos");

        mvc.perform(post(base(cenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(
                                "MARCOS.PAULA@tjgo.example", "Marcos", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem")
                        .value("Este e-mail já consta na lista desta unidade nesta edição."));
    }

    @Test
    @DisplayName("CA-3: o magistrado edita a lista da sua unidade na vigente, removendo pelo id")
    void magistradoEditaNaVigente() throws Exception {
        Cenario cenario = cenarioVigente(2113);
        ServidorHabilitado servidor = habilitarServidor(
                cenario.edicao.getId(), cenario.unidade.getId(), EMAIL_SERVIDOR, "Marcos");

        mvc.perform(delete(base(cenario) + "/" + servidor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(status().isNoContent());

        // Remocao logica: o item permanece para auditoria, porem inativo.
        Assertions.assertThat(servidores.estaHabilitado(
                cenario.edicao.getId(), cenario.unidade.getId(), EMAIL_SERVIDOR)).isFalse();
    }

    @Test
    @DisplayName("a remocao e so pelo id: e-mail na URL nao e aceito, e id de outra lista nao remove")
    void remocaoSoPeloId() throws Exception {
        Cenario cenario = cenarioVigente(2132);
        Long edicaoId = cenario.edicao.getId();
        ServidorHabilitado servidor = habilitarServidor(
                edicaoId, cenario.unidade.getId(), EMAIL_SERVIDOR, "Marcos");

        // Dado pessoal nao vai na URL: o caminho so aceita o id numerico.
        mvc.perform(delete(base(cenario) + "/" + EMAIL_SERVIDOR)
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isBadRequest());

        // O id de um item da unidade A nao remove nada pela lista da unidade B.
        UnidadeJudiciaria outra = unidade(UNIDADE_B);
        mvc.perform(delete("/api/edicoes/" + edicaoId + "/unidades/" + outra.getId()
                        + "/servidores/" + servidor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isNotFound());

        Assertions.assertThat(servidores.estaHabilitado(
                edicaoId, cenario.unidade.getId(), EMAIL_SERVIDOR)).isTrue();
    }

    @Test
    @DisplayName("CA-4: em edicao publicada porem nao vigente, so o admin edita")
    void magistradoBloqueadoForaDaVigente() throws Exception {
        Cenario anterior = cenarioVigente(2114);
        edicaoVigente(2115); // a anterior deixa de ser vigente

        mvc.perform(post(base(anterior))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(EMAIL_SERVIDOR, "Marcos", null))))
                .andExpect(status().isForbidden());

        mvc.perform(post(base(anterior))
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(EMAIL_SERVIDOR, "Marcos", null))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CA-5: o magistrado nao edita lista de unidade que nao e dele")
    void magistradoNaoEditaUnidadeAlheia() throws Exception {
        Edicao edicao = edicaoComLayouts(2116);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO_2, "Helena", UNIDADE_C, Selo.PRATA);
        edicoes.tornarVigente(edicoes.publicar(edicao.getId()).getId());

        UnidadeJudiciaria alheia = unidade(UNIDADE_C);

        mvc.perform(post("/api/edicoes/" + edicao.getId() + "/unidades/" + alheia.getId()
                        + "/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(EMAIL_SERVIDOR, "Marcos", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ressemear mescla: nao remove inclusao manual nem reativa quem saiu")
    void ressemearPreservaAjustes() throws Exception {
        Cenario cenario = cenarioVigente(2117);
        Long edicaoId = cenario.edicao.getId();
        Long unidadeId = cenario.unidade.getId();

        atuandoComo(EMAIL_ADMIN);
        servidores.semear(edicaoId, unidadeId);

        // Um ajuste manual de cada tipo: uma inclusao e uma remocao.
        servidores.incluir(edicaoId, unidadeId, EMAIL_SERVIDOR_2, "Juliana Prado Ferreira", null);
        ServidorHabilitado doEgesp = servidores.listar(edicaoId, unidadeId).stream()
                .filter(s -> s.getOrigem() == OrigemServidor.EGESP)
                .findFirst().orElseThrow();
        servidores.remover(edicaoId, unidadeId, doEgesp.getId());

        mvc.perform(post(base(cenario) + "/semear")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incluidos").value(0))
                .andExpect(jsonPath("$.preservadosRemovidos").value(1))
                .andExpect(jsonPath("$.ignoradosSemEmail").value(0));

        Assertions.assertThat(servidores.estaHabilitado(edicaoId, unidadeId, doEgesp.getEmail()))
                .isFalse();
        Assertions.assertThat(servidores.estaHabilitado(edicaoId, unidadeId, EMAIL_SERVIDOR_2))
                .isTrue();
    }

    @Test
    @DisplayName("unidade nao reconhecida na edicao nao tem lista a gerenciar")
    void unidadeNaoReconhecida() throws Exception {
        Cenario cenario = cenarioVigente(2118);
        UnidadeJudiciaria semReconhecimento = unidade(UNIDADE_B);

        mvc.perform(post("/api/edicoes/" + cenario.edicao.getId() + "/unidades/"
                        + semReconhecimento.getId() + "/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new IncluirServidorRequisicao(EMAIL_SERVIDOR, "Marcos", null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("o magistrado ve as listas das suas unidades na vigente")
    void visaoDoMagistrado() throws Exception {
        cenarioVigente(2119);

        mvc.perform(get("/api/magistrado/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unidadeNome").value(UNIDADE_A))
                .andExpect(jsonPath("$[0].podeEditar").value(true));
    }

    @Test
    @DisplayName("RNF-2: o e-mail completo so vai para quem pode editar a lista")
    void emailSoParaQuemEdita() throws Exception {
        Edicao edicao = edicaoComLayouts(2130);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO_2, "Helena", UNIDADE_C, Selo.PRATA);
        edicoes.tornarVigente(edicoes.publicar(edicao.getId()).getId());

        UnidadeJudiciaria unidadeA = unidade(UNIDADE_A);
        ServidorHabilitado servidor =
                habilitarServidor(edicao.getId(), unidadeA.getId(), EMAIL_SERVIDOR, "Marcos");

        String caminho = "/api/edicoes/" + edicao.getId()
                + "/unidades/" + unidadeA.getId() + "/servidores";

        // O dono da unidade ve o e-mail completo, e o id pelo qual remove.
        mvc.perform(get(caminho).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeEditar").value(true))
                .andExpect(jsonPath("$.servidores[0].id").value(servidor.getId()))
                .andExpect(jsonPath("$.servidores[0].email").value(EMAIL_SERVIDOR));

        // Quem so consulta recebe apenas o mascarado — e o endereco nao aparece em lugar nenhum.
        mvc.perform(get(caminho).header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_MAGISTRADO_2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeEditar").value(false))
                .andExpect(jsonPath("$.servidores[0].email").doesNotExist())
                .andExpect(jsonPath("$.servidores[0].emailMascarado").value("m***@tjgo.example"))
                .andExpect(content().string(Matchers.not(Matchers.containsString(EMAIL_SERVIDOR))));
    }

    /**
     * A unidade aqui foi renomeada, e o nome local ja nao e o do RH. Se a
     * semeadura ainda procurasse pelo nome, traria a lotacao de outra unidade —
     * o mesmo que acontece com nome repetido entre comarcas.
     */
    @Test
    @DisplayName("semear vai pelo codigo do SIEDOS, e nao pelo nome, quando a unidade o tem")
    void semeaduraPeloCodigo() throws Exception {
        Cenario cenario = cenarioVigente(2140);
        UnidadeJudiciaria unidade = unidadesRepo.findById(cenario.unidade().getId()).orElseThrow();
        Long codigo = unidade.getCodigoSiedos();
        Assertions.assertThat(codigo)
                .as("a unidade do reconhecimento nasce com o codigo do RH")
                .isNotNull();
        unidade.renomear("Nome Local Que Nao Existe No RH");
        unidadesRepo.saveAndFlush(unidade);

        mvc.perform(post(base(cenario) + "/semear")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk());

        java.util.Set<String> esperados = egesp.servidoresPorCodigo(codigo).stream()
                .map(s -> s.email().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> semeados = habilitados
                .findByEdicaoIdAndUnidadeIdOrderByNomeAsc(cenario.edicao().getId(), unidade.getId())
                .stream()
                .map(s -> s.getEmail().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        Assertions.assertThat(semeados).isEqualTo(esperados);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private br.jus.tjgo.goianao.unidade.UnidadeRepository unidadesRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private br.jus.tjgo.goianao.integracao.egesp.EgespClient egesp;

    @org.springframework.beans.factory.annotation.Autowired
    private ServidorHabilitadoRepository habilitados;

    private record Cenario(Edicao edicao, UnidadeJudiciaria unidade) {}

    private String base(Cenario cenario) {
        return "/api/edicoes/" + cenario.edicao.getId()
                + "/unidades/" + cenario.unidade.getId() + "/servidores";
    }

    private Cenario cenarioVigente(int ano) {
        Edicao edicao = edicaoComLayouts(ano);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);
        edicoes.publicar(edicao.getId());
        edicoes.tornarVigente(edicao.getId());
        return new Cenario(edicoes.buscar(edicao.getId()), unidade(UNIDADE_A));
    }
}
