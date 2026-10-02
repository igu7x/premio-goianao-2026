package br.jus.tjgo.goianao.comum.erro;

import static org.assertj.core.api.Assertions.assertThat;

import br.jus.tjgo.goianao.integracao.egesp.connecttj.ConnectTjException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Falha da API corporativa chega a tela com o motivo, e nao como "erro
 * inesperado": quem a ve e o superadministrador, que nao le o log do pod e
 * precisa repassar a mensagem a equipe da API (DI-32).
 */
@DisplayName("Falha da API corporativa na resposta")
class FalhaDoRhTest {

    @Test
    @DisplayName("vira 502 com a mensagem do ConnectTJ preservada")
    void quinhentosEDois() {
        ResponseEntity<ErroResposta> resposta = new TratadorDeErros().rhIndisponivel(
                new ConnectTjException("A API corporativa negou acesso a /api/v1/ad/usuarios "
                        + "(Acesso não autorizado ao recurso AD)."));

        assertThat(resposta.getStatusCode().value()).isEqualTo(502);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().erro()).isEqualTo("rh_indisponivel");
        assertThat(resposta.getBody().mensagem()).contains("recurso AD");
    }
}
