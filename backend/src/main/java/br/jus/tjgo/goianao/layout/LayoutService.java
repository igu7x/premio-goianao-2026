package br.jus.tjgo.goianao.layout;

import br.jus.tjgo.goianao.comum.Texto;
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
    private final ImageStorage storage;
    private final ValidadorDeArte validador;
    private final CertificadoRenderer renderer;
    private final FonteInstitucional fonte;
    private final LayoutPreRequisitos preRequisitos;
    private final String baseVerificacao;

    public LayoutService(LayoutRepository repositorio,
                         EdicaoService edicoes,
                         ImageStorage storage,
                         ValidadorDeArte validador,
                         CertificadoRenderer renderer,
                         FonteInstitucional fonte,
                         LayoutPreRequisitos preRequisitos,
                         br.jus.tjgo.goianao.config.GoianaoProperties props) {
        this.repositorio = repositorio;
        this.edicoes = edicoes;
        this.storage = storage;
        this.validador = validador;
        this.renderer = renderer;
        this.fonte = fonte;
        this.preRequisitos = preRequisitos;
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
    public boolean edicaoEditavel(Long edicaoId) {
        return edicoes.buscar(edicaoId).estaEmRascunho();
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
     * Layouts so mudam com a edicao em rascunho: publicar trava a configuracao e
     * e o que preserva a fidelidade das reemissoes (003/RF-8).
     */
    private void exigirEdicaoEditavel(Edicao edicao) {
        if (!edicao.estaEmRascunho()) {
            throw new ConflitoException(
                    "A edição " + edicao.getAno() + " já foi publicada: os layouts estão travados.");
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
