package br.jus.tjgo.goianao.integracao.egesp.connecttj;

/**
 * Segura o ritmo das chamadas a API corporativa no teto combinado com a equipe
 * dela (6 por segundo, por padrao).
 *
 * <p>O limite importa porque uma unica acao da tela vira dezenas de chamadas:
 * comparar uma unidade de trinta lotados busca a lotacao, depois cada matricula
 * e, para quem nao tem e-mail, a conta no AD. Sem freio isso sai numa rajada de
 * um segundo — o tipo de vizinho que faz a equipe do outro lado cortar o
 * acesso.
 *
 * <p>O espacamento e fixo entre chamadas consecutivas, e nao uma janela que
 * permite rajada e depois pune: assim a varredura fica previsivel (trinta
 * pessoas levam alguns segundos) em vez de rapida e as vezes recusada.
 */
class RitmoDeChamadas {

    private final long intervaloMinimoNs;
    private long proximaLiberacaoNs;

    RitmoDeChamadas(int porSegundo) {
        this.intervaloMinimoNs = porSegundo < 1 ? 0L : 1_000_000_000L / porSegundo;
        this.proximaLiberacaoNs = System.nanoTime();
    }

    /**
     * Espera, se preciso, ate a proxima chamada ser permitida.
     *
     * <p>Sincronizado de proposito: o teto e do sistema inteiro, nao de cada
     * usuario. Dois administradores comparando unidades ao mesmo tempo dividem
     * a mesma cota.
     */
    void aguardarVez() {
        long esperaNs;
        synchronized (this) {
            long agora = System.nanoTime();
            if (proximaLiberacaoNs < agora) {
                proximaLiberacaoNs = agora;
            }
            esperaNs = proximaLiberacaoNs - agora;
            proximaLiberacaoNs += intervaloMinimoNs;
        }
        if (esperaNs <= 0) {
            return;
        }
        try {
            Thread.sleep(esperaNs / 1_000_000L, (int) (esperaNs % 1_000_000L));
        } catch (InterruptedException e) {
            // Quem interrompeu quer parar: repassa o sinal e desiste da espera.
            Thread.currentThread().interrupt();
        }
    }
}
