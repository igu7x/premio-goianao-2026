package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.usuario.dto.AtualizarUsuarioRequisicao;
import br.jus.tjgo.goianao.usuario.dto.UsuarioRequisicao;
import br.jus.tjgo.goianao.usuario.dto.UsuarioResposta;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de usuarios. Exclusivo do superadministrador — e o unico modulo com
 * essa restricao, porque criar usuario e conceder acesso.
 */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('SUPERADMIN')")
public class UsuarioController {

    private final UsuarioService servico;
    private final br.jus.tjgo.goianao.usuario.atualizacao.AtualizacaoDaBaseDeUsuarios atualizacao;

    public UsuarioController(UsuarioService servico,
            br.jus.tjgo.goianao.usuario.atualizacao.AtualizacaoDaBaseDeUsuarios atualizacao) {
        this.servico = servico;
        this.atualizacao = atualizacao;
    }

    @GetMapping
    public List<UsuarioResposta> listar() {
        return servico.listar().stream().map(UsuarioResposta::de).toList();
    }

    @PostMapping
    public ResponseEntity<UsuarioResposta> criar(@Valid @RequestBody UsuarioRequisicao dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UsuarioResposta.de(servico.criar(dados)));
    }

    @PutMapping("/{id}")
    public UsuarioResposta atualizar(@PathVariable Long id,
                                     @Valid @RequestBody AtualizarUsuarioRequisicao dados) {
        return UsuarioResposta.de(servico.atualizar(id, dados));
    }

    /**
     * Dispara a atualizacao da base de usuarios pelo RH, em segundo plano.
     *
     * Responde na hora com o estado inicial: a varredura leva de minutos a
     * dezenas de minutos, e a tela acompanha pelo GET abaixo.
     */
    @PostMapping("/atualizacao-rh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public br.jus.tjgo.goianao.usuario.atualizacao.SituacaoDaAtualizacao iniciarAtualizacao(
            @RequestBody AtualizacaoRequisicao dados) {
        return atualizacao.iniciar(dados.escopo());
    }

    @GetMapping("/atualizacao-rh")
    public br.jus.tjgo.goianao.usuario.atualizacao.SituacaoDaAtualizacao situacaoDaAtualizacao() {
        return atualizacao.situacao();
    }

    public record AtualizacaoRequisicao(
            @jakarta.validation.constraints.NotNull(message = "escolha o escopo")
            br.jus.tjgo.goianao.usuario.atualizacao.EscopoDaAtualizacao escopo) {}

    /**
     * Apaga o usuario, quando nada esta preso a ele. Havendo historico, a
     * resposta e 409 com o motivo — e o caminho passa a ser a desativacao.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        servico.excluir(id);
    }

    @PutMapping("/{id}/ativacao")
    public UsuarioResposta alternarAtivacao(@PathVariable Long id,
                                            @RequestBody AtivacaoRequisicao dados) {
        return UsuarioResposta.de(servico.alternarAtivacao(id, dados.ativo()));
    }

    /** Quem administra o cadastro hoje. */
    @GetMapping("/superadmins")
    public List<UsuarioResposta> superadmins() {
        return servico.superadmins().stream().map(UsuarioResposta::de).toList();
    }

    /**
     * Promove por e-mail um usuario que ja existe.
     *
     * Promover é conceder papel a alguem conhecido, nao cadastrar gente nova —
     * por isso a entrada e so o e-mail, que e o identificador que o
     * superadministrador tem em maos.
     */
    @PostMapping("/superadmins")
    public UsuarioResposta promover(@Valid @RequestBody PromocaoRequisicao dados) {
        return UsuarioResposta.de(servico.promoverASuperadmin(dados.email()));
    }

    @DeleteMapping("/superadmins/{id}")
    public UsuarioResposta revogar(@PathVariable Long id) {
        return UsuarioResposta.de(servico.revogarSuperadmin(id));
    }

    public record AtivacaoRequisicao(boolean ativo) {}

    public record PromocaoRequisicao(
            @jakarta.validation.constraints.NotBlank(message = "informe o e-mail")
            @jakarta.validation.constraints.Email(message = "e-mail inválido")
            String email) {}
}
