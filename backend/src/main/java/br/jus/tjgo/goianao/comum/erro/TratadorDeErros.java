package br.jus.tjgo.goianao.comum.erro;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converte as excecoes do dominio no corpo de erro unico da API.
 *
 * <p>Trata tambem as excecoes padrao do Spring MVC (corpo ilegivel, tipo de
 * parametro errado, metodo ou rota inexistente). Sem elas, qualquer requisicao
 * malformada cairia no tratador generico e voltaria como <b>500</b> — dizendo ao
 * cliente que o servidor falhou quando, na verdade, foi a requisicao que veio
 * errada. Alem de enganoso, isso enchia o log de stack traces por erro de
 * digitacao.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<ErroResposta> naoEncontrado(NaoEncontradoException e) {
        return resposta(HttpStatus.NOT_FOUND, "nao_encontrado", e.getMessage(), List.of());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResposta> conflito(ConflitoException e) {
        return resposta(HttpStatus.CONFLICT, "conflito", e.getMessage(), e.detalhes());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResposta> regraDeNegocio(RegraDeNegocioException e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "regra_de_negocio", e.getMessage(), List.of());
    }

    /**
     * A excecao do dominio explica <i>por que</i> o acesso foi negado (unidade
     * alheia, edicao nao vigente); essa mensagem ajuda o usuario e nao revela
     * nada que ele ja nao saiba sobre o proprio vinculo.
     */
    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResposta> credenciaisInvalidas(CredenciaisInvalidasException e) {
        return resposta(HttpStatus.UNAUTHORIZED, "credenciais_invalidas", e.getMessage(), List.of());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResposta> acessoNegado(AcessoNegadoException e) {
        return resposta(HttpStatus.FORBIDDEN, "acesso_negado", e.getMessage(), List.of());
    }

    /** Ja a negativa vinda do RBAC do Spring fica generica de proposito. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> acessoNegadoPorPapel(AccessDeniedException e) {
        return resposta(HttpStatus.FORBIDDEN, "acesso_negado",
                "Você não tem permissão para esta operação.", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException e) {
        List<String> campos = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos",
                "Há campos inválidos na requisição.", campos);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> argumentoInvalido(IllegalArgumentException e) {
        return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos", e.getMessage(), List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroResposta> violacaoDeRestricao(ConstraintViolationException e) {
        List<String> campos = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos",
                "Há campos inválidos na requisição.", campos);
    }

    /**
     * Corpo ausente, JSON quebrado ou valor fora do conjunto aceito por um enum.
     * Quando da para identificar o campo, a mensagem diz qual e e quais valores
     * sao aceitos — evita o vaivem de tentativa e erro em quem consome a API.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> corpoIlegivel(HttpMessageNotReadableException e) {
        InvalidFormatException formato = procurarFormatoInvalido(e);
        if (formato != null) {
            String campo = formato.getPath().stream()
                    .map(referencia -> referencia.getFieldName() == null
                            ? "[" + referencia.getIndex() + "]"
                            : referencia.getFieldName())
                    .reduce((a, b) -> a + "." + b)
                    .orElse("campo");

            String aceitos = "";
            if (formato.getTargetType() != null && formato.getTargetType().isEnum()) {
                aceitos = " Valores aceitos: " + String.join(", ",
                        java.util.Arrays.stream(formato.getTargetType().getEnumConstants())
                                .map(String::valueOf).toList()) + ".";
            }
            return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos",
                    "Valor invalido para o campo \"" + campo + "\"." + aceitos, List.of());
        }
        return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos",
                "Nao foi possivel ler o corpo da requisicao. Envie um JSON valido.", List.of());
    }

    /** Ex.: {@code /api/edicoes/abc} — o id na rota nao e um numero. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tipoIncompativel(MethodArgumentTypeMismatchException e) {
        return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos",
                "Valor invalido para o parametro \"" + e.getName() + "\".", List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResposta> parametroAusente(MissingServletRequestParameterException e) {
        return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos",
                "Parametro obrigatorio ausente: \"" + e.getParameterName() + "\".", List.of());
    }

    /** Multipart sem a parte esperada (a arte do layout, a planilha de importacao). */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErroResposta> parteAusente(MissingServletRequestPartException e) {
        return resposta(HttpStatus.BAD_REQUEST, "dados_invalidos",
                "Arquivo obrigatorio ausente no envio: \"" + e.getRequestPartName() + "\".",
                List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResposta> tipoNaoSuportado(HttpMediaTypeNotSupportedException e) {
        return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "tipo_nao_suportado",
                "Formato de conteudo nao suportado por este endpoint.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> metodoNaoSuportado(HttpRequestMethodNotSupportedException e) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "metodo_nao_permitido",
                "O metodo " + e.getMethod() + " nao e aceito neste endereco.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException e) {
        return resposta(HttpStatus.NOT_FOUND, "nao_encontrado",
                "Endereco nao encontrado.", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> uploadGrande(MaxUploadSizeExceededException e) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "arquivo_grande",
                "O arquivo excede o tamanho máximo permitido.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException e) {
        log.warn("Violacao de integridade: {}", e.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "conflito",
                "A operação viola uma restrição de unicidade ou integridade.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "erro_interno",
                "Ocorreu um erro inesperado. Tente novamente.", List.of());
    }

    /**
     * O Jackson aninha a causa real quando o valor invalido esta dentro de um
     * record ou de uma lista, entao nao basta olhar {@code getCause()} — e
     * preciso percorrer a cadeia ate achar (ou nao) o formato invalido.
     */
    private InvalidFormatException procurarFormatoInvalido(Throwable e) {
        for (Throwable atual = e; atual != null; atual = atual.getCause()) {
            if (atual instanceof InvalidFormatException formato) {
                return formato;
            }
            if (atual.getCause() == atual) {
                break;
            }
        }
        return null;
    }

    private ResponseEntity<ErroResposta> resposta(HttpStatus status, String erro,
                                                  String mensagem, List<String> detalhes) {
        return ResponseEntity.status(status)
                .body(ErroResposta.de(status.value(), erro, mensagem, detalhes));
    }
}
