package br.jus.tjgo.goianao.layout;

import br.jus.tjgo.goianao.layout.dto.AtualizarLayoutRequisicao;
import br.jus.tjgo.goianao.layout.dto.LayoutRequisicao;
import br.jus.tjgo.goianao.layout.dto.LayoutResposta;
import br.jus.tjgo.goianao.layout.dto.LayoutsDaEdicaoResposta;
import br.jus.tjgo.goianao.layout.dto.PreviewRequisicao;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Configuracao de layouts: exclusiva do Administrador (003/RNF-1). */
@RestController
@RequestMapping("/api/edicoes/{edicaoId}/layouts")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class LayoutController {

    private final LayoutService servico;

    public LayoutController(LayoutService servico) {
        this.servico = servico;
    }

    @GetMapping
    public LayoutsDaEdicaoResposta listar(@PathVariable Long edicaoId) {
        return new LayoutsDaEdicaoResposta(
                servico.listar(edicaoId).stream().map(LayoutResposta::de).toList(),
                servico.pendencias(edicaoId),
                servico.edicaoEditavel(edicaoId),
                servico.fonteInstitucionalDisponivel());
    }

    @GetMapping("/{layoutId}")
    public LayoutResposta detalhar(@PathVariable Long edicaoId, @PathVariable Long layoutId) {
        return LayoutResposta.de(servico.buscar(edicaoId, layoutId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LayoutResposta> criar(
            @PathVariable Long edicaoId,
            @RequestPart("imagem") MultipartFile imagem,
            @Valid @RequestPart("dados") LayoutRequisicao dados) {

        LayoutCertificado layout = servico.criar(edicaoId, imagem, dados);
        return ResponseEntity.status(HttpStatus.CREATED).body(LayoutResposta.de(layout));
    }

    @PutMapping(path = "/{layoutId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LayoutResposta atualizar(
            @PathVariable Long edicaoId,
            @PathVariable Long layoutId,
            @RequestPart(value = "imagem", required = false) MultipartFile imagem,
            @Valid @RequestPart("dados") AtualizarLayoutRequisicao dados) {

        return LayoutResposta.de(servico.atualizar(edicaoId, layoutId, imagem, dados));
    }

    /** Arte da combinacao, consumida pelo editor visual de posicionamento. */
    @GetMapping("/{layoutId}/imagem")
    public ResponseEntity<byte[]> imagem(@PathVariable Long edicaoId, @PathVariable Long layoutId) {
        LayoutService.Arte arte = servico.arte(edicaoId, layoutId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arte.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(arte.conteudo());
    }

    @PostMapping("/{layoutId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long edicaoId,
                                          @PathVariable Long layoutId,
                                          @RequestBody(required = false) PreviewRequisicao dados) {
        byte[] pdf = servico.preview(edicaoId, layoutId, dados);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"previa-certificado.pdf\"")
                .body(pdf);
    }
}
