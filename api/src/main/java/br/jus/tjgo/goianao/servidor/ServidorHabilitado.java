package br.jus.tjgo.goianao.servidor;

import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Snapshot, por edicao x unidade, de quem pode emitir o certificado de servidor
 * (constituicao, principio 3b).
 *
 * <p>A lista existe justamente porque a lotacao ao vivo nao serve: reemitir uma
 * edicao antiga precisa refletir quem estava naquela unidade <b>naquela epoca</b>.
 * A remocao e logica ({@code ativo = false}) para preservar a auditoria e para
 * que uma nova semeadura nao ressuscite quem foi retirado de proposito.
 */
@Entity
@Table(name = "servidor_habilitado")
public class ServidorHabilitado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private UnidadeJudiciaria unidade;

    /** Chave da pessoa (DI-24). */
    @Column(name = "email", length = 200, nullable = false)
    private String email;

    /** Opcional, so informativo. */
    @Column(name = "cpf", length = 11)
    private String cpf;

    @Column(name = "nome", length = 200, nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", length = 10, nullable = false)
    private OrigemServidor origem;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "criado_por", length = 200)
    private String criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_por", length = 200)
    private String atualizadoPor;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    protected ServidorHabilitado() {}

    public ServidorHabilitado(Edicao edicao, UnidadeJudiciaria unidade, String email,
                              String nome, String cpf, OrigemServidor origem, String autor) {
        this.edicao = edicao;
        this.unidade = unidade;
        this.email = email;
        this.nome = nome;
        this.cpf = cpf;
        this.origem = origem;
        this.ativo = true;
        this.criadoPor = autor;
        this.criadoEm = LocalDateTime.now();
    }

    public void desativar(String autor) {
        this.ativo = false;
        this.atualizadoPor = autor;
        this.atualizadoEm = LocalDateTime.now();
    }

    /** Reativa um item removido — apenas por acao explicita, nunca por semeadura. */
    public void reativar(String nome, String cpf, OrigemServidor origem, String autor) {
        this.ativo = true;
        this.nome = nome;
        if (cpf != null) {
            this.cpf = cpf;
        }
        this.origem = origem;
        this.atualizadoPor = autor;
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Edicao getEdicao() {
        return edicao;
    }

    public UnidadeJudiciaria getUnidade() {
        return unidade;
    }

    public String getEmail() {
        return email;
    }

    public String getCpf() {
        return cpf;
    }

    public String getNome() {
        return nome;
    }

    public OrigemServidor getOrigem() {
        return origem;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
