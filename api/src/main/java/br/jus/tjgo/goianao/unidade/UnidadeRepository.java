package br.jus.tjgo.goianao.unidade;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadeRepository extends JpaRepository<UnidadeJudiciaria, Long> {

    Optional<UnidadeJudiciaria> findByNomeCanonico(String nomeCanonico);

    List<UnidadeJudiciaria> findAllByOrderByNomeAsc();

    /** Casamento com a API corporativa (010), depois que o codigo foi gravado. */
    Optional<UnidadeJudiciaria> findByCodigoSiedos(Long codigoSiedos);

    /** Escopo do superior responsavel: e por aqui que ele ganha a unidade. */
    boolean existsByIdAndResponsavelEmail(Long id, String email);

    List<UnidadeJudiciaria> findByResponsavelEmailOrderByNomeAsc(String email);
}
