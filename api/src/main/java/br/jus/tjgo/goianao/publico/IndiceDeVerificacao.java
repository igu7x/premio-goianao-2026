package br.jus.tjgo.goianao.publico;

import br.jus.tjgo.goianao.edicao.base.CatalogoDeEdicoes;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * De um codigo de certificado para a edicao onde ele esta (011/RF-10).
 *
 * <p>A pagina de verificacao e anonima: quem digita um codigo nao tem sessao e,
 * portanto, nao tem edicao. Sem este indice, conferir um codigo exigiria varrer
 * a base de todas as edicoes — custo que cresce um schema por ano, numa rota
 * aberta a internet.
 *
 * <p>Ele vive no esquema compartilhado e guarda o minimo: o codigo e onde
 * procurar. Os dados do certificado continuam existindo apenas na base da
 * edicao, e e la que a verificacao vai busca-los.
 *
 * <p>O acesso e por JDBC, e nao por JPA, justamente porque esta tabela e a
 * excecao: ela precisa ser lida <b>fora</b> de qualquer edicao, enquanto toda
 * entidade mapeada e lida dentro de uma.
 */
@Component
public class IndiceDeVerificacao {

    private final JdbcTemplate jdbc;
    private final String compartilhado;

    public IndiceDeVerificacao(DataSource dataSource, CatalogoDeEdicoes catalogo) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.compartilhado = catalogo.compartilhado();
    }

    /**
     * Registra o codigo recem-emitido.
     *
     * <p>Idempotente: reemitir devolve o mesmo codigo do registro original
     * (005/RF-6), e tentar indexa-lo de novo nao pode falhar a emissao.
     */
    public void registrar(String codigo, Long edicaoId) {
        int atualizadas = jdbc.update(
                "UPDATE " + compartilhado + ".certificado_indice SET edicao_id = ? "
                        + "WHERE codigo_validacao = ?",
                new Object[] {edicaoId, codigo}, new int[] {Types.BIGINT, Types.VARCHAR});
        if (atualizadas == 0) {
            jdbc.update("INSERT INTO " + compartilhado + ".certificado_indice "
                            + "(codigo_validacao, edicao_id, criado_em) VALUES (?, ?, ?)",
                    codigo, edicaoId, LocalDateTime.now());
        }
    }

    /** Em qual edicao procurar este codigo. */
    public Optional<Long> edicaoDe(String codigo) {
        return jdbc.query("SELECT edicao_id FROM " + compartilhado
                        + ".certificado_indice WHERE codigo_validacao = ?",
                (rs, linha) -> rs.getLong(1), codigo).stream().findFirst();
    }
}
