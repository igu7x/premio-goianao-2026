package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.comum.Texto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected UnidadeJudiciaria() {}

    public UnidadeJudiciaria(String nomeDoEgesp) {
        this.nome = nomeDoEgesp;
        this.nomeCanonico = Texto.canonicalizar(nomeDoEgesp);
        this.ativo = true;
        this.criadoEm = LocalDateTime.now();
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
