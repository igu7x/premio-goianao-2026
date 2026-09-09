package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolve o **conjunto** de papeis de um CPF (001/RF-3..RF-6). Papeis acumulam:
 * um administrador que tambem e magistrado reconhecido recebe os dois e ve os
 * menus de ambos, sem "atuar como".
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

    public Set<Papel> resolver(String cpf) {
        Set<Papel> papeis = EnumSet.noneOf(Papel.class);

        // O cadastro de usuarios e a fonte mais recente e a unica que concede
        // SUPERADMIN. Usuario desativado nao contribui papel nenhum: e assim que
        // desativar tira o acesso de fato, e nao so some com ele da lista.
        usuarios.findByCpf(cpf)
                .filter(Usuario::isAtivo)
                .ifPresent(u -> papeis.addAll(u.getPapeis()));

        // A tabela `administrador` continua valendo: e a origem dos
        // administradores anteriores ao cadastro de usuarios, e da carga de
        // demonstracao.
        if (administradores.existsByCpf(cpf)) {
            papeis.add(Papel.ADMINISTRADOR);
        }
        if (magistrados.ehMagistradoReconhecido(cpf)) {
            papeis.add(Papel.MAGISTRADO);
        }
        // SERVIDOR e o papel padrao de quem nao e administrador nem magistrado
        // (001/RF-6).
        if (papeis.isEmpty()) {
            papeis.add(Papel.SERVIDOR);
        }
        return papeis;
    }
}
