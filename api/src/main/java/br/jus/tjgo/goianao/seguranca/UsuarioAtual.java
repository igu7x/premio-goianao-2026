package br.jus.tjgo.goianao.seguranca;

import br.jus.tjgo.goianao.comum.erro.AcessoNegadoException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acesso a identidade da requisicao. Servicos usam este ponto — nunca um e-mail
 * vindo do corpo/query — para decidir o que o usuario pode emitir (005/RNF-1,
 * 006/RNF-1).
 */
public final class UsuarioAtual {

    private UsuarioAtual() {}

    public static Optional<UsuarioAutenticado> opcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public static UsuarioAutenticado obrigatorio() {
        return opcional().orElseThrow(
                () -> new AcessoNegadoException("Nenhum usuário autenticado na requisição."));
    }

    /** E-mail do autor da operacao, para colunas de auditoria. */
    public static String emailOuSistema() {
        return opcional().map(UsuarioAutenticado::email).orElse(null);
    }
}
