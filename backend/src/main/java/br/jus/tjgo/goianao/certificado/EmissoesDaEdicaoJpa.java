package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.layout.EmissoesDaEdicao;
import org.springframework.stereotype.Component;

/** Adaptador da porta {@link EmissoesDaEdicao} sobre o repositorio de emissoes. */
@Component
public class EmissoesDaEdicaoJpa implements EmissoesDaEdicao {

    private final CertificadoEmitidoRepository certificados;

    public EmissoesDaEdicaoJpa(CertificadoEmitidoRepository certificados) {
        this.certificados = certificados;
    }

    @Override
    public boolean houveEmissao(Long edicaoId) {
        return certificados.countByEdicaoId(edicaoId) > 0;
    }
}
