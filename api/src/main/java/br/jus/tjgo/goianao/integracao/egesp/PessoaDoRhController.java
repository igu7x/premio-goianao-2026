package br.jus.tjgo.goianao.integracao.egesp;

import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de pessoas no RH, para o administrador <b>escolher</b> em vez de
 * digitar.
 *
 * <p>Existe por causa do erro mais caro do sistema: um e-mail digitado errado no
 * cadastro de reconhecidos só aparece meses depois, quando o magistrado tenta
 * emitir e não encontra nada seu — e a essa altura a edição já está publicada,
 * onde editar é proibido (004/RF-7).
 *
 * <p>Não cria nem altera nada: é leitura, e a escolha de quem venceu continua
 * sendo do administrador (constituição, princípio 2).
 */
@RestController
@RequestMapping("/api/rh/pessoas")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class PessoaDoRhController {

    private final EgespClient egesp;

    public PessoaDoRhController(EgespClient egesp) {
        this.egesp = egesp;
    }

    /**
     * @param email nulo quando nem o RH nem o AD têm endereço para a pessoa. A
     *              tela precisa saber disso <b>antes</b> de cadastrar: sem
     *              e-mail ela nunca conseguiria emitir (DI-24)
     */
    public record PessoaResposta(
            Long matricula,
            String nome,
            String email,
            String cpfMascarado,
            boolean temEmail) {

        static PessoaResposta de(ServidorEgesp servidor) {
            boolean valido = Email.valido(servidor.email());
            return new PessoaResposta(
                    servidor.matricula(),
                    servidor.nome(),
                    valido ? Email.normalizar(servidor.email()) : null,
                    br.jus.tjgo.goianao.comum.Cpf.mascarar(servidor.cpf()),
                    valido);
        }
    }

    @GetMapping
    public List<PessoaResposta> procurar(@RequestParam String termo) {
        return egesp.procurarPessoas(termo).stream().map(PessoaResposta::de).toList();
    }

    /**
     * A pessoa escolhida, já com o e-mail completado pelo AD quando o RH não o
     * tem. A busca não faz isso — seriam várias chamadas por tecla digitada.
     */
    @GetMapping("/{matricula}")
    public PessoaResposta porMatricula(@PathVariable long matricula) {
        return egesp.servidorPorMatricula(matricula)
                .map(PessoaResposta::de)
                .orElseThrow(() -> new NaoEncontradoException(
                        "Matrícula " + matricula + " não encontrada no RH."));
    }

    /** Diz à tela se vale a pena oferecer a busca ou pedir o e-mail digitado. */
    @GetMapping("/situacao")
    public SituacaoDoRh situacao() {
        return new SituacaoDoRh(egesp.integracaoReal());
    }

    public record SituacaoDoRh(boolean disponivel) {}
}
