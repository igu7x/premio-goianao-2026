package br.jus.tjgo.goianao.auth.sso;

import br.jus.tjgo.goianao.auth.IdentidadeAutenticada;
import br.jus.tjgo.goianao.auth.MontadorDeSessao;
import br.jus.tjgo.goianao.auth.dto.SessaoResposta;
import br.jus.tjgo.goianao.comum.erro.AcessoNegadoException;
import br.jus.tjgo.goianao.edicao.base.EdicaoCorrente;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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

    /**
     * Guarda, entre a ida e a volta do Keycloak, o verifier do PKCE e o
     * aleatorio do state desta tentativa (DI-33). Fica no navegador porque a
     * API nao tem estado compartilhado entre replicas; HttpOnly porque nenhum
     * script precisa le-lo; restrito a {@code /api/auth/sso} porque so o
     * callback o usa. Dez minutos bastam para digitar a senha e o segundo fator.
     */
    static final String COOKIE = "goianao_sso";
    private static final Duration VALIDADE_COOKIE = Duration.ofMinutes(10);

    private final SsoProperties props;
    private final ClienteKeycloak keycloak;
    private final MontadorDeSessao sessoes;
    private final ApplicationEventPublisher eventos;

    public SsoController(SsoProperties props, ClienteKeycloak keycloak,
                         MontadorDeSessao sessoes,
                         ApplicationEventPublisher eventos) {
        this.props = props;
        this.keycloak = keycloak;
        this.sessoes = sessoes;
        this.eventos = eventos;
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
        DesafioPkce pkce = DesafioPkce.novo();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE,
                        cookie(nonceDo(state) + "." + pkce.verifier(), VALIDADE_COOKIE))
                .location(URI.create(keycloak.urlDeAutorizacao(state, pkce.challenge())))
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
            @RequestParam(name = "error_description", required = false) String descricao,
            @CookieValue(name = COOKIE, required = false) String guardado) {

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

        String verifier = verifierDaTentativa(guardado, state);
        if (verifier == null) {
            log.warn("Retorno do SSO sem a tentativa de login correspondente neste navegador.");
            return paraFrontend("erro=" + enc("A tentativa de login expirou ou foi iniciada "
                    + "em outra janela. Clique em entrar de novo."), state);
        }

        try {
            IdentidadeAutenticada identidade = keycloak.autenticar(codigo, verifier);

            // A identidade veio do Keycloak; em qual edicao ela entra e outra
            // pergunta, e quem responde e o cadastro de cada base (011/RF-7).
            SessaoResposta sessao = sessoes.paraEntrada(identidade);

            // O cadastro e atualizado pelo RH depois, fora deste caminho: o
            // login nao espera por sistema de terceiro (010/RNF-2). Vai junto a
            // edicao em que a pessoa entrou — a atualizacao roda em outra thread,
            // e sem isso cairia na base da edicao vigente.
            eventos.publishEvent(new LoginPeloSso(identidade.email(), EdicaoCorrente.schema()));

            return paraFrontend("token=" + enc(sessao.token()), state);
        } catch (AcessoNegadoException e) {
            // Autenticou no tribunal, mas nao tem cadastro em edicao nenhuma.
            return paraFrontend("erro=" + enc(e.getMessage()), state);
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
     * <p>O aleatorio vai tambem no cookie da tentativa, e o callback confere os
     * dois: um codigo que chegue a este navegador sem ter saido dele — o login
     * forjado do CSRF — nao tem o cookie correspondente e e recusado. Era o
     * ponto que esperava "quando houver Redis"; o cookie resolveu sem estado
     * no servidor (DI-33).
     */
    private String novoState(String destino) {
        byte[] ruido = new byte[16];
        ALEATORIO.nextBytes(ruido);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ruido);
        String limpo = (destino == null || !destino.startsWith("/")) ? "/" : destino;
        return nonce + "|" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(limpo.getBytes(StandardCharsets.UTF_8));
    }

    private static String nonceDo(String state) {
        return state.substring(0, state.indexOf('|'));
    }

    /**
     * O verifier guardado, se o cookie for desta tentativa. Sem cookie, ou com
     * cookie de outra tentativa (duas janelas de login, a segunda sobrescreve),
     * nao ha troca possivel: o Keycloak recusaria o verifier errado com uma
     * mensagem que nao ajudaria ninguem.
     */
    private static String verifierDaTentativa(String guardado, String state) {
        if (guardado == null || state == null || !state.contains("|")
                || !guardado.contains(".")) {
            return null;
        }
        String nonce = guardado.substring(0, guardado.indexOf('.'));
        String verifier = guardado.substring(guardado.indexOf('.') + 1);
        return nonce.equals(nonceDo(state)) && !verifier.isBlank() ? verifier : null;
    }

    /**
     * Secure quando o callback e HTTPS. Nao da para perguntar a requisicao:
     * no OpenShift o TLS termina na Route, e a API recebe HTTP.
     */
    private String cookie(String valor, Duration validade) {
        boolean https = props.redirectUri() != null && props.redirectUri().startsWith("https://");
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(https)
                .sameSite("Lax")
                .path("/api/auth/sso")
                .maxAge(validade)
                .build()
                .toString();
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
        // A tentativa termina aqui, com sucesso ou nao: o cookie sai junto.
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
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
