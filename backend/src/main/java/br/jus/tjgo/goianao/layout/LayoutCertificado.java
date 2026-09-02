package br.jus.tjgo.goianao.layout;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.edicao.Edicao;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Arte + posicoes de um certificado, por edicao x selo x tipo (003/RF-1).
 *
 * <p>Editavel enquanto a edicao esta em rascunho; travado ao publicar, o que
 * preserva a fidelidade das reemissoes (003/RF-8, constituicao principio 5).
 */
@Entity
@Table(name = "layout_certificado")
public class LayoutCertificado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edicao_id", nullable = false)
    private Edicao edicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "selo", length = 20, nullable = false)
    private Selo selo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoCertificado tipo;

    @Column(name = "imagem_ref", length = 300, nullable = false)
    private String imagemRef;

    @Column(name = "imagem_largura", nullable = false)
    private int imagemLargura;

    @Column(name = "imagem_altura", nullable = false)
    private int imagemAltura;

    @Convert(converter = JsonAreas.AreaTextoConverter.class)
    @Column(name = "area_nome", length = 2000, nullable = false)
    private AreaTexto areaNome;

    @Convert(converter = JsonAreas.AreaTextoConverter.class)
    @Column(name = "area_unidade", length = 2000, nullable = false)
    private AreaTexto areaUnidade;

    @Convert(converter = JsonAreas.AreaCodigoConverter.class)
    @Column(name = "area_codigo", length = 2000, nullable = false)
    private AreaCodigo areaCodigo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    protected LayoutCertificado() {}

    public LayoutCertificado(Edicao edicao, Selo selo, TipoCertificado tipo,
                             String imagemRef, int imagemLargura, int imagemAltura,
                             AreaTexto areaNome, AreaTexto areaUnidade, AreaCodigo areaCodigo) {
        this.edicao = edicao;
        this.selo = selo;
        this.tipo = tipo;
        this.criadoEm = LocalDateTime.now();
        substituirArte(imagemRef, imagemLargura, imagemAltura);
        redefinirAreas(areaNome, areaUnidade, areaCodigo);
    }

    public final void substituirArte(String imagemRef, int largura, int altura) {
        this.imagemRef = imagemRef;
        this.imagemLargura = largura;
        this.imagemAltura = altura;
        this.atualizadoEm = LocalDateTime.now();
    }

    public final void redefinirAreas(AreaTexto areaNome, AreaTexto areaUnidade, AreaCodigo areaCodigo) {
        this.areaNome = areaNome;
        this.areaUnidade = areaUnidade;
        this.areaCodigo = areaCodigo;
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Edicao getEdicao() {
        return edicao;
    }

    public Selo getSelo() {
        return selo;
    }

    public TipoCertificado getTipo() {
        return tipo;
    }

    public String getImagemRef() {
        return imagemRef;
    }

    public int getImagemLargura() {
        return imagemLargura;
    }

    public int getImagemAltura() {
        return imagemAltura;
    }

    public AreaTexto getAreaNome() {
        return areaNome;
    }

    public AreaTexto getAreaUnidade() {
        return areaUnidade;
    }

    public AreaCodigo getAreaCodigo() {
        return areaCodigo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
