package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.comum.TipoCertificado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificadoEmitidoRepository extends JpaRepository<CertificadoEmitido, Long> {

    Optional<CertificadoEmitido> findByCodigoValidacao(String codigoValidacao);

    boolean existsByCodigoValidacao(String codigoValidacao);

    /** O certificado logico: um por (edicao, tipo, pessoa, unidade). */
    Optional<CertificadoEmitido> findByEdicaoIdAndTipoAndCpfEmissorAndUnidadeId(
            Long edicaoId, TipoCertificado tipo, String cpfEmissor, Long unidadeId);

    List<CertificadoEmitido> findByCpfEmissorOrderByEmitidoEmDesc(String cpfEmissor);

    List<CertificadoEmitido> findByEdicaoIdOrderByEmitidoEmDesc(Long edicaoId);

    long countByEdicaoId(Long edicaoId);
}
