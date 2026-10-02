package br.jus.tjgo.goianao.integracao.egesp.connecttj;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Pares de chave gerados na hora, para os testes.
 *
 * <p>Nenhuma chave fica escrita no repositorio — nem de teste. O repositorio e
 * publico, e uma chave versionada "so para teste" e exatamente o que alguem
 * copia para homologacao numa tarde de pressa.
 */
final class ChavesDeTeste {

    private ChavesDeTeste() {
    }

    static KeyPair ed25519() {
        return gerar("Ed25519");
    }

    static KeyPair rsa() {
        return gerar("RSA");
    }

    /** PKCS#8 em PEM, com quebras de linha a cada 64 caracteres, como o openssl grava. */
    static String pem(KeyPair par) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(par.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    private static KeyPair gerar(String algoritmo) {
        try {
            return KeyPairGenerator.getInstance(algoritmo).generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
