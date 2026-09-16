package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.certificado.CertificadoEmitidoRepository;
import br.jus.tjgo.goianao.magistrado.MagistradoRepository;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoRepository;
import br.jus.tjgo.goianao.unidade.UnidadeRepository;
import br.jus.tjgo.goianao.usuario.dto.AtualizarUsuarioRequisicao;
import br.jus.tjgo.goianao.usuario.dto.UsuarioRequisicao;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de usuarios — exclusivo do superadministrador. */
@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder encoder;
    private final CertificadoEmitidoRepository certificados;
    private final MagistradoRepository reconhecidos;
    private final ServidorHabilitadoRepository habilitados;
    private final UnidadeRepository unidades;

    public UsuarioService(UsuarioRepository repositorio, PasswordEncoder encoder,
                          CertificadoEmitidoRepository certificados,
                          MagistradoRepository reconhecidos,
                          ServidorHabilitadoRepository habilitados,
                          UnidadeRepository unidades) {
        this.repositorio = repositorio;
        this.encoder = encoder;
        this.certificados = certificados;
        this.reconhecidos = reconhecidos;
        this.habilitados = habilitados;
        this.unidades = unidades;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return repositorio.findAllByOrderByNomeAsc();
    }

    public Usuario criar(UsuarioRequisicao dados) {
        String email = Email.exigir(dados.email());
        String cpf = Cpf.opcional(dados.cpf());

        if (repositorio.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe usuário com este e-mail.");
        }

        Usuario usuario = new Usuario(email, dados.nome().trim(), cpf, dados.papeis());
        usuario.definirLotacao(dados.unidadeLotacao(), areaDe(dados.papeis(), dados.areaAtuacao()));
        if (dados.senha() != null && !dados.senha().isBlank()) {
            usuario.definirSenhaHash(encoder.encode(dados.senha()));
        }
        return repositorio.save(usuario);
    }

    public Usuario atualizar(Long id, AtualizarUsuarioRequisicao dados) {
        Usuario usuario = buscar(id);
        // CPF em branco mantem o atual: a tela so conhece a versao mascarada.
        String cpf = dados.cpf() == null || dados.cpf().isBlank()
                ? usuario.getCpf()
                : Cpf.opcional(dados.cpf());

        exigirQueSobreSuperadmin(usuario, dados.papeis());

        usuario.alterarDados(dados.nome().trim(), cpf, dados.unidadeLotacao(),
                areaDe(dados.papeis(), dados.areaAtuacao()), dados.papeis());
        if (dados.senha() != null && !dados.senha().isBlank()) {
            usuario.definirSenhaHash(encoder.encode(dados.senha()));
        }
        return usuario;
    }

    /**
     * Apaga o usuario de verdade — quando ainda nao ha nada preso a ele.
     *
     * <p>A exclusao existe porque o cadastro nasce de cargas em lote: planilha
     * com e-mail errado e importacao de unidade inteira criam gente que nunca
     * deveria ter entrado, e desativar deixaria a lista cheia de fantasmas.
     *
     * <p>Mas e-mail e a chave de tudo que a pessoa fez (DI-24): apagar quem ja
     * emitiu certificado deixaria um documento em circulacao sem dono, e quem
     * esta em lista de habilitados ou foi reconhecido sumiria de uma edicao ja
     * fechada. Nesses casos a exclusao e recusada com o motivo, e o caminho
     * continua sendo desativar, que tira o acesso e preserva o historico.
     */
    @Transactional
    public void excluir(Long id) {
        Usuario usuario = buscar(id);
        exigirQueNaoSejaEuMesmo(usuario);
        exigirQueSobreSuperadmin(usuario, Set.of());

        List<String> vinculos = new ArrayList<>();
        if (certificados.existsByEmailEmissor(usuario.getEmail())) {
            vinculos.add("já emitiu certificado");
        }
        if (reconhecidos.existsByEmail(usuario.getEmail())) {
            vinculos.add("está cadastrado como magistrado reconhecido em alguma edição");
        }
        if (habilitados.existsByEmail(usuario.getEmail())) {
            vinculos.add("está em alguma lista de servidores habilitados");
        }
        if (unidades.existsByResponsavelEmail(usuario.getEmail())) {
            vinculos.add("responde por alguma unidade");
        }

        if (!vinculos.isEmpty()) {
            throw new ConflitoException(usuario.getNome() + " não pode ser excluído porque "
                    + String.join(", ", vinculos) + ". Desative o acesso: a pessoa deixa de "
                    + "entrar e o histórico do que já foi feito continua de pé.");
        }

        repositorio.delete(usuario);
    }

    public Usuario alternarAtivacao(Long id, boolean ativo) {
        Usuario usuario = buscar(id);
        if (!ativo) {
            exigirQueNaoSejaEuMesmo(usuario);
            exigirQueSobreSuperadmin(usuario, Set.of());
        }
        if (ativo) {
            usuario.ativar();
        } else {
            usuario.desativar();
        }
        return usuario;
    }

    /**
     * Promove a superadministrador o usuario dono do e-mail informado.
     *
     * <p>Por e-mail, e nao por um formulario de criacao: quem se promove ja
     * existe no sistema: promover e conceder um papel a alguem conhecido, nao
     * cadastrar gente nova. O e-mail e o identificador que o superadministrador
     * tem em maos — e o mesmo com que a pessoa entra.
     */
    public Usuario promoverASuperadmin(String email) {
        Usuario usuario = repositorio.findByEmailIgnoreCase(Email.normalizar(email))
                .orElseThrow(() -> new NaoEncontradoException(
                        "Nenhum usuário cadastrado com este e-mail. Cadastre-o primeiro e depois "
                        + "promova."));

        if (usuario.getPapeis().contains(Papel.SUPERADMIN)) {
            throw new ConflitoException("Este usuário já é superadministrador.");
        }
        if (!usuario.isAtivo()) {
            throw new RegraDeNegocioException(
                    "Usuário desativado não pode ser promovido. Reative-o antes.");
        }
        usuario.concederPapel(Papel.SUPERADMIN);
        return usuario;
    }

    /** Retira o papel, com a mesma guarda de nunca ficar sem superadministrador. */
    public Usuario revogarSuperadmin(Long id) {
        Usuario usuario = buscar(id);
        if (!usuario.getPapeis().contains(Papel.SUPERADMIN)) {
            throw new ConflitoException("Este usuário não é superadministrador.");
        }
        exigirQueSobreSuperadmin(usuario, Set.of());
        usuario.revogarPapel(Papel.SUPERADMIN);
        return usuario;
    }

    @Transactional(readOnly = true)
    public List<Usuario> superadmins() {
        return repositorio.findAllByOrderByNomeAsc().stream()
                .filter(u -> u.getPapeis().contains(Papel.SUPERADMIN))
                .toList();
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
    }

    /**
     * Impede que o sistema fique sem superadministrador.
     *
     * Sem esta guarda, o unico superadmin pode remover o proprio papel — ou se
     * desativar — e ninguem mais consegue cadastrar usuarios. Nao ha tela para
     * sair desse estado: so mexendo no banco.
     */
    private void exigirQueSobreSuperadmin(Usuario alvo, Set<Papel> novosPapeis) {
        if (!alvo.getPapeis().contains(Papel.SUPERADMIN)
                || novosPapeis.contains(Papel.SUPERADMIN)) {
            return;
        }
        long outros = repositorio.findAllByOrderByNomeAsc().stream()
                .filter(u -> !u.getId().equals(alvo.getId()))
                .filter(Usuario::isAtivo)
                .filter(u -> u.getPapeis().contains(Papel.SUPERADMIN))
                .count();
        if (outros == 0) {
            throw new RegraDeNegocioException(
                    "Este é o único superadministrador ativo. Promova outro usuário antes de "
                    + "remover o papel dele — senão ninguém mais consegue cadastrar usuários.");
        }
    }

    /** Desativar a si mesmo tranca a pessoa para fora na mesma requisicao. */
    private void exigirQueNaoSejaEuMesmo(Usuario alvo) {
        if (alvo.getEmail().equalsIgnoreCase(UsuarioAtual.obrigatorio().email())) {
            throw new RegraDeNegocioException("Você não pode desativar o próprio usuário.");
        }
    }

    /** Area de atuacao so faz sentido para magistrado. */
    private String areaDe(Set<Papel> papeis, String area) {
        if (!papeis.contains(Papel.MAGISTRADO)) {
            return null;
        }
        return area == null || area.isBlank() ? null : area.trim();
    }
}
