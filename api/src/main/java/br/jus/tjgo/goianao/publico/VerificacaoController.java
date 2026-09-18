package br.jus.tjgo.goianao.publico;

import br.jus.tjgo.goianao.certificado.CertificadoEmitidoRepository;
import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes;
import br.jus.tjgo.goianao.edicao.base.EdicaoCorrente;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conferencia publica de autenticidade (007). Sem login: e usada por RH, bancas
 * e cidadaos a partir do QR ou do codigo impresso no certificado.
 *
 * <p>Sem login tambem nao ha edicao, e cada edicao tem a sua base (011). Por
 * isso a conferencia tem um passo a mais desde entao: primeiro o indice diz em
 * qual edicao aquele codigo foi emitido, depois a busca acontece dentro dela.
 * Um certificado de 2026 continua sendo conferido em 2030, com os dados de
 * 2026 — que e a razao de ser desta tela.
 */
@RestController
@RequestMapping("/api/public/certificados")
public class VerificacaoController {

    private final CertificadoEmitidoRepository repositorio;
    private final IndiceDeVerificacao indice;
    private final CatalogoDeEdicoes catalogo;
    private final LimitadorDeTaxa limitador;

    public VerificacaoController(CertificadoEmitidoRepository repositorio,
                                 IndiceDeVerificacao indice,
                                 CatalogoDeEdicoes catalogo,
                                 LimitadorDeTaxa limitador) {
        this.repositorio = repositorio;
        this.indice = indice;
        this.catalogo = catalogo;
        this.limitador = limitador;
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<VerificacaoResposta> verificar(@PathVariable String codigo,
                                                         HttpServletRequest requisicao) {
        if (!limitador.permitir(origem(requisicao))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(VerificacaoResposta.naoEncontrado(codigo));
        }

        String normalizado = normalizar(codigo);

        return procurar(normalizado)
                .map(resposta -> ResponseEntity.ok(resposta))
                // Resposta uniforme: codigo inexistente nao se distingue de
                // qualquer outra falha de conferencia (007/CA-2). Codigo que o
                // indice nao conhece cai aqui pelo mesmo caminho.
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(VerificacaoResposta.naoEncontrado(normalizado)));
    }

    /** Onde o codigo foi emitido, e o certificado la dentro. */
    private Optional<VerificacaoResposta> procurar(String codigo) {
        return indice.edicaoDe(codigo)
                .flatMap(catalogo::schemaDe)
                .flatMap(schema -> EdicaoCorrente.executarEm(schema,
                        () -> repositorio.findByCodigoValidacao(codigo)
                                .map(VerificacaoResposta::valido)));
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
