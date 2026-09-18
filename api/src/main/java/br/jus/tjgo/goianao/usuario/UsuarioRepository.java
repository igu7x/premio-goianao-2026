package br.jus.tjgo.goianao.usuario;

import br.jus.tjgo.goianao.seguranca.Papel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Usuario> findAllByOrderByNomeAsc();

    /**
     * Quem administra esta base, ja como projecao (011/RF-3).
     *
     * <p>A projecao nao e economia: quem chama vai usar o resultado <b>depois</b>
     * de trocar para a base da outra edicao, e uma entidade levada para fora da
     * sessao em que nasceu nao consegue nem carregar os proprios papeis.
     */
    @Query("""
            SELECT new br.jus.tjgo.goianao.usuario.SementeDeSuperadmin(
                       u.email, u.nome, u.cpf, u.senhaHash)
              FROM Usuario u JOIN u.papeis p
             WHERE p = :papel AND u.ativo = TRUE
             ORDER BY u.nome
            """)
    List<SementeDeSuperadmin> superadminsAtivos(@Param("papel") Papel papel);
}
