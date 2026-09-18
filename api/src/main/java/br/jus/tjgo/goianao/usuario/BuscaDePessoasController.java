package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busca de pessoas para escolher em formulario: no sistema <b>e</b> no RH.
 *
 * <p>So o RH nao basta. Quem entrou por planilha, ou foi cadastrado a mao em
 * Usuarios do sistema, pode nao estar no RH do jeito que foi escrito — e a busca
 * dizia "ninguem com esse nome" sobre alguem que esta no proprio cadastro.
 *
 * <p>Quem esta nos dois aparece uma vez so, como do sistema: e o cadastro que ja
 * tem o e-mail que o login reconhece. E o RH fora do ar nao esvazia a busca — os
 * do sistema continuam aparecendo, e a resposta avisa que o RH nao respondeu.
 */
/*
 * Aberta tambem ao magistrado desde 17/09/2026: e ele quem ajusta a lista de
 * habilitados da unidade dele, e ali a pessoa passou a ser escolhida da busca em
 * vez de digitada. Sem isto, a tela dele voltaria ao e-mail digitado a mao — e
 * uma letra errada cria um habilitado que nunca conseguira emitir.
 */
@RestController
@RequestMapping("/api/pessoas")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'MAGISTRADO')")
public class BuscaDePessoasController {

    private static final Logger log = LoggerFactory.getLogger(BuscaDePessoasController.class);

    private static final int LIMITE = 20;
    private static final int MINIMO_DO_TERMO = 3;

    public enum Origem { SISTEMA, RH }

    /**
     * @param email nulo quando a pessoa vem do RH sem endereco; a tela completa
     *              pelo AD na escolha, que custa uma chamada por pessoa
     */
    public record PessoaEncontrada(
            Origem origem,
            Long matricula,
            String nome,
            String email,
            String cpfMascarado,
            boolean temEmail) {}

    public record ResultadoDaBusca(List<PessoaEncontrada> pessoas, boolean rhRespondeu) {}

    private final UsuarioRepository usuarios;
    private final EgespClient egesp;

    public BuscaDePessoasController(UsuarioRepository usuarios, EgespClient egesp) {
        this.usuarios = usuarios;
        this.egesp = egesp;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResultadoDaBusca procurar(@RequestParam String termo) {
        String alvo = Texto.canonicalizar(termo);
        if (alvo == null || alvo.length() < MINIMO_DO_TERMO) {
            return new ResultadoDaBusca(List.of(), true);
        }

        List<PessoaEncontrada> encontradas = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        Set<Long> matriculas = new HashSet<>();

        // Busca em memoria, e nao por LIKE: o nome precisa casar sem acento e
        // sem caixa, e o cadastro de usuarios e pequeno perto do RH.
        for (Usuario usuario : usuarios.findAllByOrderByNomeAsc()) {
            if (encontradas.size() >= LIMITE) {
                break;
            }
            if (!usuario.isAtivo()) {
                continue;
            }
            boolean casa = Texto.canonicalizar(usuario.getNome()).contains(alvo)
                    || usuario.getEmail().toLowerCase().contains(alvo);
            if (!casa) {
                continue;
            }
            encontradas.add(new PessoaEncontrada(Origem.SISTEMA, usuario.getMatricula(),
                    usuario.getNome(), usuario.getEmail(), Cpf.mascarar(usuario.getCpf()), true));
            emails.add(Email.normalizar(usuario.getEmail()));
            if (usuario.getMatricula() != null) {
                matriculas.add(usuario.getMatricula());
            }
        }

        boolean rhRespondeu = true;
        try {
            for (ServidorEgesp servidor : egesp.procurarPessoas(termo)) {
                if (encontradas.size() >= LIMITE) {
                    break;
                }
                boolean valido = Email.valido(servidor.email());
                boolean repetida = (valido && emails.contains(Email.normalizar(servidor.email())))
                        || (servidor.matricula() != null && matriculas.contains(servidor.matricula()));
                if (repetida) {
                    continue;
                }
                encontradas.add(new PessoaEncontrada(Origem.RH, servidor.matricula(),
                        servidor.nome(), valido ? Email.normalizar(servidor.email()) : null,
                        Cpf.mascarar(servidor.cpf()), valido));
            }
        } catch (RuntimeException e) {
            // O RH fora do ar nao pode esconder quem ja esta no cadastro.
            log.warn("Busca de pessoas: o RH nao respondeu ({}). Seguindo so com o sistema.",
                    e.getMessage());
            rhRespondeu = false;
        }

        return new ResultadoDaBusca(encontradas, rhRespondeu);
    }
}
