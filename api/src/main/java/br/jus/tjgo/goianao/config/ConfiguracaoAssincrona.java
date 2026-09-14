package br.jus.tjgo.goianao.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita o trabalho assincrono do sistema — hoje apenas a atualizacao
 * cadastral pelo RH depois do login (010).
 *
 * <p>Pool pequeno e fila curta de proposito: sao tarefas de segundo plano que
 * podem ser perdidas sem prejuizo. O que nao pode e uma fila infinita segurando
 * memoria enquanto o RH nao responde — por isso a politica de descarte, com o
 * log dizendo o que foi descartado.
 */
@Configuration
@EnableAsync
public class ConfiguracaoAssincrona {

    @Bean
    public TaskExecutor taskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(2)
                .maxPoolSize(4)
                .queueCapacity(50)
                .threadNamePrefix("goianao-async-")
                .build();
    }
}
