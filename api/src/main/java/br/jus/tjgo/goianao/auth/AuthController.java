package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.auth.dto.IdentidadeResposta;
import br.jus.tjgo.goianao.auth.dto.LoginRequisicao;
import br.jus.tjgo.goianao.auth.dto.LoginSenhaRequisicao;
import br.jus.tjgo.goianao.auth.dto.SessaoResposta;
import br.jus.tjgo.goianao.auth.dto.UsuarioMockResposta;
import br.jus.tjgo.goianao.auth.sso.SsoProperties;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.config.GoianaoProperties;
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
    private final GoianaoProperties props;
    private final SsoProperties sso;

    public AuthController(IdentityProvider identityProvider, PapeisResolver papeisResolver,
                          LoginPorSenhaService loginPorSenha,
                          JwtService jwtService,
                          GoianaoProperties props,
                          SsoProperties sso) {
        this.identityProvider = identityProvider;
        this.papeisResolver = papeisResolver;
        this.jwtService = jwtService;
        this.loginPorSenha = loginPorSenha;
        this.props = props;
        this.sso = sso;
    }

    /**
     * Porta de entrada desligada responde <b>404</b>, nao 403.
     *
     * <p>403 confirmaria que o endpoint existe e esta apenas fechado, o que
     * convida a insistir. Para quem sondar a API em producao, o login mockado
     * simplesmente nao existe — que e a verdade daquele ambiente.
     */
    private void exigirMockHabilitado() {
        if (!props.login().mock()) {
            throw new NaoEncontradoException("Endereco nao encontrado.");
        }
    }

    private void exigirSenhaHabilitada() {
        if (!props.login().senha()) {
            throw new NaoEncontradoException("Endereco nao encontrado.");
        }
    }

    /** Portas de entrada disponiveis neste ambiente. */
    public record SituacaoLogin(boolean sso, boolean senha, boolean mock) {}

    /**
     * Diz ao frontend quais portas de entrada existem neste ambiente.
     *
     * <p>Publico de proposito: sao as opcoes que a tela de login precisa
     * desenhar antes de qualquer autenticacao, e nao revelam nada que o proprio
     * formulario ja nao revelasse.
     */
    @GetMapping("/situacao")
    public SituacaoLogin situacao() {
        return new SituacaoLogin(sso.habilitado(), props.login().senha(), props.login().mock());
    }

    /**
     * Login por identidade de teste. <b>Nao pede credencial</b> — informado o
     * e-mail, a sessao e emitida.
     *
     * <p>So existe onde {@code goianao.login.mock} estiver ligado, o que
     * significa desenvolvimento. Num ambiente alcancavel de fora ele entrega o
     * sistema a quem quiser: {@link #usuariosMock()} lista as identidades
     * disponiveis e uma delas e administrador.
     */
    @PostMapping("/login")
    public SessaoResposta login(@Valid @RequestBody LoginRequisicao requisicao) {
        exigirMockHabilitado();
        return sessaoDe(identityProvider.autenticar(requisicao.credencial()));
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
        exigirSenhaHabilitada();
        IdentidadeAutenticada identidade =
                loginPorSenha.autenticar(requisicao.email(), requisicao.senha());
        return sessaoDe(identidade);
    }

    private SessaoResposta sessaoDe(IdentidadeAutenticada identidade) {
        Set<Papel> papeis = papeisResolver.resolver(identidade.email());
        UsuarioAutenticado usuario =
                new UsuarioAutenticado(identidade.email(), identidade.nome(), papeis);
        return new SessaoResposta(
                jwtService.gerar(usuario),
                jwtService.validade().toSeconds(),
                usuario.email(),
                usuario.nome(),
                usuario.papeisComoTexto());
    }

    /**
     * Identidades de teste para a tela de login enquanto o SSO e mockado.
     *
     * <p>Segue a mesma trava do {@link #login}: sem ela, este endpoint seria o
     * catalogo de contas que qualquer um poderia assumir.
     */
    @GetMapping("/usuarios-mock")
    public List<UsuarioMockResposta> usuariosMock() {
        exigirMockHabilitado();
        return identityProvider.identidadesDisponiveis().stream()
                .map(i -> new UsuarioMockResposta(
                        i.email(),
                        i.nome(),
                        papeisResolver.resolver(i.email()).stream().map(Enum::name).sorted().toList()))
                .toList();
    }

    @GetMapping("/me")
    public IdentidadeResposta me() {
        UsuarioAutenticado usuario = UsuarioAtual.obrigatorio();
        return new IdentidadeResposta(usuario.email(), usuario.nome(), usuario.papeisComoTexto());
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
