package br.jus.tjgo.goianao.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.auth.dto.LoginRequisicao;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Autenticacao e papeis (feature 001)")
class AutenticacaoIT extends TesteDeIntegracao {

    @Test
    @DisplayName("login mock devolve token e identidade, sem senha")
    void login() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequisicao(EMAIL_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(EMAIL_ADMIN))
                .andExpect(jsonPath("$.papeis[0]").value("ADMINISTRADOR"))
                // 8 horas, sem refresh (001/plan).
                .andExpect(jsonPath("$.expiraEmSegundos").value(8 * 3600));
    }

    @Test
    @DisplayName("o login mock normaliza o e-mail: maiusculas e espacos nao mudam a pessoa")
    void loginNormalizaEmail() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequisicao("  Ana.Rebelo@TJGO.example "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_ADMIN))
                .andExpect(jsonPath("$.papeis[0]").value("ADMINISTRADOR"));
    }

    @Test
    @DisplayName("credencial desconhecida nao autentica — nem um CPF, que deixou de identificar")
    void credencialInvalida() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequisicao("ninguem@tjgo.example"))))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequisicao("10120230100"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CA-6: /me reflete e-mail, nome e o conjunto de papeis")
    void identidadeCoerente() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_ADMIN))
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andExpect(jsonPath("$.papeis.length()").value(1));
    }

    @Test
    @DisplayName("CA-2: quem consta como reconhecido passa a ter o papel MAGISTRADO")
    void magistradoVemDoCadastro() {
        assertThat(papeisResolver.resolver(EMAIL_MAGISTRADO)).containsExactly(Papel.SERVIDOR);

        Edicao edicao = novaEdicao(2050);
        cadastrarMagistrado(edicao.getId(), EMAIL_MAGISTRADO, "Rafael", UNIDADE_A, Selo.OURO);

        assertThat(papeisResolver.resolver(EMAIL_MAGISTRADO)).containsExactly(Papel.MAGISTRADO);
        // O e-mail com outra caixa e a mesma pessoa.
        assertThat(papeisResolver.resolver("Rafael.Bittencourt@TJGO.example"))
                .containsExactly(Papel.MAGISTRADO);
    }

    @Test
    @DisplayName("CA-4: admin que tambem e reconhecido acumula os dois papeis")
    void acumulaPapeis() throws Exception {
        Edicao edicao = novaEdicao(2051);
        cadastrarMagistrado(edicao.getId(), EMAIL_ADMIN, "Ana", UNIDADE_A, Selo.PRATA);

        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis.length()").value(2))
                .andExpect(jsonPath("$.papeis[0]").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.papeis[1]").value("MAGISTRADO"));
    }

    @Test
    @DisplayName("CA-5: sem o papel exigido, o acesso e negado")
    void semPapelNaoAcessa() throws Exception {
        mvc.perform(get("/api/unidades/egesp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ESTRANHO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("token adulterado nao autentica")
    void tokenAdulterado() throws Exception {
        String token = token(EMAIL_ADMIN);
        String adulterado = token.substring(0, token.length() - 4) + "AAAA";

        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adulterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout responde 204: a sessao e stateless e o cliente descarta o token")
    void logout() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isNoContent());
    }
}
