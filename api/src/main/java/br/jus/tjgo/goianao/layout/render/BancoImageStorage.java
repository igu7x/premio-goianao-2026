package br.jus.tjgo.goianao.layout.render;

import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Armazenamento das artes no proprio banco — o padrao.
 *
 * A escolha nao e por simplicidade: e pelo ciclo de vida. A arte e parte da
 * autenticidade do certificado, porque uma reemissao feita daqui a anos precisa
 * sair identica a original. Guardada aqui, ela e restaurada pelo mesmo backup
 * que restaura a tabela {@code certificado_emitido} — nao existe o cenario em
 * que o registro da emissao volta e a arte nao.
 *
 * De quebra, mantem o pod sem estado em disco, o que dispensa volume
 * persistente no OpenShift e a discussao entre RWO e RWX.
 *
 * O volume justifica: sao 8 artes por edicao — quatro selos por dois tipos de
 * certificado.
 */
@Component
@ConditionalOnProperty(name = "goianao.storage.tipo", havingValue = "banco", matchIfMissing = true)
public class BancoImageStorage implements ImageStorage {

    private final ArteLayoutRepository artes;

    public BancoImageStorage(ArteLayoutRepository artes) {
        this.artes = artes;
    }

    @Override
    @Transactional
    public String salvar(byte[] conteudo, String extensao) {
        String limpa = normalizarExtensao(extensao);
        String referencia = UUID.randomUUID() + "." + limpa;
        artes.save(new ArteLayout(referencia, conteudo, limpa));
        return referencia;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] ler(String referencia) {
        return artes.findById(referencia)
                .map(ArteLayout::getConteudo)
                .orElseThrow(() -> new NaoEncontradoException(
                        "Arte do certificado não encontrada no armazenamento."));
    }

    @Override
    @Transactional
    public void remover(String referencia) {
        artes.deleteById(referencia);
    }

    private String normalizarExtensao(String extensao) {
        if (extensao == null || extensao.isBlank()) {
            return "png";
        }
        String limpa = extensao.toLowerCase().replaceAll("[^a-z0-9]", "");
        return limpa.isEmpty() ? "png" : limpa;
    }
}
