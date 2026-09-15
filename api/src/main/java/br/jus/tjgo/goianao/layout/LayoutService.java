package br.jus.tjgo.goianao.layout;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.comum.erro.NaoEncontradoException;
import br.jus.tjgo.goianao.comum.erro.RegraDeNegocioException;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.layout.dto.AtualizarLayoutRequisicao;
import br.jus.tjgo.goianao.layout.dto.LayoutRequisicao;
import br.jus.tjgo.goianao.layout.dto.PreviewRequisicao;
import br.jus.tjgo.goianao.layout.render.CertificadoRenderer;
import br.jus.tjgo.goianao.layout.render.DimensoesArte;
import br.jus.tjgo.goianao.layout.render.FonteInstitucional;
import br.jus.tjgo.goianao.layout.render.ImageStorage;
import br.jus.tjgo.goianao.layout.render.ValidadorDeArte;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LayoutService {

    private static final String CODIGO_EXEMPLO = "GOI-EXEMPLO-0000";

    private final LayoutRepository repositorio;
    private final EdicaoService edicoes;
    private final ArtesPadrao artesPadrao;
    private final ImageStorage storage;
    private final ValidadorDeArte validador;
    private final CertificadoRenderer renderer;
    private final FonteInstitucional fonte;
    private final LayoutPreRequisitos preRequisitos;
    private final EmissoesDaEdicao emissoes;
    private final String baseVerificacao;

    public LayoutService(LayoutRepository repositorio,
                         EdicaoService edicoes,
                         ArtesPadrao artesPadrao,
                         ImageStorage storage,
                         ValidadorDeArte validador,
                         CertificadoRenderer renderer,
                         FonteInstitucional fonte,
                         LayoutPreRequisitos preRequisitos,
                         EmissoesDaEdicao emissoes,
                         br.jus.tjgo.goianao.config.GoianaoProperties props) {
        this.repositorio = repositorio;
        this.edicoes = edicoes;
        this.artesPadrao = artesPadrao;
        this.storage = storage;
        this.validador = validador;
        this.renderer = renderer;
        this.fonte = fonte;
        this.preRequisitos = preRequisitos;
        this.emissoes = emissoes;
        this.baseVerificacao = props.baseVerificacao();
    }

    @Transactional
    public LayoutCertificado criar(Long edicaoId, MultipartFile imagem, LayoutRequisicao dados) {
        Edicao edicao = edicoes.buscar(edicaoId);
        exigirEdicaoEditavel(edicao);

        Optional<LayoutCertificado> existente = repositorio
                .findByEdicaoIdAndSeloAndTipo(edicaoId, dados.selo(), dados.tipo());

        if (existente.isPresent() && !dados.substituir()) {
            throw new ConflitoException(
                    "Já existe layout para " + dados.selo().rotulo() + " / " + dados.tipo().rotulo()
                            + " nesta edição. Confirme a substituição para sobrescrever.");
        }

        byte[] conteudo = lerArquivo(imagem);
        DimensoesArte dimensoes = validador.validar(conteudo, imagem.getContentType());
        validarAreas(dados.areaNome(), dados.areaUnidade(), dados.areaCodigo(), dimensoes);

        String referencia = storage.salvar(conteudo, validador.extensaoDe(imagem.getContentType()));

        if (existente.isPresent()) {
            LayoutCertificado layout = existente.get();
            String anterior = layout.getImagemRef();
            layout.substituirArte(referencia, dimensoes.largura(), dimensoes.altura());
            layout.redefinirAreas(dados.areaNome(), dados.areaUnidade(), dados.areaCodigo());
            removerSilenciosamente(anterior);
            return layout;
        }

        return repositorio.save(new LayoutCertificado(
                edicao, dados.selo(), dados.tipo(), referencia,
                dimensoes.largura(), dimensoes.altura(),
                dados.areaNome(), dados.areaUnidade(), dados.areaCodigo()));
    }

    /**
     * Preenche com as artes padrao do premio as combinacoes que ainda faltam.
     *
     * <p>Existe porque uma edicao nova nasce com oito quadros vazios e nenhuma
     * delas pode ser publicada assim: sem isto, o administrador sobe oito
     * arquivos e posiciona oito vezes so para ter de onde partir. As pecas sao
     * as mesmas que a comunicacao do tribunal forneceu, e ja vem com as caixas
     * medidas sobre elas.
     *
     * <p><b>Nao substitui o que ja esta configurado.</b> Quem ja subiu a arte
     * definitiva de um selo fez uma escolha, e sobrescreve-la em silencio seria
     * trocar o desenho de um certificado sem que ninguem tenha pedido. Para
     * trocar uma arte existente o caminho continua sendo o editor.
     *
     * @return quantas foram criadas e quantas ja existiam
     */
    @Transactional
    public ArtesPadraoAplicadas aplicarArtesPadrao(Long edicaoId) {
        Edicao edicao = edicoes.buscar(edicaoId);
        exigirEdicaoEditavel(edicao);

        int criados = 0;
        int jaExistentes = 0;

        for (Selo selo : Selo.values()) {
            for (TipoCertificado tipo : TipoCertificado.values()) {
                if (repositorio.findByEdicaoIdAndSeloAndTipo(edicaoId, selo, tipo).isPresent()) {
                    jaExistentes++;
                    continue;
                }
                String referencia = storage.salvar(
                        artesPadrao.carregar(selo, tipo), ArtesPadrao.EXTENSAO);
                repositorio.save(new LayoutCertificado(
                        edicao, selo, tipo, referencia,
                        ArtesPadrao.LARGURA, ArtesPadrao.ALTURA,
                        ArtesPadrao.areaNome(), ArtesPadrao.areaUnidade(),
                        ArtesPadrao.areaCodigo()));
                criados++;
            }
        }
        return new ArtesPadraoAplicadas(criados, jaExistentes);
    }

    /** Resultado da aplicacao das artes padrao numa edicao. */
    public record ArtesPadraoAplicadas(int criados, int jaExistentes) {}

    @Transactional
    public LayoutCertificado atualizar(Long edicaoId, Long layoutId,
                                       MultipartFile imagem, AtualizarLayoutRequisicao dados) {
        LayoutCertificado layout = buscar(edicaoId, layoutId);
        exigirEdicaoEditavel(layout.getEdicao());

        DimensoesArte dimensoes =
                new DimensoesArte(layout.getImagemLargura(), layout.getImagemAltura());

        if (imagem != null && !imagem.isEmpty()) {
            byte[] conteudo = lerArquivo(imagem);
            dimensoes = validador.validar(conteudo, imagem.getContentType());
            String anterior = layout.getImagemRef();
            String referencia =
                    storage.salvar(conteudo, validador.extensaoDe(imagem.getContentType()));
            layout.substituirArte(referencia, dimensoes.largura(), dimensoes.altura());
            removerSilenciosamente(anterior);
        }

        validarAreas(dados.areaNome(), dados.areaUnidade(), dados.areaCodigo(), dimensoes);
        layout.redefinirAreas(dados.areaNome(), dados.areaUnidade(), dados.areaCodigo());
        return layout;
    }

    /**
     * Copia as posicoes de nome, unidade e codigo para <b>todos</b> os layouts da
     * edicao.
     *
     * <p>As oito pecas de uma edicao costumam ser a mesma arte em quatro cores:
     * o texto fica no mesmo lugar em todas. Posicionar oito vezes e trabalho
     * repetido e, pior, permite que as combinacoes divirjam entre si — o nome no
     * Ouro tres pixels acima do nome no Prata, sem que ninguem perceba ate os
     * certificados sairem lado a lado.
     *
     * <p>E <b>tudo ou nada</b>: se uma arte tiver dimensoes diferentes e as
     * caixas nao couberem nela, nada e aplicado e a mensagem diz em quais
     * combinacoes o problema esta. Aplicar em parte deixaria a edicao num estado
     * que ninguem pediu e que e dificil de perceber.
     *
     * @return quantos layouts foram alterados
     */
    @Transactional
    public int aplicarAreasEmTodos(Long edicaoId, AreaTexto nome, AreaTexto unidade,
                                   AreaCodigo codigo) {
        Edicao edicao = edicoes.buscar(edicaoId);
        exigirEdicaoEditavel(edicao);

        List<LayoutCertificado> todos = repositorio.findByEdicaoIdOrderBySeloAscTipoAsc(edicaoId);

        List<String> naoCabem = todos.stream()
                .filter(layout -> !cabeNaArte(layout, nome, unidade, codigo))
                .map(layout -> layout.getSelo() + " / " + layout.getTipo())
                .toList();

        if (!naoCabem.isEmpty()) {
            throw new RegraDeNegocioException(
                    "As posições não cabem na arte de: " + String.join(", ", naoCabem)
                    + ". Nenhum layout foi alterado.");
        }

        todos.forEach(layout -> layout.redefinirAreas(nome, unidade, codigo));
        return todos.size();
    }

    private boolean cabeNaArte(LayoutCertificado layout, AreaTexto nome, AreaTexto unidade,
                               AreaCodigo codigo) {
        try {
            validarAreas(nome, unidade, codigo,
                    new DimensoesArte(layout.getImagemLargura(), layout.getImagemAltura()));
            return true;
        } catch (RegraDeNegocioException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<LayoutCertificado> listar(Long edicaoId) {
        edicoes.buscar(edicaoId);
        return repositorio.findByEdicaoIdOrderBySeloAscTipoAsc(edicaoId);
    }

    @Transactional(readOnly = true)
    public List<String> pendencias(Long edicaoId) {
        return preRequisitos.pendencias(edicaoId);
    }

    @Transactional(readOnly = true)
    /**
     * Um layout pode ser ajustado enquanto a edicao esta em rascunho e,
     * <b>depois de publicada, enquanto nenhum certificado tiver sido emitido</b>.
     *
     * <p>A trava existe para que uma reemissao feita anos depois saia identica a
     * original (003/RF-8). Se nunca houve emissao, nao existe original do qual
     * divergir — e travar ali apenas impede o caso real de a arte definitiva
     * chegar depois da publicacao, sem proteger nada.
     *
     * <p>A partir da primeira emissao a trava e definitiva: dai em diante existe
     * documento em circulacao, e a fidelidade passa a valer mais que a correcao.
     */
    public boolean edicaoEditavel(Long edicaoId) {
        return edicoes.buscar(edicaoId).estaEmRascunho() || !emissoes.houveEmissao(edicaoId);
    }

    public boolean fonteInstitucionalDisponivel() {
        return fonte.institucionalDisponivel();
    }

    @Transactional(readOnly = true)
    public LayoutCertificado buscar(Long edicaoId, Long layoutId) {
        LayoutCertificado layout = repositorio.findById(layoutId).orElseThrow(
                () -> new NaoEncontradoException("Layout " + layoutId + " não encontrado."));
        if (!layout.getEdicao().getId().equals(edicaoId)) {
            throw new NaoEncontradoException("Layout " + layoutId + " não pertence a esta edição.");
        }
        return layout;
    }

    /** Arte crua da combinacao, para o editor visual desenhar as caixas sobre ela. */
    @Transactional(readOnly = true)
    public Arte arte(Long edicaoId, Long layoutId) {
        LayoutCertificado layout = buscar(edicaoId, layoutId);
        String referencia = layout.getImagemRef();
        String tipo = referencia.toLowerCase().endsWith(".jpg")
                || referencia.toLowerCase().endsWith(".jpeg")
                ? "image/jpeg"
                : "image/png";
        return new Arte(storage.ler(referencia), tipo);
    }

    /** Conteudo da arte junto do seu content type. */
    public record Arte(byte[] conteudo, String contentType) {}

    /**
     * Pre-visualizacao gerada pelo mesmo motor da emissao — o que o admin ve aqui
     * e exatamente o que sera emitido (003/RNF-2).
     */
    @Transactional(readOnly = true)
    public byte[] preview(Long edicaoId, Long layoutId, PreviewRequisicao requisicao) {
        LayoutCertificado layout = buscar(edicaoId, layoutId);

        String nome = valorOuPadrao(requisicao == null ? null : requisicao.nomeExemplo(),
                "Nome do Reconhecido de Exemplo");
        String unidade = valorOuPadrao(requisicao == null ? null : requisicao.unidadeExemplo(),
                "1ª Vara Cível da Comarca de Goiânia");
        String codigo = valorOuPadrao(requisicao == null ? null : requisicao.codigoExemplo(),
                CODIGO_EXEMPLO);

        return renderer.renderizar(layout, new CertificadoRenderer.DadosCertificado(
                nome, unidade, codigo, baseVerificacao + "/verificar/" + codigo));
    }

    /**
     * Guarda de escrita, com a mesma regra de {@link #edicaoEditavel(Long)}: o
     * que trava o layout nao e a publicacao, e a existencia de certificado
     * emitido (003/RF-8).
     */
    private void exigirEdicaoEditavel(Edicao edicao) {
        if (!edicao.estaEmRascunho() && emissoes.houveEmissao(edicao.getId())) {
            throw new ConflitoException(
                    "A edição " + edicao.getAno() + " já tem certificados emitidos: os layouts "
                    + "estão travados para que uma reemissão saia idêntica à original.");
        }
    }

    private void validarAreas(AreaTexto nome, AreaTexto unidade, AreaCodigo codigo,
                              DimensoesArte dimensoes) {
        conferirLimites("nome", nome.direita(), nome.base(), dimensoes);
        conferirLimites("unidade", unidade.direita(), unidade.base(), dimensoes);
        conferirLimites("código", codigo.x() + codigo.largura(), codigo.y() + codigo.altura(),
                dimensoes);
        if (codigo.temQr()) {
            AreaQr qr = codigo.qr();
            conferirLimites("QR", qr.x() + qr.tamanho(), qr.y() + qr.tamanho(), dimensoes);
        }
    }

    private void conferirLimites(String area, int direita, int base, DimensoesArte dimensoes) {
        if (direita > dimensoes.largura() || base > dimensoes.altura()) {
            throw new RegraDeNegocioException(
                    "A área de " + area + " ultrapassa os limites da arte ("
                            + dimensoes.largura() + "x" + dimensoes.altura() + " px).");
        }
    }

    private byte[] lerArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie a imagem da arte do certificado.");
        }
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    /**
     * A arte antiga e descartada apos a troca; uma falha aqui nao pode derrubar a
     * operacao, ja que o layout novo ja esta consistente.
     */
    private void removerSilenciosamente(String referencia) {
        try {
            storage.remover(referencia);
        } catch (RuntimeException e) {
            // arquivo orfao e inofensivo; nao interrompe a operacao
        }
    }

    private String valorOuPadrao(String valor, String padrao) {
        String aparado = Texto.aparar(valor);
        return aparado == null ? padrao : aparado;
    }
}
