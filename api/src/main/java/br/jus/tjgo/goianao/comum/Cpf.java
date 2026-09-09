package br.jus.tjgo.goianao.comum;

/**
 * Utilitarios de CPF. O CPF e dado sensivel (001/RNF-3, 007/RNF-2): nunca deve
 * aparecer em logs, URLs ou em payloads publicos — use {@link #mascarar}.
 */
public final class Cpf {

    private Cpf() {}

    /** Remove qualquer formatacao, deixando apenas digitos. */
    public static String normalizar(String bruto) {
        return bruto == null ? null : bruto.replaceAll("\\D", "");
    }

    /** Valida os digitos verificadores do CPF. */
    public static boolean valido(String bruto) {
        String cpf = normalizar(bruto);
        if (cpf == null || cpf.length() != 11) {
            return false;
        }
        if (cpf.chars().distinct().count() == 1) {
            return false; // sequencias como 00000000000 sao formalmente invalidas
        }
        return digitoVerificador(cpf, 9) == cpf.charAt(9)
                && digitoVerificador(cpf, 10) == cpf.charAt(10);
    }

    private static char digitoVerificador(String cpf, int posicao) {
        int soma = 0;
        int peso = posicao + 1;
        for (int i = 0; i < posicao; i++) {
            soma += (cpf.charAt(i) - '0') * peso--;
        }
        int resto = soma % 11;
        return (char) ('0' + (resto < 2 ? 0 : 11 - resto));
    }

    /** Formata como {@code 000.000.000-00}; devolve o valor cru se nao tiver 11 digitos. */
    public static String formatar(String bruto) {
        String cpf = normalizar(bruto);
        if (cpf == null || cpf.length() != 11) {
            return bruto;
        }
        return cpf.substring(0, 3) + '.' + cpf.substring(3, 6) + '.'
                + cpf.substring(6, 9) + '-' + cpf.substring(9);
    }

    /** Mascara para exibicao/auditoria: {@code ***.000.000-**}. */
    public static String mascarar(String bruto) {
        String cpf = normalizar(bruto);
        if (cpf == null || cpf.length() != 11) {
            return "***";
        }
        return "***." + cpf.substring(3, 6) + '.' + cpf.substring(6, 9) + "-**";
    }
}
