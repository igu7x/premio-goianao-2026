package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.comum.erro.CredenciaisInvalidasException;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login por e-mail e senha.
 *
 * <p>Existe enquanto o SSO nao assume: e o que permite ao superadministrador
 * entrar e cadastrar os demais. Quando o Keycloak entrar, este caminho pode ser
 * desligado sem tocar no resto — a sessao resultante e o mesmo JWT.
 */
@Service
public class LoginPorSenhaService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public LoginPorSenhaService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    /**
     * Autentica e devolve a identidade.
     *
     * <p>A mensagem de erro e <b>a mesma</b> para e-mail inexistente, senha
     * errada e usuario desativado. Distinguir informaria a quem tenta adivinhar
     * quais e-mails existem no sistema.
     *
     * <p>Quando o e-mail nao existe, ainda assim se gasta o tempo de um BCrypt
     * contra um hash descartavel: sem isso, a diferenca de tempo de resposta
     * denuncia quais contas existem.
     */
    @Transactional(readOnly = true)
    public IdentidadeAutenticada autenticar(String email, String senha) {
        Usuario usuario = usuarios
                .findByEmailIgnoreCase(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElse(null);

        if (usuario == null || usuario.getSenhaHash() == null) {
            encoder.matches(senha == null ? "" : senha, HASH_DESCARTAVEL);
            throw recusar();
        }
        if (!usuario.isAtivo() || !encoder.matches(senha == null ? "" : senha,
                usuario.getSenhaHash())) {
            throw recusar();
        }
        return new IdentidadeAutenticada(usuario.getEmail(), usuario.getNome());
    }

    private CredenciaisInvalidasException recusar() {
        return new CredenciaisInvalidasException("E-mail ou senha inválidos.");
    }

    /** Hash de uma senha aleatoria, só para consumir o tempo do BCrypt. */
    private static final String HASH_DESCARTAVEL =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
}
