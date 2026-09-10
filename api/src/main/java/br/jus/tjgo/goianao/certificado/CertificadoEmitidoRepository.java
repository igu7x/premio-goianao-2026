package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.comum.TipoCertificado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificadoEmitidoRepository extends JpaRepository<CertificadoEmitido, Long> {

    Optional<CertificadoEmitido> findByCodigoValidacao(String codigoValidacao);

    boolean existsByCodigoValidacao(String codigoValidacao);

    /** O certificado logico: um por (edicao, tipo, pessoa, unidade). */
    Optional<CertificadoEmitido> findByEdicaoIdAndTipoAndEmailEmissorAndUnidadeId(
            Long edicaoId, TipoCertificado tipo, String emailEmissor, Long unidadeId);

    List<CertificadoEmitido> findByEmailEmissorOrderByEmitidoEmDesc(String emailEmissor);

    List<CertificadoEmitido> findByEdicaoIdOrderByEmitidoEmDesc(Long edicaoId);

    long countByEdicaoId(Long edicaoId);
}
