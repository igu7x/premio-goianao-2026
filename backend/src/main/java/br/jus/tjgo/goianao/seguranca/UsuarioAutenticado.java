package br.jus.tjgo.goianao.seguranca;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Identidade em uso na requisicao: CPF e nome vem do SSO (mockado nesta fase) e
 * os papeis sao resolvidos pelo {@code PapeisResolver}. O CPF nunca e digitado
 * pelo emissor (constituicao, principio 3).
 */
public record UsuarioAutenticado(String cpf, String nome, Set<Papel> papeis) {

    public boolean tem(Papel papel) {
        return papeis.contains(papel);
    }

    public boolean ehAdministrador() {
        return tem(Papel.ADMINISTRADOR);
    }

    public boolean ehSuperadmin() {
        return tem(Papel.SUPERADMIN);
    }

    /**
     * SUPERADMIN implica ADMINISTRADOR.
     *
     * A alternativa seria trocar todo {@code hasRole('ADMINISTRADOR')} por
     * {@code hasAnyRole('ADMINISTRADOR','SUPERADMIN')} — dezenas de pontos, e
     * cada novo endpoint uma chance de esquecer um. Resolvendo aqui, a inclusao
     * vale para o que ja existe e para o que vier.
     */
    public Collection<GrantedAuthority> authorities() {
        Set<Papel> efetivos = EnumSet.copyOf(papeis);
        if (efetivos.contains(Papel.SUPERADMIN)) {
            efetivos.add(Papel.ADMINISTRADOR);
        }
        return efetivos.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.authority()))
                .toList();
    }

    public List<String> papeisComoTexto() {
        return papeis.stream().map(Enum::name).sorted().toList();
    }
}
