package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Magistrado reconhecido em uma edicao. E a fonte da verdade sobre quem venceu
 * (constituicao, principio 2) e tambem a origem do <b>nome impresso</b> no
 * certificado de magistrado (005/RF-4) — o SSO fornece apenas o CPF.
 */
@Entity
@Table(name = "magistrado_reconhecido")
public class MagistradoReconhecido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Column(name = "cpf", length = 11, nullable = false)
    private String cpf;

    @Column(name = "nome", length = 200, nullable = false)
    private String nome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @OneToMany(mappedBy = "magistrado", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<Reconhecimento> reconhecimentos = new ArrayList<>();

    protected MagistradoReconhecido() {}

    public MagistradoReconhecido(Edicao edicao, String cpf, String nome) {
        this.edicao = edicao;
        this.cpf = cpf;
        this.nome = nome;
        this.criadoEm = LocalDateTime.now();
    }

    public void renomear(String nome) {
        this.nome = nome;
        this.atualizadoEm = LocalDateTime.now();
    }

    public Reconhecimento adicionar(UnidadeJudiciaria unidade, Selo selo) {
        Reconhecimento novo = new Reconhecimento(this, unidade, selo);
        reconhecimentos.add(novo);
        this.atualizadoEm = LocalDateTime.now();
        return novo;
    }

    public void limparReconhecimentos() {
        reconhecimentos.clear();
        this.atualizadoEm = LocalDateTime.now();
    }

    public boolean reconheceUnidade(Long unidadeId) {
        return reconhecimentos.stream()
                .anyMatch(r -> r.getUnidade().getId().equals(unidadeId));
    }

    public Optional<Reconhecimento> reconhecimentoDe(Long unidadeId) {
        return reconhecimentos.stream()
                .filter(r -> r.getUnidade().getId().equals(unidadeId))
                .findFirst();
    }

    public Long getId() {
        return id;
    }

    public Edicao getEdicao() {
        return edicao;
    }

    public String getCpf() {
        return cpf;
    }

    public String getNome() {
        return nome;
    }

    public List<Reconhecimento> getReconhecimentos() {
        return reconhecimentos;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
