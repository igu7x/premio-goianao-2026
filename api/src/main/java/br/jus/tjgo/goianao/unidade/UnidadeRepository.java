package br.jus.tjgo.goianao.unidade;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /** Casamento com a API corporativa (010), depois que o codigo foi gravado. */
    Optional<UnidadeJudiciaria> findByCodigoSiedos(Long codigoSiedos);

    /** Escopo do superior responsavel: e por aqui que ele ganha a unidade. */
    boolean existsByIdAndResponsavelEmail(Long id, String email);

    List<UnidadeJudiciaria> findByResponsavelEmailOrderByNomeAsc(String email);

    /**
     * Unidades ainda sem responsavel, para a designacao em lote a partir do RH.
     *
     * <p>Ordenadas por id e a partir de um cursor porque a rodada e limitada: o
     * RH cobra uma chamada por unidade, e com o tribunal inteiro cadastrado uma
     * varredura unica estouraria o tempo da rota. Sem o cursor, a rodada
     * seguinte tentaria de novo exatamente as mesmas unidades — as que o RH nao
     * tem responsavel continuam sem responsavel, e o lote nunca terminaria.
     *
     * <p>So entram as que tem codigo: sem ele nao ha o que perguntar ao RH.
     */
    List<UnidadeJudiciaria> findByResponsavelIsNullAndCodigoSiedosIsNotNullAndIdGreaterThanOrderByIdAsc(
            Long desdeId, Pageable pagina);

    long countByResponsavelIsNullAndCodigoSiedosIsNotNull();
}
