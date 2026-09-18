package br.jus.tjgo.goianao.seguranca;

import br.jus.tjgo.goianao.config.GoianaoProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Sessao stateless em JWT. O token carrega e-mail (no subject), nome, os papeis e a edicao
 * sobre a qual a sessao age, tem validade de 8h e **nao ha refresh**: expirado, o usuario faz
 * novo login (001/plan).
 *
 * <p>A edicao esta no token, e nao num cabecalho da requisicao, porque ela decide qual base o
 * sistema inteiro enxerga (011/RF-5). Num cabecalho, trocar de base seria escolha do
 * navegador: bastaria mandar outro numero. Aqui ela so muda por um token novo, emitido depois
 * de o servidor conferir que a pessoa existe naquela edicao.
 */
@Service
public class JwtService {

    /**
     * O valor de {@code goianao.jwt.segredo} que vem no application.yml.
     *
     * Ele existe para o desenvolvimento local funcionar sem configuracao, e
     * esta num repositorio publico — ou seja, e conhecido. Se subir assim fora
     * de desenvolvimento, qualquer pessoa que leia o codigo assina um token
     * valido para qualquer e-mail, inclusive o de um administrador.
     */
    private static final String SEGREDO_DE_DESENVOLVIMENTO =
            "desenvolvimento-goianao-tjgo-chave-local-nao-use-em-producao";

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(GoianaoProperties props, Environment ambiente) {
        String segredo = props.jwt().segredo();
        exigirSegredoProprio(segredo, ambiente);
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofHours(props.jwt().expiracaoHoras());
    }

    /**
     * Recusa subir com o segredo publico fora de desenvolvimento.
     *
     * Falhar no start e melhor que o contrario: uma aplicacao no ar assinando
     * com chave conhecida nao da sinal nenhum de que esta insegura, e o
     * problema so aparece quando alguem ja se passou por outra pessoa. Aqui o
     * pod nao inicia e o log diz exatamente o que definir.
     */
    private static void exigirSegredoProprio(String segredo, Environment ambiente) {
        boolean desenvolvimento = Arrays.stream(ambiente.getActiveProfiles())
                .anyMatch(p -> p.equals("dev") || p.equals("test"));
        if (!desenvolvimento && SEGREDO_DE_DESENVOLVIMENTO.equals(segredo)) {
            throw new IllegalStateException(
                    "GOIANAO_JWT_SEGREDO não foi definido: a aplicação está usando o segredo de "
                    + "desenvolvimento, que é público e permitiria forjar a sessão de qualquer "
                    + "usuário. Defina a variável com pelo menos 32 bytes aleatórios.");
        }
    }

    public String gerar(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.email())
                .claim("nome", usuario.nome())
                .claim("papeis", usuario.papeisComoTexto())
                .claim("edicao", usuario.edicaoId())
                .issuer("goianao-tjgo")
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave)
                .compact();
    }

    public Duration validade() {
        return validade;
    }

    /** Devolve a identidade do token, ou vazio se ele for invalido/expirado. */
    public Optional<UsuarioAutenticado> ler(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            @SuppressWarnings("unchecked")
            List<String> papeis = claims.get("papeis", List.class);
            Set<Papel> conjunto = papeis == null || papeis.isEmpty()
                    ? EnumSet.noneOf(Papel.class)
                    : papeis.stream().map(Papel::valueOf)
                            .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(Papel.class)));

            // Token emitido antes da feature 011 nao tem edicao: a sessao cai na
            // edicao vigente, como quem chega sem token.
            Number edicao = claims.get("edicao", Number.class);

            return Optional.of(new UsuarioAutenticado(
                    claims.getSubject(), claims.get("nome", String.class), conjunto,
                    edicao == null ? null : edicao.longValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
