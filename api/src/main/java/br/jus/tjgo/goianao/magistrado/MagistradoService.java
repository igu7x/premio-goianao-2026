package br.jus.tjgo.goianao.magistrado;

import br.jus.tjgo.goianao.comum.Cpf;
import br.jus.tjgo.goianao.comum.Email;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.magistrado.dto.MagistradoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.ReconhecimentoRequisicao;
import br.jus.tjgo.goianao.magistrado.dto.UnidadeReconhecidaResposta;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MagistradoService {

    private final MagistradoRepository magistrados;
    private final ReconhecimentoRepository reconhecimentos;
    private final EdicaoService edicoes;
    private final UnidadeService unidades;

    public MagistradoService(MagistradoRepository magistrados,
                             ReconhecimentoRepository reconhecimentos,
                             EdicaoService edicoes,
                             UnidadeService unidades) {
        this.magistrados = magistrados;
        this.reconhecimentos = reconhecimentos;
        this.edicoes = edicoes;
        this.unidades = unidades;
    }

    // ------------------------------------------------------------------
    // Elegibilidade da edicao
    // ------------------------------------------------------------------

    /**
     * Regra de <b>inclusao</b> (009): vale em rascunho e tambem na edicao
     * vigente. Adicionar nao altera nenhum certificado ja emitido — apenas
     * habilita novos. Editar e remover continuam restritos ao rascunho.
     */
    public void exigirElegivelParaInclusao(Edicao edicao) {
        if (!edicao.aceitaInclusoes()) {
            throw new ConflitoException("A edição " + edicao.getAno()
                    + " está publicada e não é a vigente: seu cadastro está congelado."
                    + " Inclusões só são possíveis em rascunho ou na edição vigente.");
        }
    }

    /**
     * A importacao em lote fica restrita ao rascunho (004/RF-11).
     *
     * <p>009/T-003 menciona {@code importar} junto de {@code criar}, mas o texto
     * que manda e o requisito numerado da 004 — "Disponivel so em Rascunho" — e o
     * §3 do plano da 009, que troca a regra apenas em {@code criar}. A inclusao na
     * vigente foi desenhada para o caso pontual (um reconhecido que faltou),
     * enquanto subir uma planilha inteira numa edicao ja em uso e uma operacao de
     * outra escala. Se o TJGO quiser liberar, e trocar esta chamada por
     * {@link #exigirElegivelParaInclusao} e emendar a RF-11.
     */
    public void exigirRascunhoParaImportacao(Edicao edicao) {
        exigirRascunho(edicao, "importar");
    }

    /** Alteracoes destrutivas so em rascunho (004/RF-7, 009/RF-3). */
    private void exigirRascunho(Edicao edicao, String operacao) {
        if (!edicao.estaEmRascunho()) {
            throw new ConflitoException("A edição " + edicao.getAno()
                    + " já foi publicada: não é possível " + operacao
                    + " reconhecidos. Na edição vigente é possível incluir novos, um a um.");
        }
    }

    // ------------------------------------------------------------------
    // Escrita
    // ------------------------------------------------------------------

    @Transactional
    public MagistradoReconhecido criar(Long edicaoId, MagistradoRequisicao requisicao) {
        Edicao edicao = edicoes.buscar(edicaoId);
        exigirElegivelParaInclusao(edicao);

        String email = Email.exigir(requisicao.email());
        String nome = exigirNome(requisicao.nome());
        String cpf = Cpf.opcional(requisicao.cpf());

        if (magistrados.existsByEdicaoIdAndEmail(edicaoId, email)) {
            throw new ConflitoException("Já existe um magistrado com este e-mail na edição "
                    + edicao.getAno() + ". Use a inclusão de reconhecimento para adicionar"
                    + " outra unidade a ele.");
        }

        MagistradoReconhecido magistrado = new MagistradoReconhecido(edicao, email, nome, cpf);
        aplicarReconhecimentos(magistrado, requisicao.reconhecimentos());
        return magistrados.save(magistrado);
    }

    /**
     * Inclusao aditiva de uma unidade a um magistrado ja cadastrado (009/RF-2).
     * Endpoint proprio, e nao um PUT: substituir a lista inteira seria destrutivo.
     */
    @Transactional
    public MagistradoReconhecido adicionarReconhecimento(Long edicaoId, Long magistradoId,
                                                         ReconhecimentoRequisicao requisicao) {
        MagistradoReconhecido magistrado = buscar(edicaoId, magistradoId);
        exigirElegivelParaInclusao(magistrado.getEdicao());

        UnidadeJudiciaria unidade = resolverUnidade(requisicao);
        if (magistrado.reconheceUnidade(unidade.getId())) {
            throw new ConflitoException("O magistrado já possui reconhecimento para a unidade "
                    + unidade.getNome() + " nesta edição.");
        }
        if (requisicao.selo() == null) {
            throw new RegraDeNegocioException("Informe o selo do reconhecimento.");
        }

        magistrado.adicionar(unidade, requisicao.selo());
        return magistrado;
    }

    @Transactional
    public MagistradoReconhecido atualizar(Long edicaoId, Long magistradoId,
                                           MagistradoRequisicao requisicao) {
        MagistradoReconhecido magistrado = buscar(edicaoId, magistradoId);
        exigirRascunho(magistrado.getEdicao(), "editar");

        // O e-mail e a chave e nao muda; quem cadastrou o e-mail errado remove e
        // cadastra de novo — possivel, porque editar so existe em rascunho.
        String email = Email.exigir(requisicao.email());
        if (!email.equals(magistrado.getEmail())) {
            throw new RegraDeNegocioException("O e-mail do magistrado não pode ser alterado. "
                    + "Remova o cadastro e inclua de novo com o e-mail correto.");
        }

        magistrado.renomear(exigirNome(requisicao.nome()));
        magistrado.definirCpf(Cpf.opcional(requisicao.cpf()));
        magistrado.limparReconhecimentos();
        aplicarReconhecimentos(magistrado, requisicao.reconhecimentos());
        return magistrado;
    }

    @Transactional
    public void remover(Long edicaoId, Long magistradoId) {
        MagistradoReconhecido magistrado = buscar(edicaoId, magistradoId);
        exigirRascunho(magistrado.getEdicao(), "remover");
        magistrados.delete(magistrado);
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MagistradoReconhecido> listar(Long edicaoId) {
        edicoes.buscar(edicaoId);
        return magistrados.findByEdicaoIdOrderByNomeAsc(edicaoId);
    }

    @Transactional(readOnly = true)
    public MagistradoReconhecido buscar(Long edicaoId, Long magistradoId) {
        MagistradoReconhecido magistrado = magistrados.findById(magistradoId).orElseThrow(
                () -> new NaoEncontradoException("Magistrado " + magistradoId + " não encontrado."));
        if (!magistrado.getEdicao().getId().equals(edicaoId)) {
            throw new NaoEncontradoException(
                    "Magistrado " + magistradoId + " não pertence a esta edição.");
        }
        return magistrado;
    }

    /**
     * Unidades reconhecidas da edicao com seus selos e o maior deles (004/RF-9).
     * O calculo do maior selo e feito na consulta, e nao gravado, para que uma
     * inclusao na edicao vigente (009) passe a valer imediatamente.
     */
    @Transactional(readOnly = true)
    public List<UnidadeReconhecidaResposta> unidadesReconhecidas(
            Long edicaoId, Function<Long, Long> contadorDeServidores) {

        Map<Long, List<Reconhecimento>> porUnidade = new LinkedHashMap<>();
        for (Reconhecimento r : reconhecimentos.daEdicao(edicaoId)) {
            porUnidade.computeIfAbsent(r.getUnidade().getId(), k -> new ArrayList<>()).add(r);
        }

        List<UnidadeReconhecidaResposta> resposta = new ArrayList<>();
        porUnidade.forEach((unidadeId, itens) -> {
            List<Selo> selos = itens.stream()
                    .map(Reconhecimento::getSelo)
                    .distinct()
                    .sorted((a, b) -> Integer.compare(b.valor(), a.valor()))
                    .toList();
            Set<Long> magistradosDistintos = new HashSet<>();
            itens.forEach(r -> magistradosDistintos.add(r.getMagistrado().getId()));

            resposta.add(new UnidadeReconhecidaResposta(
                    unidadeId,
                    itens.get(0).getUnidade().getNome(),
                    selos,
                    Selo.maior(selos).orElse(null),
                    magistradosDistintos.size(),
                    contadorDeServidores == null ? 0L : contadorDeServidores.apply(unidadeId)));
        });
        return resposta;
    }

    /** Reconhecimentos de um e-mail em uma edicao — base das opcoes de emissao (005/RF-2). */
    @Transactional(readOnly = true)
    public List<Reconhecimento> reconhecimentosDe(Long edicaoId, String email) {
        return reconhecimentos.doMagistradoNaEdicao(edicaoId, email);
    }

    @Transactional(readOnly = true)
    public List<Long> edicoesPublicadasDe(String email) {
        return magistrados.edicoesPublicadasComReconhecimento(email);
    }

    @Transactional(readOnly = true)
    public boolean unidadeEhReconhecida(Long edicaoId, Long unidadeId) {
        return reconhecimentos.existeNaEdicao(edicaoId, unidadeId);
    }

    /**
     * Regra do maior selo (constituicao, principio 4): quando a unidade foi
     * reconhecida por mais de um magistrado, com selos diferentes, vale o maior.
     * Calculado na hora da consulta/emissao, nunca gravado.
     */
    @Transactional(readOnly = true)
    public Optional<Selo> maiorSeloDaUnidade(Long edicaoId, Long unidadeId) {
        return Selo.maior(reconhecimentos.selosDaUnidade(edicaoId, unidadeId));
    }

    /** Nome cadastrado do magistrado naquela edicao — o que vai impresso (005/RF-4). */
    @Transactional(readOnly = true)
    public Optional<MagistradoReconhecido> porEmailNaEdicao(Long edicaoId, String email) {
        return magistrados.findByEdicaoIdAndEmail(edicaoId, email);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void aplicarReconhecimentos(MagistradoReconhecido magistrado,
                                        List<ReconhecimentoRequisicao> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException(
                    "Informe ao menos uma unidade com selo para o magistrado.");
        }

        Set<Long> jaUsadas = new HashSet<>();
        for (ReconhecimentoRequisicao item : itens) {
            if (item.selo() == null) {
                throw new RegraDeNegocioException("Informe o selo de cada unidade.");
            }
            UnidadeJudiciaria unidade = resolverUnidade(item);
            if (!jaUsadas.add(unidade.getId())) {
                throw new ConflitoException("A unidade " + unidade.getNome()
                        + " aparece mais de uma vez para o mesmo magistrado.");
            }
            magistrado.adicionar(unidade, item.selo());
        }
    }

    /**
     * A unidade vem do EGESP: por id, quando ja espelhada localmente, ou pelo
     * nome, que e entao conferido contra o catalogo do EGESP (004/RF-1).
     */
    private UnidadeJudiciaria resolverUnidade(ReconhecimentoRequisicao item) {
        if (item.unidadeId() != null) {
            return unidades.buscar(item.unidadeId());
        }
        String nome = Texto.aparar(item.unidadeNome());
        if (nome == null) {
            throw new RegraDeNegocioException("Informe a unidade judiciária.");
        }
        return unidades.garantirDoEgesp(nome);
    }

    private String exigirNome(String bruto) {
        String nome = Texto.aparar(bruto);
        if (nome == null) {
            throw new RegraDeNegocioException("Informe o nome do magistrado.");
        }
        return nome;
    }
}
