package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Provedor de identidade mockado (001/RF-2). Substitui o SSO corporativo
 * enquanto os parametros de OAuth do TJGO nao estao disponiveis: o restante do
 * sistema enxerga exatamente o mesmo contrato de um login real.
 *
 * <p>Os CPFs abaixo sao numeros validos quanto aos digitos verificadores, mas
 * ficticios — nao pertencem a pessoas reais.
 */
@Component
public class MockIdentityProvider implements IdentityProvider {

    /** CPFs de teste referenciados tambem pela carga de dados de demonstracao. */
    public static final String CPF_ADMIN = "10120230100";
    public static final String CPF_MAGISTRADO_1 = "20450670252";
    public static final String CPF_MAGISTRADO_2 = "30980140323";
    public static final String CPF_ADMIN_MAGISTRADO = "40310520495";
    public static final String CPF_SERVIDOR_1 = "50760980578";
    public static final String CPF_SERVIDOR_2 = "60840310641";
    public static final String CPF_SERVIDOR_3 = "70290450764";
    public static final String CPF_SERVIDOR_MULTI = "80530720892";
    public static final String CPF_SEM_VINCULO = "90170360954";

    private final Map<String, IdentidadeAutenticada> usuarios = new LinkedHashMap<>();

    public MockIdentityProvider() {
        registrar(CPF_ADMIN, "Ana Cristina Marques Rebelo");
        registrar(CPF_ADMIN_MAGISTRADO, "Otávio Lemos Peixoto");
        registrar(CPF_MAGISTRADO_1, "Rafael Siqueira Bittencourt");
        registrar(CPF_MAGISTRADO_2, "Helena Vasconcelos Aires");
        registrar(CPF_SERVIDOR_1, "Marcos Vinícius de Paula");
        registrar(CPF_SERVIDOR_2, "Juliana Prado Ferreira");
        registrar(CPF_SERVIDOR_3, "Tiago Nunes Barbosa");
        registrar(CPF_SERVIDOR_MULTI, "Carla Menezes do Amaral");
        registrar(CPF_SEM_VINCULO, "Eduardo Rocha Teixeira");
    }

    private void registrar(String cpf, String nome) {
        usuarios.put(cpf, new IdentidadeAutenticada(cpf, nome));
    }

    @Override
    public IdentidadeAutenticada autenticar(String credencial) {
        String cpf = Cpf.normalizar(credencial);
        IdentidadeAutenticada identidade = usuarios.get(cpf);
        if (identidade == null) {
            throw new NaoEncontradoException(
                    "Nenhum usuário de teste corresponde à credencial informada.");
        }
        return identidade;
    }

    @Override
    public List<IdentidadeAutenticada> identidadesDisponiveis() {
        return List.copyOf(usuarios.values());
    }
}
