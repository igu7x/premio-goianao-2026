package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.certificado.dto.EdicaoOpcaoResposta;
import br.jus.tjgo.goianao.certificado.dto.EmitirRequisicao;
import br.jus.tjgo.goianao.certificado.dto.OpcaoEmissaoResposta;
import br.jus.tjgo.goianao.seguranca.UsuarioAtual;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Meus certificados, na visao do servidor (006). */
@RestController
@RequestMapping("/api/servidor/certificados")
@PreAuthorize("hasRole('SERVIDOR')")
public class EmissaoServidorController {

    private final EmissaoServidorService servico;

    public EmissaoServidorController(EmissaoServidorService servico) {
        this.servico = servico;
    }

    @GetMapping("/edicoes")
    public List<EdicaoOpcaoResposta> edicoes() {
        return servico.edicoesDisponiveis(UsuarioAtual.obrigatorio().cpf());
    }

    @GetMapping
    public List<OpcaoEmissaoResposta> opcoes(@RequestParam(required = false) Long edicaoId) {
        return servico.opcoes(edicaoId, UsuarioAtual.obrigatorio().cpf());
    }

    @PostMapping("/emitir")
    public ResponseEntity<byte[]> emitir(@Valid @RequestBody EmitirRequisicao requisicao) {
        EmissaoService.CertificadoGerado gerado = servico.emitir(
                requisicao.edicaoId(), requisicao.unidadeId(), UsuarioAtual.obrigatorio());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + gerado.nomeArquivo() + "\"")
                .header("X-Codigo-Validacao", gerado.codigoValidacao())
                .body(gerado.pdf());
    }
}
