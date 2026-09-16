package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.comum.LeitorCsv;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Leitor da planilha de reconhecidos (004/RF-11), com colunas
 * <b>email, nome, unidade, selo</b> e, opcionalmente, <b>cpf</b> por ultimo.
 *
 * <p>O e-mail vem primeiro porque e a chave da pessoa (DI-24); o CPF foi para o
 * fim porque e opcional, e uma coluna opcional no meio obrigaria toda planilha
 * a ter o separador vazio.
 *
 * <p>O trabalho bruto — separador, BOM, aspas — e do {@link LeitorCsv}. Aqui
 * fica so o que significa cada coluna nesta planilha.
 */
@Component
public class ImportadorCsv {

    /** Uma linha ja separada em colunas, preservando o texto original para o relatorio. */
    public record LinhaCsv(int numero, String bruto, String email, String nome,
                           String unidade, String selo, String cpf) {}

    public static final String FORMATO = "email;nome;unidade;selo;cpf (o CPF é opcional)";

    private static final List<String> CABECALHOS = List.of("email", "e-mail");

    private final LeitorCsv leitor;

    public ImportadorCsv(LeitorCsv leitor) {
        this.leitor = leitor;
    }

    public List<LinhaCsv> ler(byte[] conteudo) {
        exigirFormatoAtual(conteudo);

        List<LinhaCsv> resultado = leitor
                .ler(conteudo, CABECALHOS, "Envie o arquivo CSV com os reconhecidos.")
                .stream()
                .map(linha -> new LinhaCsv(
                        linha.numero(),
                        linha.bruto(),
                        linha.coluna(0),
                        linha.coluna(1),
                        linha.coluna(2),
                        linha.coluna(3),
                        linha.coluna(4)))
                .toList();

        if (resultado.isEmpty()) {
            throw new RegraDeNegocioException(
                    "O arquivo não tem linhas de dados. Formato esperado: " + FORMATO);
        }
        return resultado;
    }

    /**
     * Planilha no formato antigo (CPF na primeira coluna) e recusada inteira,
     * com a explicacao. Sem isso, cada linha voltaria no relatorio como
     * "e-mail invalido", e a causa verdadeira ficaria escondida atras de
     * dezenas de erros iguais.
     */
    private void exigirFormatoAtual(byte[] conteudo) {
        List<String> colunas = leitor.primeiraLinha(conteudo);
        if (!colunas.isEmpty() && "cpf".equals(Texto.canonicalizar(colunas.get(0)))) {
            throw new RegraDeNegocioException("A planilha está no formato antigo, com o CPF na "
                    + "primeira coluna. Agora quem identifica o magistrado é o e-mail "
                    + "corporativo. Formato esperado: " + FORMATO);
        }
    }
}
