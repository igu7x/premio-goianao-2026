package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.auth.dto.IdentidadeResposta;
import br.jus.tjgo.goianao.auth.dto.LoginRequisicao;
import br.jus.tjgo.goianao.auth.dto.LoginSenhaRequisicao;
import br.jus.tjgo.goianao.auth.dto.SessaoResposta;
import br.jus.tjgo.goianao.auth.dto.UsuarioMockResposta;
import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.seguranca.JwtService;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityProvider identityProvider;
    private final PapeisResolver papeisResolver;
    private final JwtService jwtService;
    private final LoginPorSenhaService loginPorSenha;

    public AuthController(IdentityProvider identityProvider, PapeisResolver papeisResolver,
                          LoginPorSenhaService loginPorSenha,
                          JwtService jwtService) {
        this.identityProvider = identityProvider;
        this.papeisResolver = papeisResolver;
        this.jwtService = jwtService;
        this.loginPorSenha = loginPorSenha;
    }

    /** Login (mock nesta fase). Devolve o JWT de 8h e a identidade resolvida. */
    @PostMapping("/login")
    public SessaoResposta login(@Valid @RequestBody LoginRequisicao requisicao) {
        IdentidadeAutenticada identidade = identityProvider.autenticar(requisicao.credencial());
        Set<Papel> papeis = papeisResolver.resolver(identidade.cpf());
        UsuarioAutenticado usuario =
                new UsuarioAutenticado(identidade.cpf(), identidade.nome(), papeis);

        return new SessaoResposta(
                jwtService.gerar(usuario),
                jwtService.validade().toSeconds(),
                usuario.cpf(),
                usuario.nome(),
                usuario.papeisComoTexto());
    }


    /**
     * Login por e-mail e senha (cadastro proprio de usuarios).
     *
     * Convive com o login mockado: um serve ao cadastro real, o outro as
     * identidades de teste. Ambos terminam no mesmo JWT, entao nada mais no
     * sistema precisa saber por qual porta a pessoa entrou.
     */
    @PostMapping("/login-senha")
    public SessaoResposta loginPorSenha(@Valid @RequestBody LoginSenhaRequisicao requisicao) {
        IdentidadeAutenticada identidade =
                loginPorSenha.autenticar(requisicao.email(), requisicao.senha());
        return sessaoDe(identidade);
    }

    private SessaoResposta sessaoDe(IdentidadeAutenticada identidade) {
        Set<Papel> papeis = papeisResolver.resolver(identidade.cpf());
        UsuarioAutenticado usuario =
                new UsuarioAutenticado(identidade.cpf(), identidade.nome(), papeis);
        return new SessaoResposta(
                jwtService.gerar(usuario),
                jwtService.validade().toSeconds(),
                usuario.cpf(),
                usuario.nome(),
                usuario.papeisComoTexto());
    }

    /** Identidades de teste para a tela de login enquanto o SSO e mockado. */
    @GetMapping("/usuarios-mock")
    public List<UsuarioMockResposta> usuariosMock() {
        return identityProvider.identidadesDisponiveis().stream()
                .map(i -> new UsuarioMockResposta(
                        i.cpf(),
                        Cpf.formatar(i.cpf()),
                        i.nome(),
                        papeisResolver.resolver(i.cpf()).stream().map(Enum::name).sorted().toList()))
                .toList();
    }

    @GetMapping("/me")
    public IdentidadeResposta me() {
        UsuarioAutenticado usuario = UsuarioAtual.obrigatorio();
        return new IdentidadeResposta(usuario.cpf(), usuario.nome(), usuario.papeisComoTexto());
    }

    /**
     * A sessao e stateless: encerrar significa o cliente descartar o token. O
     * endpoint existe para que o frontend tenha um ponto unico de logout e para
     * que a troca pelo SSO real (que exige revogacao) nao mude o contrato.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
