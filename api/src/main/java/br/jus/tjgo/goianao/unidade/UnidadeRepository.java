package br.jus.tjgo.goianao.unidade;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadeRepository extends JpaRepository<UnidadeJudiciaria, Long> {

    Optional<UnidadeJudiciaria> findByNomeCanonico(String nomeCanonico);

    /**
     * Traz o responsavel junto.
     *
     * <p>Sem isto a listagem quebrava com 500 assim que <b>alguma</b> unidade
     * tinha responsavel designado: o campo e LAZY, {@code open-in-view} esta
     * desligado (e deve continuar) e a resposta e montada fora da transacao.
     * Enquanto o campo era nulo em todas, nada estourava — o defeito ficou
     * escondido ate a primeira designacao.
     */
    @EntityGraph(attributePaths = "responsavel")
    List<UnidadeJudiciaria> findAllByOrderByNomeAsc();

    /**
     * Traz o responsavel junto, pelo mesmo motivo da listagem: o campo e LAZY,
     * {@code open-in-view} esta desligado e a resposta e montada fora da
     * transacao. Sem isto, a pagina de uma unidade com responsavel designado
     * responderia 500 — e so depois da primeira designacao.
     */
    @EntityGraph(attributePaths = "responsavel")
    Optional<UnidadeJudiciaria> findWithResponsavelById(Long id);

    /** Casamento com a API corporativa (010), depois que o codigo foi gravado. */
    Optional<UnidadeJudiciaria> findByCodigoSiedos(Long codigoSiedos);

    /** Escopo do superior responsavel: e por aqui que ele ganha a unidade. */
    boolean existsByIdAndResponsavelEmail(Long id, String email);

    List<UnidadeJudiciaria> findByResponsavelEmailOrderByNomeAsc(String email);

    boolean existsByResponsavelEmail(String email);
}
