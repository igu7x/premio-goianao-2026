package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.seguranca.Papel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Usuario do sistema: quem entra, com que papel e lotado onde.
 *
 * <p>O <b>e-mail corporativo</b> e a chave que liga este cadastro ao resto do
 * dominio — magistrado reconhecido, servidor habilitado e certificado emitido
 * sao todos indexados por ele, e e ele que o SSO entrega (DI-24). Por isso e
 * obrigatorio, unico e nao muda depois do cadastro. O <b>CPF</b> e opcional e
 * so informativo.
 *
 * <p>A <b>senha</b> e opcional por desenho: hoje se entra por e-mail e senha,
 * mas quando o SSO assumir a autenticacao os usuarios continuarao existindo,
 * sem senha alguma. Guardada sempre como hash BCrypt.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cpf", length = 11)
    private String cpf;

    @Column(name = "nome", length = 200, nullable = false)
    private String nome;

    @Column(name = "email", length = 200, nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    @Column(name = "unidade_lotacao", length = 300)
    private String unidadeLotacao;

    /** Só faz sentido para magistrado; nulo nos demais papeis. */
    @Column(name = "area_atuacao", length = 150)
    private String areaAtuacao;

    /** Matricula no RH e login de rede, preenchidos pela integracao (010). */
    @Column(name = "matricula")
    private Long matricula;

    @Column(name = "login_ad", length = 100)
    private String loginAd;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papel", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "papel", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    protected Usuario() {
        // exigido pelo JPA
    }

    public Usuario(String email, String nome, String cpf, Set<Papel> papeis) {
        this.email = email;
        this.nome = nome;
        this.cpf = cpf;
        this.papeis = EnumSet.copyOf(papeis);
        this.ativo = true;
        this.criadoEm = LocalDateTime.now();
    }

    /** O e-mail fica de fora: ele e a chave, e troca-lo seria trocar a pessoa. */
    public void alterarDados(String nome, String cpf, String unidadeLotacao,
                             String areaAtuacao, Set<Papel> papeis) {
        this.nome = nome;
        this.cpf = cpf;
        this.unidadeLotacao = unidadeLotacao;
        this.areaAtuacao = areaAtuacao;
        this.papeis = EnumSet.copyOf(papeis);
        this.atualizadoEm = LocalDateTime.now();
    }

    /** Acrescenta um papel sem mexer nos demais. */
    public void concederPapel(Papel papel) {
        this.papeis.add(papel);
        this.atualizadoEm = LocalDateTime.now();
    }

    public void revogarPapel(Papel papel) {
        this.papeis.remove(papel);
        this.atualizadoEm = LocalDateTime.now();
    }

    public void definirLotacao(String unidadeLotacao, String areaAtuacao) {
        this.unidadeLotacao = unidadeLotacao;
        this.areaAtuacao = areaAtuacao;
    }

    /**
     * Dados que o RH manda no login (010).
     *
     * <p>O e-mail fica de fora: ele e a chave da pessoa (DI-24), e troca-lo
     * seria trocar a pessoa. Papeis, responsabilidade por unidade e area de
     * atuacao tambem: sao decisoes do superadministrador, nao do RH.
     */
    public void atualizarPeloRh(String nome, String cpf, Long matricula, String loginAd,
                                String unidadeLotacao) {
        if (nome != null && !nome.isBlank()) {
            this.nome = nome;
        }
        if (cpf != null && !cpf.isBlank()) {
            this.cpf = cpf;
        }
        if (matricula != null) {
            this.matricula = matricula;
        }
        if (loginAd != null && !loginAd.isBlank()) {
            this.loginAd = loginAd;
        }
        if (unidadeLotacao != null && !unidadeLotacao.isBlank()) {
            this.unidadeLotacao = unidadeLotacao;
        }
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getMatricula() {
        return matricula;
    }

    public String getLoginAd() {
        return loginAd;
    }

    /** Recebe o hash pronto: a entidade nunca vê a senha em claro. */
    public void definirSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void ativar() {
        this.ativo = true;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void desativar() {
        this.ativo = false;
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCpf() {
        return cpf;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public String getUnidadeLotacao() {
        return unidadeLotacao;
    }

    public String getAreaAtuacao() {
        return areaAtuacao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<Papel> getPapeis() {
        return papeis;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
