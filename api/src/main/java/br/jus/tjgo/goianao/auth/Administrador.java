package br.jus.tjgo.goianao.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Cadastro explicito de administradores (001/RF-4). Enquanto o SSO real nao
 * define uma claim de perfil, o papel ADMINISTRADOR vem desta lista — chaveada
 * pelo e-mail corporativo (DI-24).
 */
@Entity
@Table(name = "administrador")
public class Administrador {

    @Id
    @Column(name = "email", length = 200, nullable = false)
    private String email;

    @Column(name = "nome", length = 200, nullable = false)
    private String nome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Administrador() {}

    public Administrador(String email, String nome) {
        this.email = email;
        this.nome = nome;
        this.criadoEm = LocalDateTime.now();
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
