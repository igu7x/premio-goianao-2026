package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
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
 * Registro de um certificado <b>logico</b> — a combinacao (edicao, tipo, pessoa,
 * unidade). O PDF nao e guardado: e regerado sob demanda a partir do layout
 * travado da edicao, o que mantem a reemissao fiel sem armazenar arquivos.
 *
 * <p>O {@code codigoValidacao} e criado uma unica vez e <b>nunca muda</b>: quem
 * conferiu um certificado impresso ha meses continua encontrando o mesmo
 * registro (005/RF-10, 006/RF-9, 007/RF-5).
 */
@Entity
@Table(name = "certificado_emitido")
public class CertificadoEmitido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoCertificado tipo;

    /**
     * Quem emitiu, pelo e-mail corporativo (DI-24). A coluna {@code cpf_emissor}
     * continua na tabela, nula daqui em diante, com o que foi gravado antes.
     */
    @Column(name = "email_emissor", length = 200, nullable = false)
    private String emailEmissor;

    @Column(name = "nome_emissor", length = 200, nullable = false)
    private String nomeEmissor;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private UnidadeJudiciaria unidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "selo", length = 20, nullable = false)
    private Selo selo;

    @Column(name = "codigo_validacao", length = 32, nullable = false, updatable = false)
    private String codigoValidacao;

    @Column(name = "emitido_em", nullable = false)
    private LocalDateTime emitidoEm;

    @Column(name = "reemitido_em")
    private LocalDateTime reemitidoEm;

    @Column(name = "total_emissoes", nullable = false)
    private int totalEmissoes;

    protected CertificadoEmitido() {}

    public CertificadoEmitido(Edicao edicao, TipoCertificado tipo, String emailEmissor,
                              String nomeEmissor, UnidadeJudiciaria unidade, Selo selo,
                              String codigoValidacao) {
        this.edicao = edicao;
        this.tipo = tipo;
        this.emailEmissor = emailEmissor;
        this.nomeEmissor = nomeEmissor;
        this.unidade = unidade;
        this.selo = selo;
        this.codigoValidacao = codigoValidacao;
        this.emitidoEm = LocalDateTime.now();
        this.totalEmissoes = 1;
    }

    /**
     * Registra uma reemissao. Nome e selo sao reavaliados porque podem ter
     * mudado legitimamente: o nome do servidor vem do SSO a cada emissao, e o
     * maior selo da unidade e recalculado na edicao vigente (009).
     */
    public void registrarReemissao(String nomeEmissor, Selo selo) {
        this.nomeEmissor = nomeEmissor;
        this.selo = selo;
        this.reemitidoEm = LocalDateTime.now();
        this.totalEmissoes++;
    }

    public Long getId() {
        return id;
    }

    public Edicao getEdicao() {
        return edicao;
    }

    public TipoCertificado getTipo() {
        return tipo;
    }

    public String getEmailEmissor() {
        return emailEmissor;
    }

    public String getNomeEmissor() {
        return nomeEmissor;
    }

    public UnidadeJudiciaria getUnidade() {
        return unidade;
    }

    public Selo getSelo() {
        return selo;
    }

    public String getCodigoValidacao() {
        return codigoValidacao;
    }

    public LocalDateTime getEmitidoEm() {
        return emitidoEm;
    }

    public LocalDateTime getReemitidoEm() {
        return reemitidoEm;
    }

    public int getTotalEmissoes() {
        return totalEmissoes;
    }
}
