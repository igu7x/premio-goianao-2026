package br.jus.tjgo.goianao.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * Busca de pessoas no sistema e no RH.
 *
 * <p>O caso que motivou: magistrado criado pela planilha, com e-mail que o RH
 * nao conhece. A busca so no RH dizia "ninguem com esse nome" sobre alguem que
 * estava no proprio cadastro.
 */
@DisplayName("Busca de pessoas no sistema e no RH")
class BuscaDePessoasIT extends TesteDeIntegracao {

    @Autowired private UsuarioRepository usuarios;

    @Test
    @DisplayName("encontra quem so existe no sistema, sem acento e sem caixa")
    void encontraQuemSoEstaNoSistema() throws Exception {
        usuarios.save(new Usuario("rebelo.so.no.sistema@tjgo.example", "Ana Cristina Marques Rebélo", null,
                Set.of(Papel.MAGISTRADO)));

        mvc.perform(get("/api/pessoas").param("termo", "rebelo")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoas[0].origem").value("SISTEMA"))
                .andExpect(jsonPath("$.pessoas[0].email").value("rebelo.so.no.sistema@tjgo.example"))
                .andExpect(jsonPath("$.pessoas[0].temEmail").value(true));
    }

    @Test
    @DisplayName("junta os do RH, e quem esta nos dois aparece uma vez so, como do sistema")
    void juntaSemRepetir() throws Exception {
        // "Marcos Vinícius de Paula" existe no RH mockado com este e-mail. Outro
        // teste — a atualizacao da base — pode ja te-lo criado: e-mail e unico, e
        // o cenario precisa valer em qualquer ordem da suite.
        if (usuarios.findByEmailIgnoreCase("marcos.paula@tjgo.example").isEmpty()) {
            usuarios.save(new Usuario("marcos.paula@tjgo.example", "Marcos Vinícius de Paula",
                    null, Set.of(Papel.SERVIDOR)));
        }

        mvc.perform(get("/api/pessoas").param("termo", "marcos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoas[?(@.email == 'marcos.paula@tjgo.example')]",
                        Matchers.hasSize(1)))
                .andExpect(jsonPath("$.pessoas[?(@.email == 'marcos.paula@tjgo.example')].origem",
                        Matchers.contains("SISTEMA")));
    }

    @Test
    @DisplayName("usuario desativado nao aparece para ser escolhido")
    void ignoraDesativado() throws Exception {
        Usuario inativo = usuarios.save(new Usuario("fora.do.ar@tjgo.example",
                "Zuleica Desativada", null, Set.of(Papel.MAGISTRADO)));
        inativo.desativar();
        usuarios.saveAndFlush(inativo);

        mvc.perform(get("/api/pessoas").param("termo", "zuleica")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoas[?(@.origem == 'SISTEMA')]", Matchers.empty()));
    }
}
