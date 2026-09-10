package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro superadministrador na subida, quando ainda nao existe nenhum.
 *
 * <p>Sem isso o sistema nasce sem ninguem que possa cadastrar usuarios, e a
 * unica saida seria inserir a linha a mao no banco.
 *
 * <p><b>A senha nunca fica no repositorio.</b> Ela vem de
 * {@code GOIANAO_SUPERADMIN_SENHA} e e gravada como hash BCrypt — o valor em
 * claro nao e guardado nem registrado em log. Sem a variavel, nenhum
 * superadministrador e criado e o log diz o que fazer: e melhor subir sem
 * superadmin do que subir com uma senha previsivel que qualquer leitor do
 * codigo conheceria.
 */
@Component
public class SuperadminInicial {

    private static final Logger log = LoggerFactory.getLogger(SuperadminInicial.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String email;
    private final String senha;
    private final String cpf;
    private final String nome;

    public SuperadminInicial(UsuarioRepository usuarios,
                             PasswordEncoder encoder,
                             @Value("${goianao.superadmin.email:}") String email,
                             @Value("${goianao.superadmin.senha:}") String senha,
                             @Value("${goianao.superadmin.cpf:}") String cpf,
                             @Value("${goianao.superadmin.nome:Superadministrador}") String nome) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.email = email;
        this.senha = senha;
        this.cpf = cpf;
        this.nome = nome;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void criarSeNecessario() {
        boolean jaExiste = usuarios.findAllByOrderByNomeAsc().stream()
                .anyMatch(u -> u.getPapeis().contains(Papel.SUPERADMIN));
        if (jaExiste) {
            return;
        }

        // Todas de uma vez: avisar de uma e so cobrar a proxima na subida
        // seguinte faz a pessoa descobrir o que falta em duas tentativas.
        List<String> faltando = new ArrayList<>();
        if (email.isBlank()) {
            faltando.add("GOIANAO_SUPERADMIN_EMAIL");
        } else if (!Email.valido(email)) {
            faltando.add("GOIANAO_SUPERADMIN_EMAIL (o valor informado não é um e-mail válido)");
        }
        if (senha.isBlank()) {
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
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            log.warn("Já existe usuário com o e-mail do superadministrador inicial, mas sem o "
                    + "papel. Promova-o pelo cadastro de usuários.");
            return;
        }

        Usuario superadmin = new Usuario(emailNormalizado, nome,
                cpf.isBlank() ? null : cpfNormalizado,
                EnumSet.of(Papel.SUPERADMIN, Papel.ADMINISTRADOR));
        superadmin.definirSenhaHash(encoder.encode(senha));
        usuarios.save(superadmin);

        log.info("Superadministrador inicial criado para {}. Troque a senha no primeiro acesso.",
                emailNormalizado);
    }
}
