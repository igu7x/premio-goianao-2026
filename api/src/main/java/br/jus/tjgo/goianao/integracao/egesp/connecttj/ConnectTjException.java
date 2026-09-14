package br.jus.tjgo.goianao.integracao.egesp.connecttj;

/** Falha ao falar com a API corporativa. */
public class ConnectTjException extends RuntimeException {

    public ConnectTjException(String mensagem) {
        super(mensagem);
    }

    public ConnectTjException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
