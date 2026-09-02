package br.jus.tjgo.goianao.certificado;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Gera o codigo impresso no certificado.
 *
 * <p>Opaco e nao sequencial de proposito: como a verificacao e publica, um
 * codigo previsivel permitiria varrer os certificados emitidos (007/RNF-3). O
 * alfabeto e o de Crockford (sem I, L, O e U), que evita confusao ao ditar ou
 * digitar o codigo, e os grupos de quatro facilitam a leitura no papel.
 */
@Component
public class GeradorCodigoValidacao {

    private static final char[] ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int GRUPOS = 3;
    private static final int TAMANHO_GRUPO = 4;

    private final SecureRandom aleatorio = new SecureRandom();
    private final CertificadoEmitidoRepository repositorio;

    public GeradorCodigoValidacao(CertificadoEmitidoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public String gerar() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            String codigo = sortear();
            if (!repositorio.existsByCodigoValidacao(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException(
                "Não foi possível gerar um código de validação único. Tente novamente.");
    }

    private String sortear() {
        StringBuilder codigo = new StringBuilder(GRUPOS * TAMANHO_GRUPO + GRUPOS - 1);
        for (int grupo = 0; grupo < GRUPOS; grupo++) {
            if (grupo > 0) {
                codigo.append('-');
            }
            for (int i = 0; i < TAMANHO_GRUPO; i++) {
                codigo.append(ALFABETO[aleatorio.nextInt(ALFABETO.length)]);
            }
        }
        return codigo.toString();
    }
}
