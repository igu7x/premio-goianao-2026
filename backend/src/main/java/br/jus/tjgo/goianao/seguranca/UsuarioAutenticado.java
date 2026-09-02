package br.jus.tjgo.goianao.seguranca;

import java.util.Collection;
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

    public Collection<GrantedAuthority> authorities() {
        return papeis.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.authority()))
                .toList();
    }

    public List<String> papeisComoTexto() {
        return papeis.stream().map(Enum::name).sorted().toList();
    }
}
