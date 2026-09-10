package br.jus.tjgo.goianao.integracao.egesp;

import br.jus.tjgo.goianao.auth.MockIdentityProvider;
import br.jus.tjgo.goianao.comum.Texto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Implementacao mockada do EGESP (constituicao: integracoes mockadas nesta
 * fase). Devolve um conjunto plausivel de unidades do TJGO e, para cada uma,
 * uma lotacao deterministica — semear a mesma unidade duas vezes produz sempre
 * o mesmo resultado.
 *
 * <p>Os e-mails usam o dominio reservado {@code .example} e os CPFs sao
 * ficticios, validos apenas quanto aos digitos verificadores. Alguns servidores
 * do login mock aparecem em unidades especificas para que os cenarios das
 * features 006 e 008 possam ser exercitados ponta a ponta.
 */
@Component
public class MockEgespClient implements EgespClient {

    private static final List<UnidadeEgesp> UNIDADES = List.of(
            new UnidadeEgesp("1ª Vara Cível da Comarca de Goiânia", "Goiânia"),
            new UnidadeEgesp("2ª Vara Cível da Comarca de Goiânia", "Goiânia"),
            new UnidadeEgesp("3ª Vara Criminal da Comarca de Goiânia", "Goiânia"),
            new UnidadeEgesp("Vara da Fazenda Pública Estadual da Comarca de Goiânia",
                    "Goiânia"),
            new UnidadeEgesp("Vara de Execuções Penais da Comarca de Goiânia",
                    "Goiânia"),
            new UnidadeEgesp("Juizado da Infância e da Juventude da Comarca de Goiânia",
                    "Goiânia"),
            new UnidadeEgesp("2ª Vara de Família e Sucessões da Comarca de Goiânia",
                    "Goiânia"),
            new UnidadeEgesp(
                    "1ª Vara de Família e Sucessões da Comarca de Aparecida de Goiânia",
                    "Aparecida de Goiânia"),
            new UnidadeEgesp("2ª Vara Cível da Comarca de Aparecida de Goiânia",
                    "Aparecida de Goiânia"),
            new UnidadeEgesp("Juizado Especial Cível da Comarca de Anápolis", "Anápolis"),
            new UnidadeEgesp("1ª Vara Criminal da Comarca de Anápolis", "Anápolis"),
            new UnidadeEgesp("2ª Vara Cível da Comarca de Rio Verde", "Rio Verde"),
            new UnidadeEgesp("Vara Única da Comarca de Pirenopolis", "Pirenopolis"),
            new UnidadeEgesp("1ª Vara Criminal da Comarca de Luziânia", "Luziânia"),
            new UnidadeEgesp("Vara Única da Comarca de Cristalina", "Cristalina"),
            new UnidadeEgesp("1ª Vara Cível da Comarca de Catalão", "Catalão"),
            new UnidadeEgesp("Juizado Especial Criminal da Comarca de Itumbiara", "Itumbiara"),
            new UnidadeEgesp("Vara Única da Comarca de Porangatu", "Porangatu"));

    private static final String[] PRENOMES = {
        "Adriana", "Bruno", "Camila", "Daniel", "Elaine", "Fábio", "Gabriela", "Henrique",
        "Isadora", "João Pedro", "Karina", "Leonardo", "Mariana", "Nelson", "Olívia",
        "Patrícia", "Rodrigo", "Simone", "Thiago", "Vanessa"
    };

    private static final String[] SOBRENOMES = {
        "Almeida", "Barbosa", "Carvalho", "Duarte", "Esteves", "Fonseca", "Guimarães",
        "Henriques", "Ipiranga", "Jardim", "Lacerda", "Moraes", "Nogueira", "Oliveira",
        "Peixoto", "Queiroz", "Ribeiro", "Salgado", "Tavares", "Vilela"
    };

    /** Lotacoes fixas, para exercitar os cenarios das features 006 e 008. */
    private static final Map<String, List<ServidorEgesp>> LOTACOES_FIXAS = new LinkedHashMap<>();

