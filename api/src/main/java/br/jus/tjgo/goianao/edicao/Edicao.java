package br.jus.tjgo.goianao.edicao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Realizacao anual do premio. Ancora todos os cadastros e emissoes. */
@Entity
@Table(name = "edicao")
public class Edicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ano", nullable = false)
    private Integer ano;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusEdicao status;

    @Column(name = "vigente", nullable = false)
    private boolean vigente;

    /**
     * Coluna espelho de {@link #vigente}: 'S' quando vigente, {@code null} caso
     * contrario. Como NULLs sao distintos num indice unico, e ela que garante no
     * banco a invariante "no maximo uma edicao vigente" (002/RF-5), inclusive sob
     * concorrencia. Nao e exposta pela API.
     */
    @Column(name = "vigente_unico", length = 1)
    private String vigenteUnico;

    /**
     * Onde ficam os dados desta edicao (feature 011).
     *
     * <p>Cada edicao tem a sua base, num schema proprio. Guardar o nome aqui, em
     * vez de deriva-lo do ano toda vez, e o que permite mudar a convencao sem
     * perder o rastro das edicoes ja criadas.
     */
    @Column(name = "schema_dados", length = 63)
    private String schemaDados;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    protected Edicao() {}

    public Edicao(Integer ano, String descricao, String schemaDados) {
        this.ano = ano;
        this.descricao = descricao;
        this.schemaDados = schemaDados;
        this.status = StatusEdicao.RASCUNHO;
        this.vigente = false;
        this.vigenteUnico = null;
        this.criadoEm = LocalDateTime.now();
    }

    public void publicar() {
        this.status = StatusEdicao.PUBLICADA;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void definirVigencia(boolean vigente) {
        this.vigente = vigente;
        this.vigenteUnico = vigente ? "S" : null;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void alterarDescricao(String descricao) {
        this.descricao = descricao;
        this.atualizadoEm = LocalDateTime.now();
    }

    public boolean estaPublicada() {
        return status == StatusEdicao.PUBLICADA;
    }

    public boolean estaEmRascunho() {
        return status == StatusEdicao.RASCUNHO;
    }

    /**
     * Edicao aceita **inclusoes** de reconhecidos quando esta em rascunho ou
     * quando e a vigente (feature 009). Editar/remover continua so em rascunho.
     */
    public boolean aceitaInclusoes() {
        return estaEmRascunho() || (estaPublicada() && vigente);
    }

    public Long getId() {
        return id;
    }

    public Integer getAno() {
        return ano;
    }

    public String getDescricao() {
        return descricao;
    }

    public StatusEdicao getStatus() {
        return status;
    }

    public boolean isVigente() {
        return vigente;
    }

    /** O schema onde vive a base desta edicao (011). */
    public String getSchemaDados() {
        return schemaDados;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
