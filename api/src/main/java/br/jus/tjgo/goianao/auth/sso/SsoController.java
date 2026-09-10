package br.jus.tjgo.goianao.auth.sso;

import br.jus.tjgo.goianao.auth.IdentidadeAutenticada;
import br.jus.tjgo.goianao.auth.PapeisResolver;
import br.jus.tjgo.goianao.seguranca.JwtService;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fluxo OIDC de codigo de autorizacao contra o Keycloak do TJGO.
 *
 * <p>O backend redireciona e o frontend so navega — o navegador nunca vê
 * client_secret. Ao final, o Keycloak deixa de existir para o resto do sistema:
 * a identidade verificada vira o mesmo JWT de 8h que o login mockado emite, e
 * nenhum controller precisa saber por qual porta o usuario entrou.
 *
 * <p><b>O token volta ao frontend no fragmento da URL</b>
 * ({@code /entrar#token=...}), nao na query string. Fragmento nao e enviado ao
 * servidor: nao entra em log de proxy, nem no Referer, nem no HAProxy — que no
 * sistema irmao devolveu 502 quando o payload da query passou de 80 KB.
 */
@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SsoProperties props;
    private final ClienteKeycloak keycloak;
    private final PapeisResolver papeisResolver;
    private final JwtService jwtService;

    public SsoController(SsoProperties props, ClienteKeycloak keycloak,
                         PapeisResolver papeisResolver, JwtService jwtService) {
        this.props = props;
        this.keycloak = keycloak;
        this.papeisResolver = papeisResolver;
        this.jwtService = jwtService;
    }

    /** Diz ao frontend se deve mostrar o botao de SSO ou a lista mockada. */
    @GetMapping("/situacao")
    public SituacaoSso situacao() {
        return new SituacaoSso(props.habilitado());
    }

    /** Manda o navegador ao Keycloak. */
    @GetMapping("/login")
    public ResponseEntity<Void> login(
            @RequestParam(name = "destino", required = false) String destino) {

        if (!props.habilitado()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        String state = novoState(destino);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(keycloak.urlDeAutorizacao(state)))
                .build();
    }

    /**
     * Retorno do Keycloak.
     *
     * Sempre redireciona de volta ao frontend — inclusive no erro. Uma tela de
     * login que recebe a mensagem e melhor que um JSON de erro cru no meio de
     * um fluxo de navegacao.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String codigo,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String erro,
            @RequestParam(name = "error_description", required = false) String descricao) {

        if (!props.habilitado()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (erro != null) {
            log.warn("Keycloak recusou a autenticação: {} — {}", erro, descricao);
            return paraFrontend("erro=" + enc("O login corporativo foi recusado."), state);
        }
        if (codigo == null || codigo.isBlank()) {
            return paraFrontend("erro=" + enc("Retorno do login sem código de autorização."), state);
        }

        try {
            IdentidadeAutenticada identidade = keycloak.autenticar(codigo);
            Set<Papel> papeis = papeisResolver.resolver(identidade.email());
            UsuarioAutenticado usuario =
                    new UsuarioAutenticado(identidade.email(), identidade.nome(), papeis);

            return paraFrontend("token=" + enc(jwtService.gerar(usuario)), state);
        } catch (SsoException e) {
            return paraFrontend("erro=" + enc(e.getMessage()), state);
        }
    }

    /**
     * URL de encerramento no Keycloak, para o frontend redirecionar depois de
     * descartar o token local.
     *
     * <p>Manda {@code post_logout_redirect_uri} <b>e</b> o antigo
     * {@code redirect_uri}: o Keycloak do TJGO e anterior a versao 19, quando o
     * parametro mudou de nome, e enviar os dois faz a mesma chamada servir as
     * duas versoes — inclusive depois de uma atualizacao do servidor.
     */
    @GetMapping("/logout-url")
    public SaidaSso urlDeLogout() {
        if (!props.habilitado()) {
            return new SaidaSso(null);
        }
        String destino = props.urlFrontend() == null ? "" : props.urlFrontend();
        return new SaidaSso(props.urlLogout()
                + "?post_logout_redirect_uri=" + enc(destino)
                + "&redirect_uri=" + enc(destino)
                + "&client_id=" + enc(props.clientId()));
    }

    /**
     * O {@code state} carrega o destino pos-login e um valor aleatorio.
     *
     * O aleatorio existe para o parametro nao ser previsivel; a validacao
     * completa contra CSRF exige guardar o state entre as duas requisicoes, o
     * que num sistema de varias replicas pede estado compartilhado. Como o
     * callback so produz um token para uma identidade que o proprio Keycloak
     * assinou, a janela e estreita — mas fica registrado como ponto a fechar
     * quando houver Redis.
     */
    private String novoState(String destino) {
        byte[] ruido = new byte[16];
        ALEATORIO.nextBytes(ruido);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ruido);
        String limpo = (destino == null || !destino.startsWith("/")) ? "/" : destino;
        return nonce + "|" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(limpo.getBytes(StandardCharsets.UTF_8));
    }

    private String destinoDoState(String state) {
        if (state == null || !state.contains("|")) {
            return "/";
        }
        try {
            String parte = state.substring(state.indexOf('|') + 1);
            String destino = new String(
                    Base64.getUrlDecoder().decode(parte), StandardCharsets.UTF_8);
            // So caminho interno: state vem do navegador e nao e confiavel.
            return destino.startsWith("/") && !destino.startsWith("//") ? destino : "/";
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    private ResponseEntity<Void> paraFrontend(String fragmento, String state) {
        String base = props.urlFrontend() == null ? "" : props.urlFrontend();
        String destino = destinoDoState(state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(base + "/entrar#" + fragmento
                        + "&destino=" + enc(destino)))
                .build();
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    public record SituacaoSso(boolean habilitado) {}

    public record SaidaSso(String url) {}
}
