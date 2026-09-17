package br.jus.tjgo.goianao.unidade;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Designação do superior responsável pela unidade.
 *
 * <p>É o que separa <b>responder pela unidade</b> de <b>ter vencido o prêmio
 * nela</b>: antes, só quem foi reconhecido conseguia gerenciar a lista de
 * habilitados, o que deixava de fora justamente o chefe de uma vara que não
 * ganhou nada — e que é quem sabe quem trabalha ali.
 */
@DisplayName("Superior responsavel pela unidade")
class ResponsavelPelaUnidadeIT extends TesteDeIntegracao {

    /**
     * Deliberadamente diferente de {@code EMAIL_ADMIN}: o teste precisa provar que
     * ser administrador <b>não</b> abre o cadastro de unidades, e usar o mesmo
     * e-mail dos dois lados faria o teste concordar consigo mesmo.
     */
    private static final String EMAIL_SUPER = "super@tjgo.example";
    /** Magistrado do provedor mockado que NÃO é reconhecido nas unidades do teste. */
    private static final String EMAIL_CHEFE = EMAIL_MAGISTRADO_2;

    @Autowired private UsuarioRepository usuarios;
    @Autowired private UnidadeRepository unidadesRepo;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    private Long superadmin() {
        return usuarios.save(new Usuario(EMAIL_SUPER, "Super de Teste", null,
                Set.of(Papel.SUPERADMIN, Papel.ADMINISTRADOR))).getId();
    }

    private Long chefeMagistrado() {
        return usuarios.save(new Usuario(EMAIL_CHEFE, "Helena Chefe", null,
                Set.of(Papel.MAGISTRADO))).getId();
    }

    /** Edição vigente com a unidade reconhecida por OUTRO magistrado. */
    private Long unidadeReconhecidaPorOutro(int ano, String nomeUnidade) {
        Edicao edicao = edicaoVigente(ano);
        magistrados.criar(edicao.getId(), new MagistradoRequisicao(
                EMAIL_MAGISTRADO, "Rafael Vencedor", null,
                List.of(new ReconhecimentoRequisicao(null, nomeUnidade, Selo.OURO))));
        return unidade(nomeUnidade).getId();
    }

    @Test
    @DisplayName("designado passa a ver e a gerenciar a unidade, sem ter sido reconhecido nela")
    void designacaoDaEscopo() throws Exception {
        superadmin();
        Long chefeId = chefeMagistrado();
        Long unidadeId = unidadeReconhecidaPorOutro(2070, "1ª Vara Cível da Comarca de Goiânia");

        // Antes da designação: a unidade não é dele.
        mvc.perform(get("/api/magistrado/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_CHEFE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(put("/api/unidades/" + unidadeId + "/responsavel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + chefeId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidade.responsavel.nome").value("Helena Chefe"))
                .andExpect(jsonPath("$.unidade.responsavel.email").value(EMAIL_CHEFE));

        // Depois: a unidade aparece e ele pode editar.
        mvc.perform(get("/api/magistrado/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_CHEFE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unidadeId").value(unidadeId))
                .andExpect(jsonPath("$[0].podeEditar").value(true));
    }

    @Test
    @DisplayName("retirar a designacao devolve o escopo ao que era")
    void removerDesignacao() throws Exception {
        superadmin();
        Long chefeId = chefeMagistrado();
        Long unidadeId = unidadeReconhecidaPorOutro(2071, "2ª Vara Cível da Comarca de Goiânia");

        mvc.perform(put("/api/unidades/" + unidadeId + "/responsavel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + chefeId + "}"))
                .andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/unidades/" + unidadeId + "/responsavel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsavel").doesNotExist());
        // A retirada continua devolvendo a unidade em si: nao ha lista a semear.

        mvc.perform(get("/api/magistrado/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_CHEFE)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * O ponto da emenda de 17/09/2026: designar passou a ser tambem o ato que
     * traz a equipe do RH. Sem isto, o responsavel abria a tela dele numa
     * unidade vazia e tinha de semear a mao — e em unidade nao reconhecida nem
     * podia, porque nao havia lista nenhuma.
     */
    @Test
    @DisplayName("designar semeia a lista da unidade, mesmo sem reconhecimento na edicao")
    void designacaoSemeiaALista() throws Exception {
        superadmin();
        Long chefeId = chefeMagistrado();
        Edicao edicao = edicaoVigente(2076);
        // Unidade que ninguem venceu nesta edicao: so a designacao a traz.
        Long unidadeId = unidade(UNIDADE_B).getId();

        mvc.perform(put("/api/unidades/" + unidadeId + "/responsavel?edicaoId=" + edicao.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + chefeId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semeadura.incluidos")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.aviso").doesNotExist());

        // A lista aparece para ele pronta, sem passo nenhum no meio.
        mvc.perform(get("/api/magistrado/servidores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_CHEFE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unidadeId").value(unidadeId))
                .andExpect(jsonPath("$[0].servidores.length()")
                        .value(org.hamcrest.Matchers.greaterThan(0)));

        // E a lista da edicao mostra o numero, que e como o superadmin confere.
        mvc.perform(get("/api/unidades?edicaoId=" + edicao.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + unidadeId + ")].habilitados")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.greaterThan(0))));
    }

    @Test
    @DisplayName("so magistrado pode responder por unidade — a tela liberada e a dele")
    void exigePapelDeMagistrado() throws Exception {
        superadmin();
        Long semPapel = usuarios.save(new Usuario("servidor.comum@tjgo.example", "Servidor Comum",
                null, Set.of(Papel.SERVIDOR))).getId();
        Long unidadeId = unidadeReconhecidaPorOutro(2072, "3ª Vara Criminal da Comarca de Goiânia");

        mvc.perform(put("/api/unidades/" + unidadeId + "/responsavel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + semPapel + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("magistrado")));
    }

    /**
     * A listagem monta a resposta <b>fora</b> da transacao, com
     * {@code open-in-view} desligado. Se o responsavel vier preguicoso, a
     * primeira unidade designada derruba a tela inteira com 500 — foi o que
     * aconteceu em homologacao em 15/09/2026.
     *
     * <p>Limpar o contexto de persistencia e o que torna este teste honesto:
     * sem isso a entidade ja estaria na memoria da propria transacao do teste,
     * o proxy resolveria sozinho e o defeito passaria batido, que e exatamente
     * como ele escapou da primeira vez.
     */
    @Test
    @DisplayName("a listagem traz o responsavel carregado, e nao um proxy preguicoso")
    void responsavelVemCarregado() {
        superadmin();
        Long chefeId = chefeMagistrado();
        Long unidadeId = unidadeReconhecidaPorOutro(2075, UNIDADE_C);
        atuandoComo(EMAIL_SUPER);
        unidades.designarResponsavel(unidadeId, chefeId);

        em.flush();
        em.clear();

        List<UnidadeJudiciaria> listadas = unidadesRepo.findAllByOrderByNomeAsc();

        Assertions.assertThat(listadas)
                .filteredOn(u -> u.getId().equals(unidadeId))
                .isNotEmpty()
                .allSatisfy(u -> Assertions.assertThat(Hibernate.isInitialized(u.getResponsavel()))
                        .as("o responsavel precisa vir junto: a resposta e montada "
                                + "fora da transacao")
                        .isTrue());
    }

    @Test
    @DisplayName("o cadastro de unidades e exclusivo do superadministrador")
    void somenteSuperadmin() throws Exception {
        superadmin();
        mvc.perform(get("/api/unidades")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/unidades")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_SUPER)))
                .andExpect(status().isOk());
    }
}
