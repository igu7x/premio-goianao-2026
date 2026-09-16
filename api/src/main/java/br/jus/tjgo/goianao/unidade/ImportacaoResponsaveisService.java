package br.jus.tjgo.goianao.unidade;

import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.LeitorCsv;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.magistrado.MagistradoReconhecido;
import br.jus.tjgo.goianao.magistrado.MagistradoRepository;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.unidade.dto.ImportacaoResponsaveis;
import br.jus.tjgo.goianao.usuario.Usuario;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planilha que diz qual magistrado responde por qual unidade, e com que selo.
 *
 * <p>A fonte e a planilha, e nao o RH: quem responde pela unidade no organograma
 * corporativo nem sempre e quem responde por ela <b>no premio</b> — e o selo, o
 * RH nao tem de jeito nenhum. Quem premia e a comissao, e o sistema nunca
 * calcula vencedor (constituicao, principio 2).
 *
 * <p>Cada linha faz tres coisas de uma vez, porque as tres vem juntas no
 * arquivo: garante o usuario magistrado, designa quem responde pela unidade e
 * grava o reconhecimento com o selo. Uma linha ruim volta no relatorio e as
 * demais sao gravadas — planilha de tribunal chega com unidade extinta e linha
 * em branco no meio, e recusar tudo por causa de uma obrigaria a refazer o
 * arquivo para corrigir um nome.
 */
@Service
public class ImportacaoResponsaveisService {

    public static final String FORMATO = "nome;email;unidade;selo — a unidade pelo código do "
            + "SIEDOS (o nome exato também vale) e o selo entre bronze, prata, ouro e diamante";

    private static final List<String> CABECALHOS = List.of("nome", "magistrado");

    private final LeitorCsv leitor;
    private final UnidadeRepository unidades;
    private final UsuarioRepository usuarios;
    private final MagistradoService magistrados;
    private final MagistradoRepository reconhecidos;
    private final EdicaoService edicoes;

    public ImportacaoResponsaveisService(LeitorCsv leitor, UnidadeRepository unidades,
                                         UsuarioRepository usuarios, MagistradoService magistrados,
                                         MagistradoRepository reconhecidos, EdicaoService edicoes) {
        this.leitor = leitor;
        this.unidades = unidades;
        this.usuarios = usuarios;
        this.magistrados = magistrados;
        this.reconhecidos = reconhecidos;
        this.edicoes = edicoes;
    }

