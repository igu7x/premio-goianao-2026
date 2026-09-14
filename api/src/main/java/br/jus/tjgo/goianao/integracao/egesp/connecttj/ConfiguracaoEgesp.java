package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.MockEgespClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Escolhe, na subida, quem responde pelo RH: a API corporativa quando a
 * configuracao esta completa, o mock caso contrario.
 *
 * <p>A escolha e explicita, e nao uma condicao espalhada por anotacoes, porque
 * ela precisa ir para o log: "esta pegando dados de onde?" e a primeira
 * pergunta quando a lista de uma unidade vem diferente do esperado.
 */
@Configuration
public class ConfiguracaoEgesp {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoEgesp.class);

    @Bean
    @Primary
    public EgespClient egespClient(ConnectTjProperties props, RestClient.Builder builder,
                                   MockEgespClient mock) {
        if (props.habilitado()) {
            log.info("RH: integracao com a API corporativa habilitada.");
            return new ConnectTjEgespClient(props, builder);
        }
        log.warn("RH: integracao corporativa nao configurada; valem os dados mockados. "
                + "Defina GOIANAO_CONNECTTJ_URL, _TOKEN_URL, _CLIENT_ID e _SECRET para ligar.");
        return mock;
    }
}
