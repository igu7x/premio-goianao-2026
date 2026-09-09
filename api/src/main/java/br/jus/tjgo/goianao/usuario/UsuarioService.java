package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import br.jus.tjgo.goianao.usuario.dto.AtualizarUsuarioRequisicao;
import br.jus.tjgo.goianao.usuario.dto.UsuarioRequisicao;
import java.util.List;
import java.util.Locale;
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

    public UsuarioService(UsuarioRepository repositorio, PasswordEncoder encoder) {
        this.repositorio = repositorio;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return repositorio.findAllByOrderByNomeAsc();
    }

    public Usuario criar(UsuarioRequisicao dados) {
        String cpf = normalizarCpf(dados.cpf());
        String email = normalizarEmail(dados.email());

        if (repositorio.existsByCpf(cpf)) {
            throw new ConflitoException("Já existe usuário com este CPF.");
        }
        if (repositorio.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe usuário com este e-mail.");
        }

        Usuario usuario = new Usuario(cpf, dados.nome().trim(), email, dados.papeis());
        usuario.definirLotacao(dados.unidadeLotacao(), areaDe(dados.papeis(), dados.areaAtuacao()));
        if (dados.senha() != null && !dados.senha().isBlank()) {
            usuario.definirSenhaHash(encoder.encode(dados.senha()));
        }
        return repositorio.save(usuario);
    }

    public Usuario atualizar(Long id, AtualizarUsuarioRequisicao dados) {
        Usuario usuario = buscar(id);
        String email = normalizarEmail(dados.email());

        repositorio.findByEmailIgnoreCase(email)
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new ConflitoException("Já existe usuário com este e-mail.");
                });

        exigirQueSobreSuperadmin(usuario, dados.papeis());

        usuario.alterarDados(dados.nome().trim(), email, dados.unidadeLotacao(),
                areaDe(dados.papeis(), dados.areaAtuacao()), dados.papeis());
        if (dados.senha() != null && !dados.senha().isBlank()) {
            usuario.definirSenhaHash(encoder.encode(dados.senha()));
        }
        return usuario;
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
        Usuario usuario = repositorio.findByEmailIgnoreCase(normalizarEmail(email))
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
        if (alvo.getCpf().equals(UsuarioAtual.obrigatorio().cpf())) {
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

    private String normalizarCpf(String bruto) {
        String cpf = Cpf.normalizar(bruto);
        if (!Cpf.valido(cpf)) {
            throw new RegraDeNegocioException("CPF inválido.");
        }
        return cpf;
    }

    private String normalizarEmail(String bruto) {
        return bruto.trim().toLowerCase(Locale.ROOT);
    }
}
