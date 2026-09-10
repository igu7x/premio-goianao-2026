package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolve o **conjunto** de papeis de um e-mail (001/RF-3..RF-6). Papeis
 * acumulam: um administrador que tambem e magistrado reconhecido recebe os dois
 * e ve os menus de ambos, sem "atuar como".
 */
@Service
public class PapeisResolver {

    private final AdministradorRepository administradores;
    private final MagistradoLookup magistrados;
    private final UsuarioRepository usuarios;

    public PapeisResolver(AdministradorRepository administradores, MagistradoLookup magistrados,
                          UsuarioRepository usuarios) {
        this.administradores = administradores;
        this.magistrados = magistrados;
        this.usuarios = usuarios;
    }

    public Set<Papel> resolver(String emailBruto) {
        Set<Papel> papeis = EnumSet.noneOf(Papel.class);
        String email = Email.normalizar(emailBruto);

        if (email != null) {
            // O cadastro de usuarios e a fonte mais recente e a unica que concede
            // SUPERADMIN. Usuario desativado nao contribui papel nenhum: e assim
            // que desativar tira o acesso de fato, e nao so some com ele da lista.
            usuarios.findByEmailIgnoreCase(email)
                    .filter(Usuario::isAtivo)
                    .ifPresent(u -> papeis.addAll(u.getPapeis()));

            // A tabela `administrador` continua valendo: e a origem dos
            // administradores anteriores ao cadastro de usuarios, e da carga de
            // demonstracao.
            if (administradores.existsByEmail(email)) {
                papeis.add(Papel.ADMINISTRADOR);
            }
            if (magistrados.ehMagistradoReconhecido(email)) {
                papeis.add(Papel.MAGISTRADO);
            }
        }
        // SERVIDOR e o papel padrao de quem nao e administrador nem magistrado
        // (001/RF-6).
        if (papeis.isEmpty()) {
            papeis.add(Papel.SERVIDOR);
        }
        return papeis;
    }
}
