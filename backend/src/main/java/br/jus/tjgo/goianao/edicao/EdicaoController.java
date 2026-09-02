package br.jus.tjgo.goianao.edicao;

import br.jus.tjgo.goianao.edicao.dto.AtualizarEdicaoRequisicao;
import br.jus.tjgo.goianao.edicao.dto.CriarEdicaoRequisicao;
import br.jus.tjgo.goianao.edicao.dto.EdicaoResposta;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Escrita restrita ao Administrador (002/RNF-1); as leituras servem tambem as
 * telas de emissao, que precisam saber a vigente e as edicoes publicadas.
 */
@RestController
@RequestMapping("/api/edicoes")
public class EdicaoController {

    private final EdicaoService servico;

    public EdicaoController(EdicaoService servico) {
        this.servico = servico;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public EdicaoResposta criar(@Valid @RequestBody CriarEdicaoRequisicao requisicao) {
        return EdicaoResposta.de(servico.criar(requisicao));
    }

    @GetMapping
    public List<EdicaoResposta> listar() {
        return servico.listar().stream().map(EdicaoResposta::de).toList();
    }

    @GetMapping("/publicadas")
    public List<EdicaoResposta> listarPublicadas() {
        return servico.listarPublicadas().stream().map(EdicaoResposta::de).toList();
    }

    /** Edicao vigente, ou {@code 204} quando ainda nao ha uma definida. */
    @GetMapping("/vigente")
    public ResponseEntity<EdicaoResposta> vigente() {
        return servico.vigente()
                .map(edicao -> ResponseEntity.ok(EdicaoResposta.de(edicao)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public EdicaoResposta detalhar(@PathVariable Long id) {
        return EdicaoResposta.de(servico.buscar(id));
    }

    /** Combinacoes selo x tipo que ainda faltam para a edicao poder ser publicada. */
    @GetMapping("/{id}/pendencias-publicacao")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<String> pendenciasPublicacao(@PathVariable Long id) {
        return servico.pendenciasParaPublicar(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public EdicaoResposta atualizar(@PathVariable Long id,
                                    @Valid @RequestBody AtualizarEdicaoRequisicao requisicao) {
        return EdicaoResposta.de(servico.atualizar(id, requisicao));
    }

    @PostMapping("/{id}/publicar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public EdicaoResposta publicar(@PathVariable Long id) {
        return EdicaoResposta.de(servico.publicar(id));
    }

    @PostMapping("/{id}/vigente")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public EdicaoResposta tornarVigente(@PathVariable Long id) {
        return EdicaoResposta.de(servico.tornarVigente(id));
    }
}
