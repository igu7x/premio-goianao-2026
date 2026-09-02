package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.seguranca.Papel;
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

    public PapeisResolver(AdministradorRepository administradores, MagistradoLookup magistrados) {
        this.administradores = administradores;
        this.magistrados = magistrados;
    }

    public Set<Papel> resolver(String cpf) {
        Set<Papel> papeis = EnumSet.noneOf(Papel.class);

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
