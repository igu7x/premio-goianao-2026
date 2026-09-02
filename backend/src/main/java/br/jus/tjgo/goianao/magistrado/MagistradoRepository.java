package br.jus.tjgo.goianao.magistrado;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MagistradoRepository extends JpaRepository<MagistradoReconhecido, Long> {

    List<MagistradoReconhecido> findByEdicaoIdOrderByNomeAsc(Long edicaoId);

    Optional<MagistradoReconhecido> findByEdicaoIdAndCpf(Long edicaoId, String cpf);

    boolean existsByEdicaoIdAndCpf(Long edicaoId, String cpf);

    boolean existsByCpf(String cpf);

    /** Ids das edicoes publicadas em que o CPF tem reconhecimento (005/RF-1). */
    @Query("select distinct m.edicao.id from MagistradoReconhecido m join m.reconhecimentos r"
            + " where m.cpf = :cpf"
            + " and m.edicao.status = br.jus.tjgo.goianao.edicao.StatusEdicao.PUBLICADA")
    List<Long> edicoesPublicadasComReconhecimento(@Param("cpf") String cpf);
}
