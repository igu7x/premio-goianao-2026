package br.jus.tjgo.goianao.edicao.base;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Aplica a {@link GuardaDaEdicaoDaSessao} as rotas da API.
 *
 * <p>A troca de edicao ({@code /api/auth/edicao/{edicaoId}}) fica de fora por
 * definicao: e a unica rota cujo proposito e justamente pedir uma edicao
 * diferente da sessao. A validacao dela e outra — a pessoa precisa existir na
 * edicao de destino.
 */
@Configuration
public class ConfiguracaoDaGuarda implements WebMvcConfigurer {

    private final GuardaDaEdicaoDaSessao guarda;

    public ConfiguracaoDaGuarda(GuardaDaEdicaoDaSessao guarda) {
        this.guarda = guarda;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(guarda)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/public/**");
    }
}
