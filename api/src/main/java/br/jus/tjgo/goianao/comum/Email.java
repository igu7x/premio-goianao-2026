package br.jus.tjgo.goianao.comum;

import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utilitarios de e-mail.
 *
 * <p>O e-mail corporativo e a <b>chave que identifica a pessoa</b> em todo o
 * dominio (DI-24): e o que o SSO do tribunal entrega, e e por ele que o sistema
 * encontra reconhecimentos, habilitacoes e certificados. Por isso e sempre
 * gravado normalizado — a mesma pessoa nao pode virar duas por causa de uma
 * letra maiuscula.
 *
 * <p>Como o CPF antes dele, e dado pessoal: para quem so consulta, sai
 * mascarado ({@link #mascarar}).
 */
public final class Email {

    /** Deliberadamente frouxo: quem valida de fato e o SSO, que so entrega e-mails reais. */
    private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Email() {}

    /** Minusculas e sem espacos nas pontas; vazio vira nulo. */
    public static String normalizar(String bruto) {
        if (bruto == null) {
            return null;
        }
        String limpo = bruto.trim().toLowerCase(Locale.ROOT);
        return limpo.isEmpty() ? null : limpo;
    }

    public static boolean valido(String bruto) {
        String email = normalizar(bruto);
        return email != null && email.length() <= 200 && FORMATO.matcher(email).matches();
    }

    /** Normaliza e exige um e-mail valido, com a mensagem que vai para a tela. */
    public static String exigir(String bruto) {
        String email = normalizar(bruto);
        if (email == null) {
            throw new RegraDeNegocioException("Informe o e-mail.");
        }
        if (!valido(email)) {
            throw new RegraDeNegocioException("E-mail inválido.");
        }
        return email;
    }

    /** {@code t***@tjgo.jus.br}: da para reconhecer a pessoa sem entregar o endereco. */
    public static String mascarar(String bruto) {
        String email = normalizar(bruto);
        if (email == null || email.indexOf('@') < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }
}
