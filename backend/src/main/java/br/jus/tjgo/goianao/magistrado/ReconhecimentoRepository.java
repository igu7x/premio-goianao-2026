package br.jus.tjgo.goianao.magistrado;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconhecimentoRepository extends JpaRepository<Reconhecimento, Long> {

    /** Reconhecimentos de uma edicao, com a unidade ja carregada. */
    @Query("select r from Reconhecimento r join fetch r.unidade u"
            + " where r.magistrado.edicao.id = :edicaoId order by u.nome asc")
    List<Reconhecimento> daEdicao(@Param("edicaoId") Long edicaoId);

    /**
     * Uma unidade e considerada reconhecida na edicao quando ha ao menos um
     * reconhecimento apontando para ela — e o que habilita a lista de servidores
     * (008) e a emissao do servidor (006).
     */
    @Query("select count(r) > 0 from Reconhecimento r"
            + " where r.magistrado.edicao.id = :edicaoId and r.unidade.id = :unidadeId")
    boolean existeNaEdicao(@Param("edicaoId") Long edicaoId, @Param("unidadeId") Long unidadeId);

    @Query("select r from Reconhecimento r join fetch r.unidade"
            + " where r.magistrado.edicao.id = :edicaoId and r.magistrado.cpf = :cpf")
    List<Reconhecimento> doMagistradoNaEdicao(@Param("edicaoId") Long edicaoId,
                                              @Param("cpf") String cpf);

    /**
     * Selos que uma unidade recebeu na edicao. A emissao do servidor calcula o
     * maior deles no momento da emissao (006/RF-4), e nao guarda o resultado:
     * assim uma inclusao na edicao vigente (009) passa a valer de imediato.
     */
    @Query("select r.selo from Reconhecimento r"
            + " where r.magistrado.edicao.id = :edicaoId and r.unidade.id = :unidadeId")
    List<br.jus.tjgo.goianao.comum.Selo> selosDaUnidade(@Param("edicaoId") Long edicaoId,
                                                        @Param("unidadeId") Long unidadeId);
}
