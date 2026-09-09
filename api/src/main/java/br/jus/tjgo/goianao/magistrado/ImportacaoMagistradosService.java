package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.magistrado.dto.ImportacaoResposta;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Importacao em lote dos reconhecidos (004/RF-11).
 *
 * <p>Deliberadamente <b>sem</b> transacao propria: cada magistrado e persistido
 * pela transacao de {@link MagistradoService#criar}, o que produz a semantica
 * pedida — um CPF com qualquer linha invalida e rejeitado inteiro e vai para o
 * relatorio, e os demais entram normalmente (004/RNF-3).
 */
@Service
public class ImportacaoMagistradosService {

    private final ImportadorCsv leitor;
    private final MagistradoService magistrados;
    private final EdicaoService edicoes;

    public ImportacaoMagistradosService(ImportadorCsv leitor, MagistradoService magistrados,
                                        EdicaoService edicoes) {
        this.leitor = leitor;
        this.magistrados = magistrados;
        this.edicoes = edicoes;
    }

    public ImportacaoResposta importar(Long edicaoId, byte[] csv) {
        Edicao edicao = edicoes.buscar(edicaoId);
        magistrados.exigirRascunhoParaImportacao(edicao);

        List<ImportadorCsv.LinhaCsv> linhas = leitor.ler(csv);
        List<ImportacaoResposta.ErroDeLinha> erros = new ArrayList<>();

        // Agrupa por CPF preservando a ordem do arquivo: varias linhas do mesmo
        // magistrado viram um cadastro com varios reconhecimentos.
        Map<String, Grupo> grupos = new LinkedHashMap<>();

        for (ImportadorCsv.LinhaCsv linha : linhas) {
            String cpf = Cpf.normalizar(linha.cpf());
            if (cpf == null || !Cpf.valido(cpf)) {
                erros.add(erro(linha, "CPF inválido ou ausente."));
                continue;
            }
            String nome = Texto.aparar(linha.nome());
            if (nome == null) {
                erros.add(erro(linha, "Nome ausente."));
                continue;
            }
            String unidade = Texto.aparar(linha.unidade());
            if (unidade == null) {
                erros.add(erro(linha, "Unidade ausente."));
                continue;
            }
            Selo selo = converterSelo(linha.selo());
            if (selo == null) {
                erros.add(erro(linha, "Selo inválido. Use Bronze, Prata, Ouro ou Diamante."));
                continue;
            }

            Grupo grupo = grupos.computeIfAbsent(cpf, k -> new Grupo(nome));
            grupo.linhas.add(linha);
            grupo.reconhecimentos.add(new ReconhecimentoRequisicao(null, unidade, selo));
        }

        List<String> criados = new ArrayList<>();
        int magistradosCriados = 0;
        int reconhecimentosCriados = 0;

        for (Map.Entry<String, Grupo> entrada : grupos.entrySet()) {
            Grupo grupo = entrada.getValue();
            try {
                MagistradoReconhecido salvo = magistrados.criar(edicaoId,
                        new MagistradoRequisicao(entrada.getKey(), grupo.nome,
                                grupo.reconhecimentos));
                magistradosCriados++;
                reconhecimentosCriados += salvo.getReconhecimentos().size();
                criados.add(salvo.getNome() + " (" + Cpf.formatar(salvo.getCpf()) + ") - "
                        + salvo.getReconhecimentos().size() + " unidade(s)");
            } catch (RuntimeException e) {
                // Rejeita o magistrado inteiro, apontando todas as linhas dele.
                String motivo = e.getMessage() == null ? "Falha ao importar." : e.getMessage();
                grupo.linhas.forEach(linha -> erros.add(erro(linha, motivo)));
            }
        }

        return new ImportacaoResposta(linhas.size(), magistradosCriados, reconhecimentosCriados,
                criados, erros);
    }

    private ImportacaoResposta.ErroDeLinha erro(ImportadorCsv.LinhaCsv linha, String motivo) {
        return new ImportacaoResposta.ErroDeLinha(linha.numero(), linha.bruto(), motivo);
    }

    private Selo converterSelo(String bruto) {
        String valor = Texto.canonicalizar(bruto);
        if (valor == null) {
            return null;
        }
        for (Selo selo : Selo.values()) {
            if (selo.name().toLowerCase(Locale.ROOT).equals(valor)
                    || Texto.canonicalizar(selo.rotulo()).equals(valor)) {
                return selo;
            }
        }
        return null;
    }

    private static final class Grupo {
        private final String nome;
        private final List<ImportadorCsv.LinhaCsv> linhas = new ArrayList<>();
        private final List<ReconhecimentoRequisicao> reconhecimentos = new ArrayList<>();

        private Grupo(String nome) {
            this.nome = nome;
        }
    }
}
