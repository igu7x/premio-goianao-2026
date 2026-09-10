package br.jus.tjgo.goianao.auth;

import br.jus.tjgo.goianao.comum.Email;
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
 * <p>Os e-mails usam o dominio {@code .example}, reservado para exemplos (RFC
 * 2606): nao pertencem a ninguem. Os CPFs sao validos quanto aos digitos
 * verificadores, mas ficticios; nao identificam ninguem (DI-24) e so entram
 * como dado informativo na carga de demonstracao.
 */
@Component
public class MockIdentityProvider implements IdentityProvider {

    /** E-mails de teste referenciados tambem pela carga de dados de demonstracao. */
    public static final String EMAIL_ADMIN = "ana.rebelo@tjgo.example";
    public static final String EMAIL_MAGISTRADO_1 = "rafael.bittencourt@tjgo.example";
    public static final String EMAIL_MAGISTRADO_2 = "helena.aires@tjgo.example";
    public static final String EMAIL_ADMIN_MAGISTRADO = "otavio.peixoto@tjgo.example";
    public static final String EMAIL_SERVIDOR_1 = "marcos.paula@tjgo.example";
    public static final String EMAIL_SERVIDOR_2 = "juliana.ferreira@tjgo.example";
    public static final String EMAIL_SERVIDOR_3 = "tiago.barbosa@tjgo.example";
    public static final String EMAIL_SERVIDOR_MULTI = "carla.amaral@tjgo.example";
    public static final String EMAIL_SEM_VINCULO = "eduardo.teixeira@tjgo.example";

    /** CPFs ficticios das mesmas pessoas, so para preencher o campo informativo. */
    public static final String CPF_MAGISTRADO_1 = "20450670252";
    public static final String CPF_MAGISTRADO_2 = "30980140323";
    public static final String CPF_ADMIN_MAGISTRADO = "40310520495";
    public static final String CPF_SERVIDOR_1 = "50760980578";
    public static final String CPF_SERVIDOR_2 = "60840310641";
    public static final String CPF_SERVIDOR_3 = "70290450764";
    public static final String CPF_SERVIDOR_MULTI = "80530720892";

    private final Map<String, IdentidadeAutenticada> usuarios = new LinkedHashMap<>();

    public MockIdentityProvider() {
        registrar(EMAIL_ADMIN, "Ana Cristina Marques Rebelo");
        registrar(EMAIL_ADMIN_MAGISTRADO, "Otávio Lemos Peixoto");
        registrar(EMAIL_MAGISTRADO_1, "Rafael Siqueira Bittencourt");
        registrar(EMAIL_MAGISTRADO_2, "Helena Vasconcelos Aires");
        registrar(EMAIL_SERVIDOR_1, "Marcos Vinícius de Paula");
        registrar(EMAIL_SERVIDOR_2, "Juliana Prado Ferreira");
        registrar(EMAIL_SERVIDOR_3, "Tiago Nunes Barbosa");
        registrar(EMAIL_SERVIDOR_MULTI, "Carla Menezes do Amaral");
        registrar(EMAIL_SEM_VINCULO, "Eduardo Rocha Teixeira");
    }

    private void registrar(String email, String nome) {
        usuarios.put(email, new IdentidadeAutenticada(email, nome));
    }

    @Override
    public IdentidadeAutenticada autenticar(String credencial) {
        IdentidadeAutenticada identidade = usuarios.get(Email.normalizar(credencial));
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
