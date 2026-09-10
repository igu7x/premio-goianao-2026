package br.jus.tjgo.goianao.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.auth.dto.LoginSenhaRequisicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.usuario.dto.UsuarioRequisicao;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Cadastro de usuarios e superadministrador")
class UsuarioIT extends TesteDeIntegracao {

    /** CPF ficticio; desde a DI-24 so informativo, e pode repetir entre usuarios. */
    private static final String CPF_NOVO = "20450670252";

    @Autowired private UsuarioRepository usuarios;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder encoder;

    /** Superadmin de teste, identificado pelo e-mail com que entra. */
    private void darSuperadminAo(String email, String senha) {
        Usuario u = new Usuario(email, "Super de Teste", null,
                Set.of(Papel.SUPERADMIN, Papel.ADMINISTRADOR));
        u.definirSenhaHash(encoder.encode(senha));
        usuarios.save(u);
    }

    private UsuarioRequisicao requisicao(String email, String cpf) {
        return new UsuarioRequisicao(email, "Servidor de Teste", cpf, "1ª Vara Cível", null,
                Set.of(Papel.SERVIDOR), null);
    }

    @Test
    @DisplayName("apenas superadministrador acessa o cadastro de usuarios")
    void somenteSuperadmin() throws Exception {
        // EMAIL_ADMIN é administrador, mas não superadministrador.
        mvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cadastra usuario e o CPF sai mascarado, sem qualquer vestigio da senha")
    void cadastra() throws Exception {
        darSuperadminAo("super1@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer("super1@tjgo.jus.br"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new UsuarioRequisicao(
                                "Juiz@TJGO.jus.br", "Juiz de Teste", CPF_NOVO,
                                "1ª Vara Cível", "Cível",
                                Set.of(Papel.MAGISTRADO), "senha-do-juiz"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Juiz de Teste"))
                // O e-mail e a chave e sai normalizado (DI-24).
                .andExpect(jsonPath("$.email").value("juiz@tjgo.jus.br"))
                .andExpect(jsonPath("$.areaAtuacao").value("Cível"))
                .andExpect(jsonPath("$.temSenha").value(true))
                // Nem o CPF inteiro nem o hash aparecem na resposta.
                .andExpect(jsonPath("$.cpfMascarado").value("***.506.702-**"))
                .andExpect(jsonPath("$.cpfMascarado").value(
                        Matchers.not(Matchers.containsString(CPF_NOVO))))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    @DisplayName("o CPF e opcional: sem ele o usuario entra e a mascara vem vazia")
    void cpfOpcional() throws Exception {
        darSuperadminAo("super8@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer("super8@tjgo.jus.br"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(requisicao("sem.cpf@tjgo.jus.br", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpfMascarado").doesNotExist());

        // Preenchido, porem, precisa ser valido.
        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer("super8@tjgo.jus.br"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(requisicao("cpf.ruim@tjgo.jus.br", "11111111111"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("CPF inválido."));
    }

    @Test
    @DisplayName("o e-mail nao repete, em nenhuma caixa; o CPF pode repetir")
    void unicidadePeloEmail() throws Exception {
        darSuperadminAo("super9@tjgo.jus.br", "uma-senha-boa");
        String bearer = bearer("super9@tjgo.jus.br");

        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(requisicao("fulano@tjgo.example", CPF_NOVO))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(requisicao("Fulano@TJGO.example", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Já existe usuário com este e-mail."));

        // Mesmo CPF, outra pessoa: o CPF nao identifica ninguem.
        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(requisicao("ciclano@tjgo.example", CPF_NOVO))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("alterar nao troca o e-mail, e CPF em branco mantem o atual")
    void alterarPreservaEmailECpf() throws Exception {
        darSuperadminAo("super10@tjgo.jus.br", "uma-senha-boa");
        Long id = usuarios.save(new Usuario("alvo@tjgo.example", "Alvo", CPF_NOVO,
                Set.of(Papel.SERVIDOR))).getId();

        // O e-mail vai no corpo de proposito: o contrato nao tem esse campo, e
        // ele precisa ser ignorado.
        Map<String, Object> corpo = Map.of(
                "email", "outro@tjgo.example",
                "nome", "Alvo Renomeado",
                "cpf", "",
                "papeis", List.of("SERVIDOR"));

        mvc.perform(put("/api/usuarios/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer("super10@tjgo.jus.br"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Alvo Renomeado"))
                .andExpect(jsonPath("$.email").value("alvo@tjgo.example"))
                .andExpect(jsonPath("$.cpfMascarado").value("***.506.702-**"));
    }

    @Test
    @DisplayName("area de atuacao so e guardada para magistrado")
    void areaSoParaMagistrado() throws Exception {
        darSuperadminAo("super2@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer("super2@tjgo.jus.br"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new UsuarioRequisicao(
                                "serv@tjgo.jus.br", "Servidor de Teste", null,
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
        darSuperadminAo("super3@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginSenhaRequisicao(
                                "SUPER3@TJGO.JUS.BR", "uma-senha-boa"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("super3@tjgo.jus.br"))
                .andExpect(jsonPath("$.papeis", Matchers.hasItem("SUPERADMIN")));
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente devolvem a mesma resposta")
    void naoRevelaQuaisContasExistem() throws Exception {
        darSuperadminAo("super4@tjgo.jus.br", "uma-senha-boa");

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
        darSuperadminAo("super5@tjgo.jus.br", "uma-senha-boa");
        usuarios.save(new Usuario("futuro@tjgo.jus.br", "Futuro Super", null,
                Set.of(Papel.ADMINISTRADOR)));

        mvc.perform(post("/api/usuarios/superadmins")
                        .header(HttpHeaders.AUTHORIZATION, bearer("super5@tjgo.jus.br"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Futuro@TJGO.jus.br\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis", Matchers.hasItem("SUPERADMIN")));

        mvc.perform(get("/api/usuarios/superadmins")
                        .header(HttpHeaders.AUTHORIZATION, bearer("super5@tjgo.jus.br")))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("promover e-mail inexistente explica que e preciso cadastrar antes")
    void promoverDesconhecido() throws Exception {
        darSuperadminAo("super6@tjgo.jus.br", "uma-senha-boa");

        mvc.perform(post("/api/usuarios/superadmins")
                        .header(HttpHeaders.AUTHORIZATION, bearer("super6@tjgo.jus.br"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@tjgo.jus.br\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("Cadastre-o primeiro")));
    }

    @Test
    @DisplayName("o sistema nunca fica sem superadministrador")
    void naoDeixaZerarSuperadmins() throws Exception {
        darSuperadminAo("super7@tjgo.jus.br", "uma-senha-boa");
        Long id = usuarios.findByEmailIgnoreCase("super7@tjgo.jus.br").orElseThrow().getId();

        mvc.perform(delete("/api/usuarios/superadmins/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer("super7@tjgo.jus.br")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("único superadministrador")));
    }
}
