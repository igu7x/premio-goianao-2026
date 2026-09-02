package br.jus.tjgo.goianao.seguranca;

import br.jus.tjgo.goianao.config.GoianaoProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Sessao stateless em JWT. O token carrega CPF, nome e os papeis, tem validade
 * de 8h e **nao ha refresh**: expirado, o usuario faz novo login (001/plan).
 */
@Service
public class JwtService {

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(GoianaoProperties props) {
        this.chave = Keys.hmacShaKeyFor(props.jwt().segredo().getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofHours(props.jwt().expiracaoHoras());
    }

    public String gerar(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.cpf())
                .claim("nome", usuario.nome())
                .claim("papeis", usuario.papeisComoTexto())
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

            return Optional.of(new UsuarioAutenticado(
                    claims.getSubject(), claims.get("nome", String.class), conjunto));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
