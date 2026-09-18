package br.jus.tjgo.goianao.edicao.base;

import br.jus.tjgo.goianao.comum.erro.AcessoNegadoException;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Recusa a requisicao que fala de uma edicao diferente da sessao (011/RF-11).
 *
 * <p>Varias rotas trazem a edicao no caminho ou na consulta —
 * {@code /api/edicoes/{edicaoId}/magistrados}, {@code ?edicaoId=} — porque
 * nasceram quando todas as edicoes dividiam a mesma base. Hoje a base e a da
 * sessao, e uma rota que dissesse 2026 numa sessao de 2027 leria os dados de
 * 2027 com o numero de 2026 no pedido. Em vez de responder isso, a guarda
 * recusa: quem quer agir em 2026 troca de edicao, e a troca confere o direito.
 *
 * <p>Ficam de fora as operacoes sobre o <b>catalogo</b> ({@code /api/edicoes/{id}}:
 * detalhe, publicar, tornar vigente). O catalogo e um so, e a tela de edicoes
 * precisa agir sobre todas elas.
 */
@Component
public class GuardaDaEdicaoDaSessao implements HandlerInterceptor {

    private static final String VARIAVEL = "edicaoId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest requisicao,
                             @NonNull HttpServletResponse resposta,
                             @NonNull Object handler) {
        UsuarioAutenticado usuario = UsuarioAtual.opcional().orElse(null);
        if (usuario == null || usuario.edicaoId() == null) {
            // Sem sessao a rota ja e barrada pela seguranca; sem edicao no token
            // (sessao anterior a 011) a requisicao cai na vigente.
            return true;
        }

        Long pedida = edicaoPedida(requisicao);
        if (pedida != null && !pedida.equals(usuario.edicaoId())) {
            throw new AcessoNegadoException("Esta operação é de outra edição do prêmio. "
                    + "Troque para ela no seletor de edição e tente de novo.");
        }
        return true;
    }

    private Long edicaoPedida(HttpServletRequest requisicao) {
        @SuppressWarnings("unchecked")
        Map<String, String> variaveis = (Map<String, String>) requisicao
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String valor = variaveis == null ? null : variaveis.get(VARIAVEL);
        if (valor == null) {
            valor = requisicao.getParameter(VARIAVEL);
        }
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            // Quem valida o formato e o controlador, com a mensagem dele.
            return null;
        }
    }
}
