package br.jus.tjgo.goianao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada do backend do Premio Goianao (TJGO).
 *
 * <p>A autoconfiguracao de {@code UserDetailsService} e excluida porque nao ha
 * usuario/senha local: a identidade vem do SSO (mockado nesta fase) e a sessao e
 * um JWT emitido por {@code AuthController}.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class GoianaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoianaoApplication.class, args);
    }
}