    static {
        LOTACOES_FIXAS.put(
                Texto.canonicalizar("1ª Vara Cível da Comarca de Goiânia"),
                List.of(
                        new ServidorEgesp(MockIdentityProvider.EMAIL_SERVIDOR_1,
                                "Marcos Vinícius de Paula", MockIdentityProvider.CPF_SERVIDOR_1),
                        new ServidorEgesp(MockIdentityProvider.EMAIL_SERVIDOR_MULTI,
                                "Carla Menezes do Amaral",
                                MockIdentityProvider.CPF_SERVIDOR_MULTI)));
        LOTACOES_FIXAS.put(
                Texto.canonicalizar("3ª Vara Criminal da Comarca de Goiânia"),
                List.of(new ServidorEgesp(MockIdentityProvider.EMAIL_SERVIDOR_2,
                        "Juliana Prado Ferreira", MockIdentityProvider.CPF_SERVIDOR_2)));
        LOTACOES_FIXAS.put(
                Texto.canonicalizar("Juizado Especial Cível da Comarca de Anápolis"),
                List.of(
                        new ServidorEgesp(MockIdentityProvider.EMAIL_SERVIDOR_3,
                                "Tiago Nunes Barbosa", MockIdentityProvider.CPF_SERVIDOR_3),
                        new ServidorEgesp(MockIdentityProvider.EMAIL_SERVIDOR_MULTI,
                                "Carla Menezes do Amaral",
                                MockIdentityProvider.CPF_SERVIDOR_MULTI)));
    }

    @Override
    public List<UnidadeEgesp> listarUnidades(String filtro) {
        String alvo = Texto.canonicalizar(filtro);
        if (alvo == null || alvo.isBlank()) {
            return UNIDADES;
        }
        return UNIDADES.stream()
                .filter(u -> Texto.canonicalizar(u.nome()).contains(alvo)
                        || Texto.canonicalizar(u.comarca()).contains(alvo))
                .toList();
    }

    @Override
    public List<ServidorEgesp> listarServidoresPorUnidade(String nomeUnidade) {
        String canonico = Texto.canonicalizar(nomeUnidade);
        if (canonico == null || canonico.isBlank()) {
            return List.of();
        }

        List<ServidorEgesp> servidores =
                new ArrayList<>(LOTACOES_FIXAS.getOrDefault(canonico, List.of()));

        // Complementa com uma lotacao deterministica derivada do nome da unidade,
        // para que unidades sem lista fixa tambem tenham servidores plausiveis.
        int semente = Math.abs(canonico.hashCode());
        int quantidade = 4 + (semente % 5);
        for (int i = 0; i < quantidade; i++) {
            int passo = Math.abs(semente + i * 7919);
            String nome = PRENOMES[passo % PRENOMES.length] + " "
                    + SOBRENOMES[(passo / 13) % SOBRENOMES.length];
            String email = emailDeterministico(nome, passo);
            if (servidores.stream().noneMatch(s -> s.email().equals(email))) {
                servidores.add(new ServidorEgesp(email, nome, cpfDeterministico(passo)));
            }
        }
        return List.copyOf(servidores);
    }

    /**
     * {@code nome.sobrenome.NN@tjgo.example}. O sufixo evita que homonimos de
     * unidades diferentes virem, por acaso, a mesma pessoa.
     */
    private static String emailDeterministico(String nome, int semente) {
        String local = Texto.canonicalizar(nome).replaceAll("[^a-z0-9]+", ".");
        return local + "." + (semente % 100) + "@tjgo.example";
    }

    /**
     * Gera um CPF ficticio, porem valido quanto aos digitos verificadores, a
     * partir de uma semente — assim a semeadura de uma mesma unidade e sempre
     * identica (008/CA-1).
     */
    private static String cpfDeterministico(int semente) {
        long base = Math.abs((long) semente * 2654435761L) % 900_000_000L + 100_000_000L;
        String noveDigitos = Long.toString(base);
        int d1 = digitoVerificador(noveDigitos);
        int d2 = digitoVerificador(noveDigitos + d1);
        return noveDigitos + d1 + d2;
    }

    private static int digitoVerificador(String base) {
        int soma = 0;
        int peso = base.length() + 1;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0') * peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
