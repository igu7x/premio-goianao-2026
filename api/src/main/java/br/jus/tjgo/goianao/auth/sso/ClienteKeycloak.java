package br.jus.tjgo.goianao.auth.sso;

import br.jus.tjgo.goianao.auth.IdentidadeAutenticada;
import br.jus.tjgo.goianao.comum.Cpf;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Conversa com o Keycloak: troca o code por token e valida o {@code id_token}.
 *
 * <p>A validacao e feita pelo {@link NimbusJwtDecoder}, que busca as chaves
 * publicas no JWKS do realm e confere <b>assinatura, emissor e expiracao</b>.
 * Isso e deliberado: a implementacao do sistema irmao decodifica o payload em
 * base64 e confia nele, o que permite forjar a sessao de qualquer usuario. Um
 * token so vale depois de verificado criptograficamente.
 */
@Component
public class ClienteKeycloak {

    private static final Logger log = LoggerFactory.getLogger(ClienteKeycloak.class);

    private final SsoProperties props;
    private final RestClient http;
    private volatile JwtDecoder decoder;

    public ClienteKeycloak(SsoProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.build();
    }

    /** URL para onde o navegador e redirecionado, com o {@code state} opaco. */
    public String urlDeAutorizacao(String state) {
        return props.urlAutorizacao()
                + "?client_id=" + enc(props.clientId())
                + "&redirect_uri=" + enc(props.redirectUri())
                + "&response_type=code"
                + "&scope=openid"
                + "&state=" + enc(state);
    }

    /**
     * Troca o codigo de autorizacao pelos tokens e devolve a identidade ja
     * verificada.
     *
     * @throws SsoException quando o Keycloak recusa o codigo, quando o token
     *                      nao passa na verificacao ou quando o CPF nao vem
     *                      em nenhum dos claims configurados
     */
    public IdentidadeAutenticada autenticar(String codigo) {
        Map<String, Object> resposta = trocarCodigo(codigo);

        Object bruto = resposta.get("id_token");
        if (bruto == null) {
            // Sem id_token o realm nao devolveu o escopo openid; e configuracao
            // do client, nao erro de quem esta entrando.
            throw new SsoException("O Keycloak não devolveu id_token. Verifique o escopo "
                    + "'openid' na configuração do client.");
        }

        Jwt token = verificar(bruto.toString());
        return identidadeDe(token);
    }

    private Map<String, Object> trocarCodigo(String codigo) {
        MultiValueMap<String, String> corpo = new LinkedMultiValueMap<>();
        corpo.add("grant_type", "authorization_code");
        corpo.add("code", codigo);
        corpo.add("redirect_uri", props.redirectUri());
        corpo.add("client_id", props.clientId());
        corpo.add("client_secret", props.clientSecret());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resposta = http.post()
                    .uri(props.urlToken())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(corpo)
                    .retrieve()
                    .body(Map.class);
            if (resposta == null) {
                throw new SsoException("Resposta vazia do Keycloak na troca do código.");
            }
            return resposta;
        } catch (SsoException e) {
            throw e;
        } catch (RuntimeException e) {
            // A mensagem do Keycloak costuma citar o client_id e o redirect_uri;
            // fica no log, nao na tela.
            log.warn("Falha ao trocar o código de autorização no Keycloak", e);
            throw new SsoException("Não foi possível concluir o login corporativo.");
        }
    }

    private Jwt verificar(String idToken) {
        try {
            return decoder().decode(idToken);
        } catch (JwtException e) {
            log.warn("id_token recusado na verificação", e);
            throw new SsoException("O token recebido do Keycloak não é válido.");
        }
    }

    /**
     * Criado sob demanda: no start o Keycloak pode nao estar acessivel, e a
     * aplicacao nao deve deixar de subir por isso. O Nimbus mantem cache das
     * chaves internamente.
     */
    private JwtDecoder decoder() {
        JwtDecoder atual = decoder;
        if (atual == null) {
            synchronized (this) {
                if (decoder == null) {
                    decoder = NimbusJwtDecoder.withJwkSetUri(props.urlJwks()).build();
                }
                atual = decoder;
            }
        }
        return atual;
    }

    /**
     * Extrai CPF e nome dos claims.
     *
     * O CPF e procurado nos claims configurados, em ordem, porque o nome dele
     * depende do mapper do client e ainda nao foi confirmado pela infra. O
     * valor e normalizado: o mapper pode entregar com pontuacao.
     */
    private IdentidadeAutenticada identidadeDe(Jwt token) {
        for (String claim : props.claimsCpf()) {
            Object valor = token.getClaim(claim);
            if (valor == null) {
                continue;
            }
            String cpf = Cpf.normalizar(valor.toString());
            if (Cpf.valido(cpf)) {
                // O NOME do claim, nunca o valor: CPF e dado pessoal e log de
                // aplicacao costuma sair do perimetro (coletor, indice, backup).
                // Saber qual claim funcionou responde, no primeiro login real, a
                // pergunta que a infra nao soube responder -- e permite fixar a
                // lista em OPENSHIFT_SSO_CLAIMS_CPF em vez de tentar varios.
                log.info("CPF obtido do claim \"{}\".", claim);
                Object nome = token.getClaim(props.claimNome());
                return new IdentidadeAutenticada(
                        cpf, nome != null ? nome.toString() : cpf);
            }
        }

        // Mensagem util no diagnostico: diz onde procuramos e o que veio.
        log.error("CPF ausente no id_token. Claims procurados: {}. Claims presentes: {}",
                props.claimsCpf(), token.getClaims().keySet());
        throw new SsoException("O login corporativo não informou o CPF. "
                + "Peça à equipe do SSO para incluir o CPF nos dados do usuário.");
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
