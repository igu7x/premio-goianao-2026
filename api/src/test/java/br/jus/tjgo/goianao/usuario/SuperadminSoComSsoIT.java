package br.jus.tjgo.goianao.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Producao entra so pelo SSO, e la a senha do superadministrador inicial nao
 * teria uso: a pessoa e autenticada pelo tribunal, e o cadastro serve para
 * dizer que aquele e-mail administra o sistema.
 *
 * <p>Exigir a senha nesse ambiente obrigaria a inventar uma que ninguem usaria
 * — e que ficaria gravada como hash no banco de producao sem motivo.
 */
@DisplayName("Superadministrador inicial onde so ha SSO")
@TestPropertySource(properties = {
        "goianao.login.senha=false",
        "goianao.login.mock=false",
        "goianao.superadmin.email=chefe@tjgo.jus.br",
        "goianao.superadmin.nome=Chefe do Prêmio",
        "goianao.superadmin.senha=",
})
class SuperadminSoComSsoIT extends TesteDeIntegracao {

    @Autowired private SuperadminInicial superadminInicial;
    @Autowired private UsuarioRepository usuarios;

    @Test
    @DisplayName("e criado sem senha, com o papel que da acesso ao cadastro")
    void criaSemSenha() {
        superadminInicial.criarSeNecessario();

        Usuario criado = usuarios.findByEmailIgnoreCase("chefe@tjgo.jus.br").orElseThrow();
        assertThat(criado.getSenhaHash()).as("sem senha: o acesso é pelo SSO").isNull();
        assertThat(criado.getPapeis()).contains(Papel.SUPERADMIN);
        assertThat(criado.isAtivo()).isTrue();
    }
}
