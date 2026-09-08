package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.comum.Selo;
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
 * Vinculo de um magistrado a uma unidade com um selo, dentro de uma edicao.
 *
 * <p>Um magistrado pode ter varias unidades, com selos diferentes (004/RF-4), e
 * a mesma unidade pode ser reconhecida por mais de um magistrado (004/RF-10) —
 * dai a regra do maior selo na emissao do servidor.
 */
@Entity
@Table(name = "reconhecimento")
public class Reconhecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "magistrado_id", nullable = false)
    private MagistradoReconhecido magistrado;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private UnidadeJudiciaria unidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "selo", length = 20, nullable = false)
    private Selo selo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Reconhecimento() {}

    public Reconhecimento(MagistradoReconhecido magistrado, UnidadeJudiciaria unidade, Selo selo) {
        this.magistrado = magistrado;
        this.unidade = unidade;
        this.selo = selo;
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public MagistradoReconhecido getMagistrado() {
        return magistrado;
    }

    public UnidadeJudiciaria getUnidade() {
        return unidade;
    }

    public Selo getSelo() {
        return selo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
