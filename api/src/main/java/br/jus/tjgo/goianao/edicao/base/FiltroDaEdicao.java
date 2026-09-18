package br.jus.tjgo.goianao.edicao.base;

import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Decide sobre qual edicao esta requisicao age (011/RF-4).
 *
 * <p>Corre logo depois do filtro do JWT, porque a resposta esta na sessao: o
 * token diz em que edicao a pessoa entrou. Sem token — a verificacao publica de
 * um certificado, a tela de login —, vale a edicao vigente.
 *
 * <p>O contexto anterior e reposto no fim, e nao apagado. Na aplicacao os dois
 * dao no mesmo — a thread volta vazia para o pool do servidor, e um schema
 * esquecido aqui seria alguem lendo a base do ano errado sem nenhum sinal.
 * A diferenca aparece quando quem chama a requisicao ja estava agindo sobre uma
 * edicao, como nos testes: apagar levaria junto o contexto de quem chamou.
 */
@Component
public class FiltroDaEdicao extends OncePerRequestFilter {

    private final CatalogoDeEdicoes catalogo;

    public FiltroDaEdicao(CatalogoDeEdicoes catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requisicao,
                                    @NonNull HttpServletResponse resposta,
                                    @NonNull FilterChain cadeia) throws ServletException, IOException {
        try {
            EdicaoCorrente.executarEm(edicaoDaSessao(), () -> {
                try {
                    cadeia.doFilter(requisicao, resposta);
                } catch (IOException | ServletException e) {
                    throw new FalhaNaCadeia(e);
                }
            });
        } catch (FalhaNaCadeia falha) {
            // Devolve a excecao original: quem esta acima na cadeia trata
            // IOException e ServletException pelo tipo.
            if (falha.getCause() instanceof IOException e) {
                throw e;
            }
            throw (ServletException) falha.getCause();
        }
    }

    /**
     * O schema da edicao do token, ou {@code null} para cair na vigente.
     *
     * <p>Edicao que nao existe mais no catalogo tambem cai na vigente: o token
     * continua valido para quem o carrega, e recusar a requisicao inteira por
     * causa de uma edicao removida seria pior do que atende-la no ano corrente.
     */
    private String edicaoDaSessao() {
        return UsuarioAtual.opcional()
                .map(UsuarioAutenticado::edicaoId)
                .flatMap(catalogo::schemaDe)
                .orElse(null);
    }

    /** Leva a excecao da cadeia atraves do trecho, que nao as declara. */
    private static class FalhaNaCadeia extends RuntimeException {
        FalhaNaCadeia(Exception causa) {
            super(causa);
        }
    }
}
