package br.jus.tjgo.goianao.layout.render;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Arte de um layout guardada no banco.
 *
 * O conteudo e um {@code byte[]} simples, sem {@code @Lob} de proposito: com
 * {@code @Lob} o Hibernate mapeia para large object (OID) no PostgreSQL, o que
 * cria uma tabela auxiliar, exige transacao para ler e nao sai no dump da mesma
 * forma. Sem a anotacao o mapeamento e VARBINARY, que vira {@code bytea} no
 * PostgreSQL e VARBINARY no H2 — o mesmo codigo serve aos dois.
 */
@Entity
@Table(name = "arte_layout")
public class ArteLayout {

    @Id
    @Column(length = 64)
    private String referencia;

    @Column(nullable = false)
    private byte[] conteudo;

    @Column(nullable = false, length = 10)
    private String extensao;

    @Column(nullable = false)
    private long tamanho;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected ArteLayout() {
        // exigido pelo JPA
    }

    public ArteLayout(String referencia, byte[] conteudo, String extensao) {
        this.referencia = referencia;
        this.conteudo = conteudo;
        this.extensao = extensao;
        this.tamanho = conteudo.length;
        this.criadoEm = LocalDateTime.now();
    }

    public String getReferencia() {
        return referencia;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public String getExtensao() {
        return extensao;
    }

    public long getTamanho() {
        return tamanho;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
