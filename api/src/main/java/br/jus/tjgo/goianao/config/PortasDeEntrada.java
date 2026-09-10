package br.jus.tjgo.goianao.config;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Verifica, na subida, quais portas de entrada este ambiente abre.
 *
 * <p>Segue a mesma regra que {@code JwtService} aplica ao segredo de
 * assinatura: falhar no start e melhor do que o contrario. Uma aplicacao no ar
 * com login mockado nao da sinal nenhum de que esta aberta — ela funciona
 * perfeitamente, e o problema so aparece quando alguem ja entrou como
 * administrador sem credencial. Aqui o pod nao inicia e o log diz o que
 * remover.
 */
@Component
public class PortasDeEntrada {

    private static final Logger log = LoggerFactory.getLogger(PortasDeEntrada.class);

    public PortasDeEntrada(GoianaoProperties props, Environment ambiente) {
        boolean desenvolvimento = Arrays.stream(ambiente.getActiveProfiles())
                .anyMatch(p -> p.equals("dev") || p.equals("test"));

        if (props.login().mock() && !desenvolvimento) {
            throw new IllegalStateException(
                    "GOIANAO_LOGIN_MOCK=true fora de desenvolvimento. O login mockado dispensa "
                    + "credencial: informado o e-mail, a sessão é emitida — e /api/auth/usuarios-mock "
                    + "lista as identidades disponíveis, uma delas administrador. Remova a "
                    + "variável.");
        }

        if (props.login().senha()) {
            // Nao e problema — e pedido em homologacao. Mas fica registrado, para
            // que uma auditoria de producao encontre a linha se ela nao devia
            // estar la.
            log.info("Login por e-mail e senha HABILITADO (goianao.login.senha=true).");
        }
        if (props.login().mock()) {
            log.warn("Login mockado HABILITADO: qualquer e-mail de teste entra sem credencial.");
        }
        if (!props.login().senha() && !props.login().mock()) {
            log.info("Apenas SSO como porta de entrada.");
        }
    }
}
