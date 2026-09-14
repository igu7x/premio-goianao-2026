package br.jus.tjgo.goianao.auth.sso;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.integracao.egesp.EgespClient;
import br.jus.tjgo.goianao.integracao.egesp.ServidorEgesp;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Atualiza o cadastro de quem acabou de entrar com o que o RH tem (010/RF-8).
 *
 * <p>Tres cuidados, e cada um tem motivo:
 *
 * <ul>
 *   <li><b>Assincrona.</b> O RH e um sistema de terceiros; se ele estiver lento
 *       ou fora do ar, quem esta entrando no premio nao pode esperar nem levar
 *       erro na cara.</li>
 *   <li><b>Silenciosa.</b> Falha vira log, nunca tela: a pessoa entrou, e a
 *       sessao dela nao depende do RH.</li>
 *   <li><b>Nao toca na lista de habilitados.</b> Quem pode emitir e snapshot da
 *       edicao (constituicao, principio 3b) — so muda por ato do administrador.
 *       Uma rotina de login que habilitasse alguem quebraria a reemissao de
 *       edicoes antigas.</li>
 * </ul>
 */
@Component
public class AtualizacaoPeloRh {

    private static final Logger log = LoggerFactory.getLogger(AtualizacaoPeloRh.class);

    private final EgespClient egesp;
    private final UsuarioRepository usuarios;

    public AtualizacaoPeloRh(EgespClient egesp, UsuarioRepository usuarios) {
        this.egesp = egesp;
        this.usuarios = usuarios;
    }

    /**
     * Transacao nova, e nao a do login: este metodo roda depois do commit e num
     * outro thread — a transacao original ja nem existe mais.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEntrar(LoginPeloSso evento) {
        try {
            atualizar(evento.email());
        } catch (RuntimeException e) {
            // O nome do claim e o e-mail nao vao para o log: dado pessoal
            // costuma sair do perimetro (coletor, indice, backup).
            log.warn("Nao foi possivel atualizar o cadastro pelo RH no login: {}", e.toString());
        }
    }

    private void atualizar(String email) {
        Optional<Usuario> cadastrado = usuarios.findByEmailIgnoreCase(email);
        if (cadastrado.isEmpty()) {
            return;
        }
        String loginAd = prefixoDoEmail(email);
        Optional<ServidorEgesp> noRh = egesp.servidorPorLogin(loginAd);
        if (noRh.isEmpty()) {
            return;
        }

        ServidorEgesp servidor = noRh.get();
        String cpf = Cpf.valido(servidor.cpf()) ? Cpf.normalizar(servidor.cpf()) : null;
        cadastrado.get().atualizarPeloRh(servidor.nome(), cpf, servidor.matricula(), loginAd,
                null);
    }

    /** O login de rede e o que vem antes do arroba (confirmado com a equipe da API). */
    private String prefixoDoEmail(String email) {
        int arroba = email.indexOf('@');
        return arroba < 1 ? email : email.substring(0, arroba);
    }
}
