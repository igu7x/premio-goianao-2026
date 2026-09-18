package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.erro.CredenciaisInvalidasException;
import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes;
import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes.EdicaoNoCatalogo;
import br.jus.tjgo.goianao.edicao.base.EdicaoCorrente;
import br.jus.tjgo.goianao.magistrado.MagistradoRepository;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoRepository;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Em quais edicoes uma pessoa existe, e com quais papeis em cada uma
 * (011/RF-6 a RF-9).
 *
 * <p>Com uma base por edicao, "quem e voce" deixou de ter resposta unica: a
 * mesma pessoa pode ser magistrada reconhecida em 2026, apenas servidora em
 * 2027 e nao existir em 2025. Esta classe e o unico ponto do sistema que
 * atravessa as bases — e atravessa de leitura, uma edicao de cada vez, com
 * {@link EdicaoCorrente#executarEm}.
 *
 * <p>E o que permite ao premiado de um ano continuar entrando depois que o ano
 * seguinte vira vigente: o certificado dele nao some quando a base do premio
 * recomeca.
 *
 * <p><b>Nao pode ser chamada de dentro de uma transacao.</b> Trocar de edicao e
 * trocar a conexao que a sessao do Hibernate usa; uma sessao ja aberta ficaria
 * presa ao schema em que nasceu. Por isso quem a usa e o controlador de
 * autenticacao, antes de qualquer servico transacional.
 */
@Service
public class AcessoPorEdicao {

    private final CatalogoDeEdicoes catalogo;
    private final PapeisResolver papeisResolver;
    private final LoginPorSenhaService loginPorSenha;
    private final UsuarioRepository usuarios;
    private final AdministradorRepository administradores;
    private final MagistradoRepository magistrados;
    private final ServidorHabilitadoRepository servidores;

    public AcessoPorEdicao(CatalogoDeEdicoes catalogo,
                           PapeisResolver papeisResolver,
                           LoginPorSenhaService loginPorSenha,
                           UsuarioRepository usuarios,
                           AdministradorRepository administradores,
                           MagistradoRepository magistrados,
                           ServidorHabilitadoRepository servidores) {
        this.catalogo = catalogo;
        this.papeisResolver = papeisResolver;
        this.loginPorSenha = loginPorSenha;
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.magistrados = magistrados;
        this.servidores = servidores;
    }

    /** Uma edicao a que a pessoa tem acesso, e o que ela e la dentro. */
    public record EdicaoDeAcesso(Long id, int ano, String schema, boolean vigente,
                                 Set<Papel> papeis) {}

    /** Quem entrou, e em qual edicao. */
    public record Entrada(IdentidadeAutenticada identidade, EdicaoDeAcesso edicao) {}

    /**
     * As edicoes em que a pessoa existe, na ordem em que interessam a ela: a
     * vigente primeiro, depois da mais recente para a mais antiga.
     */
    public List<EdicaoDeAcesso> edicoesDe(String emailBruto) {
        String email = Email.normalizar(emailBruto);
        if (email == null) {
            return List.of();
        }
        return emOrdemDeInteresse()
                .map(edicao -> acessoA(edicao, email))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Onde a pessoa entra ao fazer login: a edicao vigente, se ela existir la;
     * senao, a mais recente em que existe (011/RF-7).
     */
    public Optional<EdicaoDeAcesso> entradaDe(String email) {
        return edicoesDe(email).stream().findFirst();
    }

    /** O acesso da pessoa a uma edicao especifica, ou vazio se ela nao existe la. */
    public Optional<EdicaoDeAcesso> acessoA(Long edicaoId, String email) {
        return catalogo.porId(edicaoId)
                .flatMap(edicao -> acessoA(edicao, Email.normalizar(email)));
    }

    /**
     * Login por e-mail e senha, procurando a pessoa em todas as edicoes.
     *
     * <p>A senha e conferida edicao a edicao, e nao uma vez so, porque as bases
     * sao independentes: quem existe em 2026 e em 2027 tem um cadastro em cada
     * uma, e nada obriga as duas senhas a serem iguais. Para a pessoa isso e
     * invisivel — ela digita a senha que conhece e entra na edicao a que ela
     * pertence.
     *
     * <p>A recusa e a mesma do servico de login: uma so mensagem para e-mail
     * inexistente, senha errada e usuario desativado.
     */
    public Entrada autenticarPorSenha(String email, String senha) {
        for (EdicaoNoCatalogo edicao : emOrdemDeInteresse().toList()) {
            Optional<IdentidadeAutenticada> identidade =
                    EdicaoCorrente.executarEm(edicao.schemaDados(), () -> {
                        try {
                            return Optional.of(loginPorSenha.autenticar(email, senha));
                        } catch (CredenciaisInvalidasException naoEnessa) {
                            return Optional.<IdentidadeAutenticada>empty();
                        }
                    });
            if (identidade.isPresent()) {
                return new Entrada(identidade.get(),
                        acessoA(edicao, Email.normalizar(identidade.get().email()))
                                .orElseThrow(() -> new CredenciaisInvalidasException(
                                        "E-mail ou senha inválidos.")));
            }
        }
        throw new CredenciaisInvalidasException("E-mail ou senha inválidos.");
    }

    private java.util.stream.Stream<EdicaoNoCatalogo> emOrdemDeInteresse() {
        return catalogo.todas().stream()
                .filter(edicao -> edicao.schemaDados() != null)
                .sorted(Comparator.comparing(EdicaoNoCatalogo::vigente).reversed()
                        .thenComparing(Comparator.comparingInt(EdicaoNoCatalogo::ano).reversed()));
    }

    private Optional<EdicaoDeAcesso> acessoA(EdicaoNoCatalogo edicao, String email) {
        if (email == null || edicao.schemaDados() == null) {
            return Optional.empty();
        }
        return EdicaoCorrente.executarEm(edicao.schemaDados(), () -> {
            if (!existeNaEdicao(email)) {
                return Optional.<EdicaoDeAcesso>empty();
            }
            return Optional.of(new EdicaoDeAcesso(edicao.id(), edicao.ano(), edicao.schemaDados(),
                    edicao.vigente(), papeisResolver.resolver(email)));
        });
    }

    /**
     * Ter o que fazer nesta edicao.
     *
     * <p>Nao basta olhar o cadastro de usuarios: quem foi reconhecido ou
     * habilitado pelo administrador tem direito a emitir mesmo sem nunca ter
     * sido cadastrado — e e pelo SSO que ele entra. Por outro lado, usuario
     * desativado nao conta: desativar tem que tirar o acesso de fato.
     */
    private boolean existeNaEdicao(String email) {
        return usuarios.findByEmailIgnoreCase(email).map(Usuario::isAtivo).orElse(false)
                || administradores.existsByEmail(email)
                || magistrados.existsByEmail(email)
                || servidores.existsByEmail(email);
    }
}
