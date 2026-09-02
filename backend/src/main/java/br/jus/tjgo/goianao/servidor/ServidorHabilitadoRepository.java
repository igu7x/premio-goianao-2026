package br.jus.tjgo.goianao.servidor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServidorHabilitadoRepository extends JpaRepository<ServidorHabilitado, Long> {

    List<ServidorHabilitado> findByEdicaoIdAndUnidadeIdOrderByNomeAsc(Long edicaoId, Long unidadeId);

    Optional<ServidorHabilitado> findByEdicaoIdAndUnidadeIdAndCpf(
            Long edicaoId, Long unidadeId, String cpf);

    boolean existsByEdicaoIdAndUnidadeIdAndCpfAndAtivoTrue(
            Long edicaoId, Long unidadeId, String cpf);

    long countByEdicaoIdAndUnidadeIdAndAtivoTrue(Long edicaoId, Long unidadeId);

    /** Ids das unidades em que o CPF esta habilitado na edicao (006/RF-2). */
    @Query("select s.unidade.id from ServidorHabilitado s"
            + " where s.edicao.id = :edicaoId and s.cpf = :cpf and s.ativo = true")
    List<Long> unidadesHabilitadas(@Param("edicaoId") Long edicaoId, @Param("cpf") String cpf);

    /** Edicoes publicadas em que o CPF consta em alguma lista ativa (006/RF-3). */
    @Query("select distinct s.edicao.id from ServidorHabilitado s"
            + " where s.cpf = :cpf and s.ativo = true"
            + " and s.edicao.status = br.jus.tjgo.goianao.edicao.StatusEdicao.PUBLICADA")
    List<Long> edicoesPublicadasHabilitadas(@Param("cpf") String cpf);

    /** Nome semeado do servidor, usado apenas como fallback na impressao (006/RF-1). */
    @Query("select s.nome from ServidorHabilitado s"
            + " where s.edicao.id = :edicaoId and s.unidade.id = :unidadeId and s.cpf = :cpf")
    Optional<String> nomeSalvo(@Param("edicaoId") Long edicaoId,
                               @Param("unidadeId") Long unidadeId,
                               @Param("cpf") String cpf);
}
