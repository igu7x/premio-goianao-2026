package br.jus.tjgo.goianao.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.jus.tjgo.goianao.auth.dto.LoginSenhaRequisicao;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.EnumSet;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Entrar e trocar de edicao quando cada uma tem a sua base (011/RF-6 a RF-9).
 *
 * <p>Os cadastros sao montados edicao a edicao, como o superadministrador os
 * montaria: a mesma pessoa aparece em uma, nas duas ou em nenhuma, e com papeis
 * diferentes em cada.
 */
@DisplayName("Acesso por edicao (feature 011)")
class AcessoPorEdicaoIT extends TesteDeIntegracao {

    private static final String EMAIL = "pessoa.edicoes@tjgo.example";
    private static final String SENHA = "senha-de-teste-11";

    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;

    private void cadastrarEm(Edicao edicao, Papel... papeis) {
        naEdicao(edicao, () -> {
            Usuario usuario = new Usuario(EMAIL, "Pessoa das Edicoes", null,
                    EnumSet.of(papeis[0], papeis));
            usuario.definirSenhaHash(encoder.encode(SENHA));
            usuarios.save(usuario);
        });
    }

    private ResultActions entrar() throws Exception {
        return mvc.perform(post("/api/auth/login-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(new LoginSenhaRequisicao(EMAIL, SENHA))));
    }

    private String tokenDe(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsString())
                .get("token").asText();
    }

    @Test
    @DisplayName("CA-4: quem existe nas duas entra na vigente e pode trocar para a outra")
    void entraNaVigenteETroca() throws Exception {
        Edicao anterior = edicaoVigente(2401);
        Edicao vigente = edicaoVigente(2402);
        cadastrarEm(anterior, Papel.SERVIDOR);
        cadastrarEm(vigente, Papel.SERVIDOR);

        ResultActions login = entrar()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edicao.ano").value(2402))
                .andExpect(jsonPath("$.edicoesDisponiveis.length()").value(2))
                .andExpect(jsonPath("$.edicoesDisponiveis[0].ano").value(2402));

        mvc.perform(post("/api/auth/edicao/" + anterior.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edicao.ano").value(2401))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("CA-3: quem so existe na edicao anterior entra nela, mesmo com outra vigente")
    void entraNaAnteriorQuandoSoExisteLa() throws Exception {
        Edicao anterior = edicaoVigente(2403);
        edicaoVigente(2404);
        cadastrarEm(anterior, Papel.MAGISTRADO);

        entrar()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edicao.ano").value(2403))
                .andExpect(jsonPath("$.edicoesDisponiveis.length()").value(1));
    }

    @Test
    @DisplayName("CA-5: os papeis sao os da edicao da sessao, e mudam na troca")
    void papeisMudamComAEdicao() throws Exception {
        Edicao anterior = edicaoVigente(2405);
        Edicao vigente = edicaoVigente(2406);
        cadastrarEm(anterior, Papel.ADMINISTRADOR);
        cadastrarEm(vigente, Papel.SERVIDOR);

        ResultActions login = entrar()
                .andExpect(jsonPath("$.edicao.ano").value(2406))
                .andExpect(jsonPath("$.papeis", Matchers.contains("SERVIDOR")));

        mvc.perform(post("/api/auth/edicao/" + anterior.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis", Matchers.contains("ADMINISTRADOR")));
    }

    @Test
    @DisplayName("trocar para edicao em que a pessoa nao existe e recusado")
    void trocaParaEdicaoAlheiaERecusada() throws Exception {
        Edicao minha = edicaoVigente(2407);
        Edicao alheia = edicaoVigente(2408);
        cadastrarEm(minha, Papel.SERVIDOR);

        ResultActions login = entrar().andExpect(jsonPath("$.edicao.ano").value(2407));

        mvc.perform(post("/api/auth/edicao/" + alheia.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(login)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-7: quem nao existe em edicao nenhuma nao entra")
    void semCadastroNaoEntra() throws Exception {
        edicaoVigente(2409);

        entrar().andExpect(status().isUnauthorized());
    }
}
