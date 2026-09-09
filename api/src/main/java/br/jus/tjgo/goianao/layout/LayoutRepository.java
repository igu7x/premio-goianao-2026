package br.jus.tjgo.goianao.layout;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutRepository extends JpaRepository<LayoutCertificado, Long> {

    List<LayoutCertificado> findByEdicaoIdOrderBySeloAscTipoAsc(Long edicaoId);

    Optional<LayoutCertificado> findByEdicaoIdAndSeloAndTipo(
            Long edicaoId, Selo selo, TipoCertificado tipo);

    boolean existsByEdicaoIdAndSeloAndTipo(Long edicaoId, Selo selo, TipoCertificado tipo);

    long countByEdicaoId(Long edicaoId);
}
