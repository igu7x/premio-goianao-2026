package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.auth.AcessoPorEdicao.EdicaoDeAcesso;
import br.jus.tjgo.goianao.auth.dto.EdicaoDaSessao;
import br.jus.tjgo.goianao.auth.dto.SessaoResposta;
import br.jus.tjgo.goianao.comum.erro.AcessoNegadoException;
import br.jus.tjgo.goianao.seguranca.JwtService;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Transforma uma identidade ja verificada na sessao do sistema (feature 011).
 *
 * <p>Existe para que os tres caminhos de entrada — SSO, senha e login de teste —
 * produzam exatamente a mesma sessao: mesmo token, mesma edicao de entrada,
 * mesma lista de edicoes ao alcance. Antes cada controlador montava o seu; com
 * a edicao dentro do token, repetir a montagem em tres lugares seria repetir a
 * chance de esquecer a edicao em um deles.
 */
@Service
public class MontadorDeSessao {

    private final AcessoPorEdicao acesso;
    private final JwtService jwtService;

    public MontadorDeSessao(AcessoPorEdicao acesso, JwtService jwtService) {
        this.acesso = acesso;
        this.jwtService = jwtService;
    }

    /**
     * Sessao de quem acabou de se identificar, na edicao de entrada dele.
     *
     * @throws AcessoNegadoException se a pessoa nao existe em edicao nenhuma
     *     (011/RF-9)
     */
    public SessaoResposta paraEntrada(IdentidadeAutenticada identidade) {
        EdicaoDeAcesso entrada = acesso.entradaDe(identidade.email())
                .orElseThrow(MontadorDeSessao::semCadastro);
        return montar(identidade, entrada);
    }

    /** Sessao de quem ja entrou e esta trocando de edicao (011/RF-6). */
    public SessaoResposta paraEdicao(IdentidadeAutenticada identidade, Long edicaoId) {
        EdicaoDeAcesso destino = acesso.acessoA(edicaoId, identidade.email())
                .orElseThrow(() -> new AcessoNegadoException(
                        "Você não tem cadastro nessa edição do prêmio."));
        return montar(identidade, destino);
    }

    public SessaoResposta montar(IdentidadeAutenticada identidade, EdicaoDeAcesso edicao) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(
                identidade.email(), identidade.nome(), edicao.papeis(), edicao.id());

        List<EdicaoDaSessao> disponiveis = acesso.edicoesDe(identidade.email()).stream()
                .map(EdicaoDaSessao::de)
                .toList();

        return new SessaoResposta(
                jwtService.gerar(usuario),
                jwtService.validade().toSeconds(),
                usuario.email(),
                usuario.nome(),
                usuario.papeisComoTexto(),
                EdicaoDaSessao.de(edicao),
                disponiveis);
    }

    /**
     * Quem nao existe em edicao nenhuma nao entra (011/RF-9).
     *
     * <p>A mensagem diz o que aconteceu porque, aqui, a identidade ja foi
     * verificada pelo SSO: a pessoa e quem diz ser, e o que falta e cadastro.
     * Esconder isso so a faria tentar de novo sem entender o motivo.
     */
    private static AcessoNegadoException semCadastro() {
        return new AcessoNegadoException(
                "Você não tem cadastro em nenhuma edição do prêmio. "
                + "Peça ao administrador para incluí-lo na edição correspondente.");
    }
}
