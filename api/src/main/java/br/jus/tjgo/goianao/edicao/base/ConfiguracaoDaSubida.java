package br.jus.tjgo.goianao.edicao.base;

import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ordem da subida: os schemas das edicoes antes do JPA.
 *
 * <p>Com {@code ddl-auto: validate}, o Hibernate confere o mapeamento contra o
 * banco no momento em que cria o {@code EntityManagerFactory} — e ele o faz pela
 * conexao da edicao padrao. Sem esta dependencia declarada, a aplicacao poderia
 * tentar validar tabelas em um schema que {@link BaseDaEdicao} ainda nao criou,
 * e a falha apareceria como "tabela não encontrada", longe da causa.
 */
@Configuration
public class ConfiguracaoDaSubida {

    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor jpaDependeDaBaseDaEdicao() {
        return new EntityManagerFactoryDependsOnPostProcessor(BaseDaEdicao.class);
    }
}
