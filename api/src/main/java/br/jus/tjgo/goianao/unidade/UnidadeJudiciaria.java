package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Espelho local de uma unidade judiciaria do EGESP (004/RF-1).
 *
 * <p>O {@code nome} e guardado exatamente como veio do EGESP — e ele que sera
 * impresso no certificado. O {@code nomeCanonico} (sem acentos, minusculo,
 * espacos colapsados) e apenas a chave de casamento entre as integracoes e
 * carrega a unicidade.
 */
@Entity
@Table(name = "unidade_judiciaria")
public class UnidadeJudiciaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", length = 300, nullable = false)
    private String nome;

    @Column(name = "nome_canonico", length = 300, nullable = false)
    private String nomeCanonico;

    /**
     * Codigo da unidade no SIEDOS. Nulo enquanto ela nao foi casada com a API
     * corporativa (010): o cadastro nasceu do nome, e e o nome canonico que
     * continua carregando a unicidade.
     */
    @Column(name = "codigo_siedos")
    private Long codigoSiedos;

    @Column(name = "comarca", length = 150)
    private String comarca;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    /**
     * Superior responsavel, designado pelo superadministrador.
     *
     * <p>E propriedade da <b>unidade</b>, nao da edicao: quem responde pela vara
     * nao muda porque o premio mudou de ano. Nulo enquanto ninguem foi
     * designado, que e o estado normal de uma unidade recem-cadastrada.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected UnidadeJudiciaria() {}

    public UnidadeJudiciaria(String nomeDoEgesp) {
        this.nome = nomeDoEgesp;
        this.nomeCanonico = Texto.canonicalizar(nomeDoEgesp);
        this.ativo = true;
        this.criadoEm = LocalDateTime.now();
    }

    /**
     * Adota o nome que a API corporativa passou a usar.
     *
     * <p>Sempre por acao explicita do superadministrador (010): este nome vai
     * impresso no certificado, e trocar sozinho o texto de um documento ja
     * emitido seria inaceitavel. Quem ja emitiu nao e afetado — o certificado
     * guarda o que foi impresso.
     */
    public void renomear(String nomeDoSiedos) {
        this.nome = nomeDoSiedos;
        this.nomeCanonico = Texto.canonicalizar(nomeDoSiedos);
    }

    /** Casa a unidade com a API corporativa. O nome impresso nao muda aqui. */
    public void vincularAoSiedos(Long codigoSiedos, String comarca) {
        this.codigoSiedos = codigoSiedos;
        if (comarca != null && !comarca.isBlank()) {
            this.comarca = comarca;
        }
    }

    public Long getCodigoSiedos() {
        return codigoSiedos;
    }

    public String getComarca() {
        return comarca;
    }

    public void designarResponsavel(Usuario responsavel) {
        this.responsavel = responsavel;
    }

    public Usuario getResponsavel() {
        return responsavel;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeCanonico() {
        return nomeCanonico;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