    /**
     * @param edicaoId edicao dos reconhecimentos; nulo usa a vigente. Sem
     *                 nenhuma das duas o selo nao tem onde ser gravado, e a
     *                 importacao para antes de mexer no cadastro — gravar so
     *                 metade do arquivo seria pior do que nao gravar nada
     */
    @Transactional
    public ImportacaoResponsaveis importar(byte[] csv, Long edicaoId) {
        Edicao edicao = edicaoId != null
                ? edicoes.buscar(edicaoId)
                : edicoes.vigente().orElseThrow(() -> new RegraDeNegocioException(
                        "Nenhuma edição vigente definida, e a planilha traz selos: eles são "
                        + "reconhecimentos de uma edição. Defina a edição vigente em Edições do "
                        + "prêmio, ou escolha a edição no envio."));

        List<LeitorCsv.LinhaBruta> linhas = leitor.ler(csv, CABECALHOS,
                "Envie o arquivo CSV com os magistrados responsáveis.");
        if (linhas.isEmpty()) {
            throw new RegraDeNegocioException(
                    "O arquivo não tem linhas de dados. Formato esperado: " + FORMATO);
        }

        List<ImportacaoResponsaveis.ErroDeLinha> erros = new ArrayList<>();
        int designados = 0;
        int criados = 0;
        int papelConcedido = 0;
        int substituidos = 0;
        int jaEram = 0;
        int reconhecimentos = 0;

        for (LeitorCsv.LinhaBruta linha : linhas) {
            String nome = linha.coluna(0);
            String email = linha.coluna(1);
            String alvoUnidade = linha.coluna(2);
            Selo selo = converterSelo(linha.coluna(3));

            if (nome == null || nome.isBlank()) {
                erros.add(erro(linha, "Informe o nome do magistrado na primeira coluna."));
                continue;
            }
            if (!Email.valido(email)) {
                erros.add(erro(linha, email == null || email.isBlank()
                        ? "Informe o e-mail corporativo do magistrado."
                        : "E-mail inválido: \"" + email + "\"."));
                continue;
            }
            Optional<UnidadeJudiciaria> encontrada = procurarUnidade(alvoUnidade);
            if (encontrada.isEmpty()) {
                erros.add(erro(linha, alvoUnidade == null || alvoUnidade.isBlank()
                        ? "Informe o código da unidade na terceira coluna."
                        : "Unidade \"" + alvoUnidade + "\" não está cadastrada. Cadastre-a em "
                          + "Sincronização de Unidades antes de designar quem responde por ela."));
                continue;
            }
            if (selo == null) {
                erros.add(erro(linha, "Selo inválido: \"" + Texto.aparar(linha.coluna(3))
                        + "\". Use bronze, prata, ouro ou diamante."));
                continue;
            }

            UnidadeJudiciaria unidade = encontrada.get();
            String normalizado = Email.normalizar(email);
            Usuario usuario = usuarios.findByEmailIgnoreCase(normalizado).orElse(null);

            if (usuario == null) {
                usuario = usuarios.save(
                        new Usuario(normalizado, nome, null, EnumSet.of(Papel.MAGISTRADO)));
                criados++;
            } else if (!usuario.isAtivo()) {
                // Reativar por causa de uma planilha seria devolver acesso que
                // alguem tirou de proposito.
                erros.add(erro(linha, "O usuário " + normalizado + " está desativado. Reative-o "
                        + "em Usuários do sistema antes de designá-lo."));
                continue;
            } else if (!usuario.getPapeis().contains(Papel.MAGISTRADO)) {
                // A designacao exige o papel: e a tela do magistrado que ela
                // destrava (008). Conceder aqui evita ter de passar por outra
                // tela no meio da importacao.
                usuario.concederPapel(Papel.MAGISTRADO);
                papelConcedido++;
            }

            try {
                if (reconhecer(edicao, normalizado, nome, unidade, selo)) {
                    reconhecimentos++;
                }
            } catch (RuntimeException e) {
                erros.add(erro(linha, e.getMessage() == null
                        ? "Falha ao gravar o reconhecimento." : e.getMessage()));
                continue;
            }

            Usuario anterior = unidade.getResponsavel();
            if (anterior != null && anterior.getId().equals(usuario.getId())) {
                jaEram++;
                continue;
            }
            if (anterior != null) {
                substituidos++;
            }
            unidade.designarResponsavel(usuario);
            designados++;
        }

        return new ImportacaoResponsaveis(linhas.size(), designados, criados, papelConcedido,
                substituidos, jaEram, reconhecimentos, edicao.getAno(), erros);
    }

    /**
     * Grava o reconhecimento pelos servicos de sempre, para nao escapar das
     * guardas de edicao publicada e de unidade duplicada (004, 009).
     *
     * @return falso quando o magistrado ja tinha aquele reconhecimento — repetir
     *         a planilha nao pode virar erro nem contagem inflada
     */
    private boolean reconhecer(Edicao edicao, String email, String nome,
                               UnidadeJudiciaria unidade, Selo selo) {
        Optional<MagistradoReconhecido> existente =
                reconhecidos.findByEdicaoIdAndEmail(edicao.getId(), email);

        if (existente.isEmpty()) {
            magistrados.criar(edicao.getId(), new MagistradoRequisicao(email, nome, null,
                    List.of(new ReconhecimentoRequisicao(unidade.getId(), null, selo))));
            return true;
        }
        if (existente.get().reconheceUnidade(unidade.getId())) {
            return false;
        }
        magistrados.adicionarReconhecimento(edicao.getId(), existente.get().getId(),
                new ReconhecimentoRequisicao(unidade.getId(), null, selo));
        return true;
    }

    /**
     * Pelo codigo do SIEDOS quando a coluna e um numero; senao pelo nome
     * canonico, que ignora acento, caixa e espaco repetido — a planilha vem
     * digitada por gente, e exigir o nome caractere a caractere transformaria a
     * importacao num jogo de adivinhacao.
     */
    private Optional<UnidadeJudiciaria> procurarUnidade(String alvo) {
        if (alvo == null || alvo.isBlank()) {
            return Optional.empty();
        }
        String limpo = alvo.trim();
        if (limpo.chars().allMatch(Character::isDigit)) {
            return unidades.findByCodigoSiedos(Long.parseLong(limpo));
        }
        return unidades.findByNomeCanonico(Texto.canonicalizar(limpo));
    }

    private Selo converterSelo(String bruto) {
        String valor = Texto.canonicalizar(bruto);
        if (valor == null || valor.isBlank()) {
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

    private ImportacaoResponsaveis.ErroDeLinha erro(LeitorCsv.LinhaBruta linha, String motivo) {
        return new ImportacaoResponsaveis.ErroDeLinha(linha.numero(), linha.bruto(), motivo);
    }
}
