package br.jus.tjgo.goianao.seguranca;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Identidade em uso na requisicao: e-mail e nome vem do SSO (ou do login por
 * senha) e os papeis sao resolvidos pelo {@code PapeisResolver}. O e-mail e a
 * chave da pessoa (DI-24) e nunca e digitado pelo emissor (constituicao,
 * principio 3).
 *
 * <p>Desde a feature 011 a identidade carrega tambem a <b>edicao</b> sobre a qual
 * a sessao age. Ela nao e um detalhe de navegacao: cada edicao tem a sua base, e
 * os papeis aqui sao os daquela edicao — a mesma pessoa pode ser magistrada em um
 * ano e apenas servidora em outro (011/RF-8). Trocar de edicao e trocar de sessao,
 * por {@code POST /api/auth/edicao/{id}}.
 */
public record UsuarioAutenticado(String email, String nome, Set<Papel> papeis, Long edicaoId) {

    public boolean tem(Papel papel) {
        return papeis.contains(papel);
    }

    /** SUPERADMIN implica ADMINISTRADOR, como nas authorities abaixo. */
    public boolean ehAdministrador() {
        return tem(Papel.ADMINISTRADOR) || tem(Papel.SUPERADMIN);
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
