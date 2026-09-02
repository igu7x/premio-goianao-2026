package br.jus.tjgo.goianao.edicao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdicaoRepository extends JpaRepository<Edicao, Long> {

    boolean existsByAno(Integer ano);

    Optional<Edicao> findByAno(Integer ano);

    Optional<Edicao> findByVigenteTrue();

    List<Edicao> findAllByOrderByAnoDesc();

    List<Edicao> findByStatusOrderByAnoDesc(StatusEdicao status);
}
