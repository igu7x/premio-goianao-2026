package br.jus.tjgo.goianao.layout.render;

/**
 * Porta de armazenamento das artes. Mantem o banco leve e permite trocar o
 * filesystem por um bucket S3-compativel sem tocar no dominio (003/plan).
 */
public interface ImageStorage {

    /** Guarda o conteudo e devolve a referencia usada para recupera-lo. */
    String salvar(byte[] conteudo, String extensao);

    byte[] ler(String referencia);

    void remover(String referencia);
}
