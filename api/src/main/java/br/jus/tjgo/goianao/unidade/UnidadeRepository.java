package br.jus.tjgo.goianao.unidade;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadeRepository extends JpaRepository<UnidadeJudiciaria, Long> {

    Optional<UnidadeJudiciaria> findByNomeCanonico(String nomeCanonico);

    List<UnidadeJudiciaria> findAllByOrderByNomeAsc();

    /** Escopo do superior responsavel: e por aqui que ele ganha a unidade. */
    boolean existsByIdAndResponsavelCpf(Long id, String cpf);

    List<UnidadeJudiciaria> findByResponsavelCpfOrderByNomeAsc(String cpf);
}
