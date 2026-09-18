package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.edicao.base.EdicaoCorrente;
import br.jus.tjgo.goianao.seguranca.Papel;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Leva os superadministradores para a base de uma edicao nova (011/RF-3).
 *
 * <p>E a unica excecao a regra de que uma edicao nasce vazia, e existe por um
 * motivo pratico: uma base sem superadministrador nao pode ser administrada por
 * ninguem. Ela nasceria morta — sem quem cadastrasse o primeiro usuario, sem
 * quem sincronizasse as unidades, sem quem publicasse a edicao.
 *
 * <p>O que vai junto e o minimo para destravar: e-mail, nome, CPF e o hash da
 * senha, para que quem administrava o ano anterior entre no ano novo com a mesma
 * credencial. <b>Nao</b> vao os demais papeis daquela pessoa — se ela era
 * tambem magistrada reconhecida em 2026, isso e resultado do premio de 2026 e
 * nao se herda. O papel concedido e SUPERADMIN, que ja implica ADMINISTRADOR
 * nas permissoes.
 */
@Service
public class SuperadminsDaEdicao {

    private static final Logger log = LoggerFactory.getLogger(SuperadminsDaEdicao.class);

    private final UsuarioRepository usuarios;

    public SuperadminsDaEdicao(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }


    public int semear(String schemaOrigem, String schemaDestino) {
        if (schemaOrigem == null || schemaOrigem.equals(schemaDestino)) {
            return 0;
        }

        List<SementeDeSuperadmin> sementes = EdicaoCorrente.executarEm(schemaOrigem,
                () -> usuarios.superadminsAtivos(Papel.SUPERADMIN));

        int criados = EdicaoCorrente.executarEm(schemaDestino, () -> {
            int total = 0;
            for (SementeDeSuperadmin semente : sementes) {
                if (usuarios.existsByEmailIgnoreCase(semente.email())) {
                    continue;
                }
                Usuario copia = new Usuario(semente.email(), semente.nome(), semente.cpf(),
                        EnumSet.of(Papel.SUPERADMIN));
                copia.definirSenhaHash(semente.senhaHash());
                usuarios.save(copia);
                total++;
            }
            return total;
        });

        log.info("{} superadministrador(es) levados para {}.", criados, schemaDestino);
        return criados;
    }
}
