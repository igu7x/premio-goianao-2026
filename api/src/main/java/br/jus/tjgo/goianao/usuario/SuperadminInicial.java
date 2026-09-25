package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.config.GoianaoProperties;
import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes;
import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes.EdicaoNoCatalogo;
import br.jus.tjgo.goianao.edicao.base.EdicaoCorrente;
import br.jus.tjgo.goianao.seguranca.Papel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cria o primeiro superadministrador na subida, quando ainda nao existe nenhum.
 *
 * <p>Sem isso o sistema nasce sem ninguem que possa cadastrar usuarios, e a
 * unica saida seria inserir a linha a mao no banco.
 *
 * <p><b>O que a variavel garante e aquele e-mail</b>, e nao "algum
 * superadministrador". A primeira versao parava assim que encontrava qualquer
 * superadmin na base — e em producao isso deu no que tinha de dar: a migracao
 * 009 planta um superadmin de bootstrap, a aplicacao o encontrava, ficava calada,
 * e o e-mail corporativo configurado nunca era criado. Ninguem conseguia entrar:
 * a conta plantada so tem senha, e ali o acesso e pelo SSO.
 *
 * <p><b>A senha nunca fica no repositorio.</b> Ela vem de
 * {@code GOIANAO_SUPERADMIN_SENHA} e e gravada como hash BCrypt — o valor em
 * claro nao e guardado nem registrado em log. Enquanto o login por senha
 * estiver ligado, sem a variavel nenhum superadministrador e criado e o log diz
 * o que fazer: e melhor subir sem superadmin do que subir com uma senha
 * previsivel que qualquer leitor do codigo conheceria.
 *
 * <p><b>Onde so ha SSO — producao —, a senha nao e pedida.</b> Ela nao teria uso:
 * a pessoa entra pelo tribunal, e o que este cadastro faz e dizer que aquele
 * e-mail administra o sistema. O usuario e criado sem senha alguma.
 *
 * <p>Desde a feature 011 ele roda <b>em cada edicao</b>: cada uma tem a sua base
 * de usuarios, e uma edicao sem superadministrador nao poderia ser administrada
 * por ninguem. A mesma credencial vale em todas — o que muda e o cadastro, nao
 * a pessoa.
 */
@Component
public class SuperadminInicial {

    private static final Logger log = LoggerFactory.getLogger(SuperadminInicial.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final CatalogoDeEdicoes catalogo;
    private final TransactionTemplate transacao;
    private final boolean loginPorSenha;
    private final String email;
    private final String senha;
    private final String cpf;
    private final String nome;

    public SuperadminInicial(UsuarioRepository usuarios,
                             PasswordEncoder encoder,
                             CatalogoDeEdicoes catalogo,
                             PlatformTransactionManager gerenciador,
                             GoianaoProperties props,
                             @Value("${goianao.superadmin.email:}") String email,
                             @Value("${goianao.superadmin.senha:}") String senha,
                             @Value("${goianao.superadmin.cpf:}") String cpf,
                             @Value("${goianao.superadmin.nome:Superadministrador}") String nome) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.catalogo = catalogo;
        // A transacao e aberta dentro do escopo de cada edicao: com @Transactional
        // no metodo, ela comecaria antes de se saber de qual base estamos falando.
        this.transacao = new TransactionTemplate(gerenciador);
        this.loginPorSenha = props.login().senha();
        this.email = email;
        this.senha = senha;
        this.cpf = cpf;
        this.nome = nome;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void criarSeNecessario() {
        for (EdicaoNoCatalogo edicao : catalogo.todas()) {
            if (edicao.schemaDados() == null) {
                continue;
            }
            EdicaoCorrente.executarEm(edicao.schemaDados(),
                    () -> transacao.executeWithoutResult(status -> criarNaBaseCorrente()));
        }
    }

    private void criarNaBaseCorrente() {
        // Sem e-mail configurado nao ha o que garantir: so avisa se a base ficou
        // sem nenhum superadministrador, que e o estado em que ninguem administra.
        if (email.isBlank()) {
            if (usuarios.superadminsAtivos(Papel.SUPERADMIN).isEmpty()) {
                log.warn("Nenhum superadministrador cadastrado. Defina "
                        + "GOIANAO_SUPERADMIN_EMAIL (e GOIANAO_SUPERADMIN_SENHA, onde houver "
                        + "login por senha) e reinicie para criar o primeiro acesso.");
            }
            return;
        }

        // Todas de uma vez: avisar de uma e so cobrar a proxima na subida
        // seguinte faz a pessoa descobrir o que falta em duas tentativas.
        List<String> faltando = new ArrayList<>();
        if (!Email.valido(email)) {
            faltando.add("GOIANAO_SUPERADMIN_EMAIL (o valor informado não é um e-mail válido)");
        }
        // Com SSO como unica porta de entrada, senha nao faz falta nenhuma.
        if (senha.isBlank() && loginPorSenha) {
            faltando.add("GOIANAO_SUPERADMIN_SENHA");
        }
        // O CPF e opcional; so atrapalha quando vem preenchido e errado.
        String cpfNormalizado = Cpf.normalizar(cpf);
        if (!cpf.isBlank() && !Cpf.valido(cpfNormalizado)) {
            faltando.add("GOIANAO_SUPERADMIN_CPF (o valor informado não é um CPF válido; "
                    + "é opcional, pode ser removido)");
        }

        if (!faltando.isEmpty()) {
            log.warn("Nenhum superadministrador cadastrado. Defina {} e reinicie para criar o "
                    + "primeiro acesso.", String.join(", ", faltando));
            return;
        }

        String emailNormalizado = Email.normalizar(email);

        // Ja cadastrado: garante o papel e o acesso, em vez de mandar promover a
        // mao — quem promove e o superadministrador, e e justamente ele que falta.
        Usuario existente = usuarios.findByEmailIgnoreCase(emailNormalizado).orElse(null);
        if (existente != null) {
            boolean mudou = false;
            if (!existente.getPapeis().contains(Papel.SUPERADMIN)) {
                existente.concederPapel(Papel.SUPERADMIN);
                mudou = true;
            }
            if (!existente.isAtivo()) {
                existente.ativar();
                mudou = true;
            }
            if (mudou) {
                usuarios.save(existente);
                log.info("Superadministrador garantido para {}.", emailNormalizado);
            }
            return;
        }

        Usuario superadmin = new Usuario(emailNormalizado, nome,
                cpf.isBlank() ? null : cpfNormalizado,
                EnumSet.of(Papel.SUPERADMIN, Papel.ADMINISTRADOR));
        if (!senha.isBlank()) {
            superadmin.definirSenhaHash(encoder.encode(senha));
        }
        usuarios.save(superadmin);

        log.info("Superadministrador inicial criado para {}. {}", emailNormalizado,
                senha.isBlank()
                        ? "Sem senha: o acesso é pelo SSO."
                        : "Troque a senha no primeiro acesso.");
    }
}
