package br.jus.tjgo.goianao.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.suporte.TesteDeIntegracao;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * O que a variavel garante e <b>aquele</b> e-mail, e nao "algum
 * superadministrador".
 *
 * <p>Foi o que faltou em producao: a migracao 009 planta um superadmin de
 * bootstrap, a aplicacao o encontrava e ficava calada, e o e-mail corporativo
 * configurado nunca era criado. Como ali o acesso e so pelo SSO — e a conta
 * plantada so tem senha —, ninguem conseguia entrar.
 */
@DisplayName("Superadministrador configurado quando a base ja tem outro")
@TestPropertySource(properties = {
        "goianao.login.senha=false",
        "goianao.login.mock=false",
        "goianao.superadmin.email=chefe.corporativo@tjgo.jus.br",
        "goianao.superadmin.nome=Chefe do Prêmio",
        "goianao.superadmin.senha=",
})
class SuperadminConfiguradoIT extends TesteDeIntegracao {

    private static final String PLANTADO = "bootstrap@exemplo.invalid";
    private static final String CONFIGURADO = "chefe.corporativo@tjgo.jus.br";

    @Autowired private SuperadminInicial superadminInicial;
    @Autowired private UsuarioRepository usuarios;

    @Test
    @DisplayName("cria o e-mail configurado mesmo havendo superadmin plantado na base")
    void criaMesmoComOutroSuperadmin() {
        usuarios.save(new Usuario(PLANTADO, "Superadmin de bootstrap", null,
                EnumSet.of(Papel.SUPERADMIN)));

        superadminInicial.criarSeNecessario();

        Usuario criado = usuarios.findByEmailIgnoreCase(CONFIGURADO).orElseThrow();
        assertThat(criado.getPapeis()).contains(Papel.SUPERADMIN);
        assertThat(criado.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("quem ja existe sem o papel e promovido, e nao ignorado")
    void promoveQuemJaExiste() {
        Usuario semPapel = usuarios.save(new Usuario(CONFIGURADO, "Chefe", null,
                EnumSet.of(Papel.SERVIDOR)));
        semPapel.desativar();
        usuarios.save(semPapel);

        superadminInicial.criarSeNecessario();

        Usuario garantido = usuarios.findByEmailIgnoreCase(CONFIGURADO).orElseThrow();
        assertThat(garantido.getPapeis()).contains(Papel.SUPERADMIN, Papel.SERVIDOR);
        assertThat(garantido.isAtivo()).as("desativado não administraria nada").isTrue();
    }
}
