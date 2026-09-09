package br.jus.tjgo.goianao.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.auth.dto.LoginSenhaRequisicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.usuario.dto.UsuarioRequisicao;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Cadastro de usuarios e superadministrador")
class UsuarioIT extends TesteDeIntegracao {

    private static final String CPF_SUPER = "10120230100";
    private static final String CPF_NOVO = "20450670252";

    @Autowired private UsuarioRepository usuarios;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder encoder;

    /** O superadmin de teste: mesmo CPF do administrador padrao da base. */
    private void darSuperadminAo(String cpf, String email, String senha) {
        Usuario u = new Usuario(cpf, "Super de Teste", email,
                Set.of(Papel.SUPERADMIN, Papel.ADMINISTRADOR));
        u.definirSenhaHash(encoder.encode(senha));
        usuarios.save(u);
    }

    @Test
    @DisplayName("apenas superadministrador acessa o cadastro de usuarios")
    void somenteSuperadmin() throws Exception {
        // CPF_ADMIN é administrador, mas não superadministrador.
        mvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cadastra usuario e o CPF sai mascarado, sem qualquer vestigio da senha")
    void cadastra() throws Exception {
        darSuperadminAo(CPF_SUPER, "super1@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new UsuarioRequisicao(
                                CPF_NOVO, "Juiz de Teste", "juiz@tjgo.jus.br",
                                "1ª Vara Cível", "Cível",
                                Set.of(Papel.MAGISTRADO), "senha-do-juiz"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Juiz de Teste"))
                .andExpect(jsonPath("$.areaAtuacao").value("Cível"))
                .andExpect(jsonPath("$.temSenha").value(true))
                // Nem o CPF inteiro nem o hash aparecem na resposta.
                .andExpect(jsonPath("$.cpfMascarado").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(CPF_NOVO))))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    @DisplayName("area de atuacao so e guardada para magistrado")
    void areaSoParaMagistrado() throws Exception {
        darSuperadminAo(CPF_SUPER, "super2@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new UsuarioRequisicao(
                                CPF_NOVO, "Servidor de Teste", "serv@tjgo.jus.br",
                                "1ª Vara Cível", "Cível",
                                Set.of(Papel.SERVIDOR), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areaAtuacao").doesNotExist())
                // Sem senha, o usuário existe — mas só entrará pelo SSO.
                .andExpect(jsonPath("$.temSenha").value(false));
    }

    @Test
    @DisplayName("login por e-mail e senha devolve o token e os papeis")
    void loginPorSenha() throws Exception {
        darSuperadminAo(CPF_SUPER, "super3@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginSenhaRequisicao(
                                "SUPER3@TJGO.JUS.BR", "uma-senha-boa"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.papeis", org.hamcrest.Matchers.hasItem("SUPERADMIN")));
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente devolvem a mesma resposta")
    void naoRevelaQuaisContasExistem() throws Exception {
        darSuperadminAo(CPF_SUPER, "super4@tjgo.jus.br", "uma-senha-boa");

        String mensagemSenhaErrada = mvc.perform(post("/api/auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginSenhaRequisicao(
                                "super4@tjgo.jus.br", "senha-errada"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String mensagemInexistente = mvc.perform(post("/api/auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginSenhaRequisicao(
                                "ninguem@tjgo.jus.br", "senha-errada"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(mensagemSenhaErrada)
                .contains("E-mail ou senha inválidos")
                .isEqualTo(trocarMomento(mensagemInexistente, mensagemSenhaErrada));
    }

    /** As respostas só diferem no carimbo de tempo; o resto tem de ser igual. */
    private String trocarMomento(String origem, String modelo) {
        return origem.replaceAll("\"momento\":\"[^\"]+\"",
                modelo.replaceAll(".*(\"momento\":\"[^\"]+\").*", "$1"));
    }

    @Test
    @DisplayName("promove por e-mail um usuario que ja existe")
    void promovePorEmail() throws Exception {
        darSuperadminAo(CPF_SUPER, "super5@tjgo.jus.br", "uma-senha-boa");
        usuarios.save(new Usuario(CPF_NOVO, "Futuro Super", "futuro@tjgo.jus.br",
                Set.of(Papel.ADMINISTRADOR)));

        mvc.perform(post("/api/usuarios/superadmins")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"futuro@tjgo.jus.br\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis", org.hamcrest.Matchers.hasItem("SUPERADMIN")));

        mvc.perform(get("/api/usuarios/superadmins")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_SUPER)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("promover e-mail inexistente explica que e preciso cadastrar antes")
    void promoverDesconhecido() throws Exception {
        darSuperadminAo(CPF_SUPER, "super6@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/usuarios/superadmins")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_SUPER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@tjgo.jus.br\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("Cadastre-o primeiro")));
    }

    @Test
    @DisplayName("o sistema nunca fica sem superadministrador")
    void naoDeixaZerarSuperadmins() throws Exception {
        darSuperadminAo(CPF_SUPER, "super7@tjgo.jus.br", "uma-senha-boa");
        Long id = usuarios.findByCpf(CPF_SUPER).orElseThrow().getId();

        mvc.perform(delete("/api/usuarios/superadmins/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(CPF_SUPER)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString("único superadministrador")));
    }
}
