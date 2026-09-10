package br.jus.tjgo.goianao.magistrado;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MagistradoRepository extends JpaRepository<MagistradoReconhecido, Long> {

    List<MagistradoReconhecido> findByEdicaoIdOrderByNomeAsc(Long edicaoId);

    Optional<MagistradoReconhecido> findByEdicaoIdAndEmail(Long edicaoId, String email);

    boolean existsByEdicaoIdAndEmail(Long edicaoId, String email);

    boolean existsByEmail(String email);

    /** Ids das edicoes publicadas em que o e-mail tem reconhecimento (005/RF-1). */
    @Query("select distinct m.edicao.id from MagistradoReconhecido m join m.reconhecimentos r"
            + " where m.email = :email"
            + " and m.edicao.status = br.jus.tjgo.goianao.edicao.StatusEdicao.PUBLICADA")
    List<Long> edicoesPublicadasComReconhecimento(@Param("email") String email);
}
