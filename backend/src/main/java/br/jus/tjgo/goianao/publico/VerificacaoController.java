package br.jus.tjgo.goianao.publico;

import br.jus.tjgo.goianao.certificado.CertificadoEmitidoRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conferencia publica de autenticidade (007). Sem login: e usada por RH, bancas
 * e cidadaos a partir do QR ou do codigo impresso no certificado.
 */
@RestController
@RequestMapping("/api/public/certificados")
public class VerificacaoController {

    private final CertificadoEmitidoRepository repositorio;
    private final LimitadorDeTaxa limitador;

    public VerificacaoController(CertificadoEmitidoRepository repositorio,
                                 LimitadorDeTaxa limitador) {
        this.repositorio = repositorio;
        this.limitador = limitador;
    }

    @GetMapping("/{codigo}")
    @Transactional(readOnly = true)
    public ResponseEntity<VerificacaoResposta> verificar(@PathVariable String codigo,
                                                         HttpServletRequest requisicao) {
        if (!limitador.permitir(origem(requisicao))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(VerificacaoResposta.naoEncontrado(codigo));
        }

        String normalizado = normalizar(codigo);

        return repositorio.findByCodigoValidacao(normalizado)
                .map(certificado -> ResponseEntity.ok(VerificacaoResposta.valido(certificado)))
                // Resposta uniforme: codigo inexistente nao se distingue de
                // qualquer outra falha de conferencia (007/CA-2).
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(VerificacaoResposta.naoEncontrado(normalizado)));
    }

    /** Aceita o codigo digitado com ou sem hifens, em qualquer caixa. */
    private String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        String limpo = codigo.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (limpo.length() != 12) {
            return codigo.trim().toUpperCase(Locale.ROOT);
        }
        return limpo.substring(0, 4) + "-" + limpo.substring(4, 8) + "-" + limpo.substring(8);
    }

    private String origem(HttpServletRequest requisicao) {
        String encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return requisicao.getRemoteAddr();
    }
}
