package br.jus.tjgo.goianao.seguranca;

import br.jus.tjgo.goianao.comum.erro.ErroResposta;
import br.jus.tjgo.goianao.config.GoianaoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Toda decisao de autorizacao acontece aqui e nos servicos — o frontend apenas
 * reflete o que o usuario pode ver (001/RNF-2).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final FiltroJwt filtroJwt;
    private final GoianaoProperties props;
    private final ObjectMapper objectMapper;
    private final boolean consoleH2Ligado;

    public SecurityConfig(FiltroJwt filtroJwt, GoianaoProperties props, ObjectMapper objectMapper,
                          @Value("${spring.h2.console.enabled:false}") boolean consoleH2Ligado) {
        this.filtroJwt = filtroJwt;
        this.props = props;
        this.objectMapper = objectMapper;
        this.consoleH2Ligado = consoleH2Ligado;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> {
                // Login mock e verificacao publica de certificado (007/RNF-1).
                reg.requestMatchers("/api/auth/login", "/api/auth/usuarios-mock").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll();

                // O console do H2 so e liberado quando esta de fato ligado (perfil
                // dev). Sem essa condicao, um ambiente que ativasse o console por
                // engano o teria aberto a qualquer um.
                if (consoleH2Ligado) {
                    reg.requestMatchers("/h2-console/**").permitAll();
                }

                reg.anyRequest().authenticated();
            })
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) ->
                            escrever(res, HttpStatus.UNAUTHORIZED, "nao_autenticado",
                                    "Sessão ausente ou expirada. Faça login novamente."))
                    .accessDeniedHandler((req, res, e) ->
                            escrever(res, HttpStatus.FORBIDDEN, "acesso_negado",
                                    "Você não tem permissão para esta operação.")))
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void escrever(jakarta.servlet.http.HttpServletResponse res, HttpStatus status,
                          String erro, String mensagem) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getWriter(),
                ErroResposta.de(status.value(), erro, mensagem, List.of()));
    }

    @Bean
    CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Padroes, e nao origens exatas: em desenvolvimento o Vite troca de porta
        // sozinho quando a 5173 esta ocupada, e o app nao pode quebrar por isso.
        // Continua sendo lista fechada — em producao vem a origem exata.
        config.setAllowedOriginPatterns(props.cors().origens());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Sem expor estes dois, um frontend servido de outra origem baixaria o PDF
        // sem o nome de arquivo correto e sem conseguir mostrar o codigo de
        // validacao logo apos a emissao — o proxy de desenvolvimento esconde isso
        // porque ali tudo e mesma origem.
        config.setExposedHeaders(List.of("Content-Disposition", "X-Codigo-Validacao"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
