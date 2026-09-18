package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.ConflitoException;
import br.jus.tjgo.goianao.config.GoianaoProperties;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.layout.LayoutCertificado;
import br.jus.tjgo.goianao.layout.LayoutRepository;
import br.jus.tjgo.goianao.layout.render.CertificadoRenderer;
import br.jus.tjgo.goianao.publico.IndiceDeVerificacao;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nucleo comum das emissoes de magistrado (005) e servidor (006).
 *
 * <p>Recebe ja resolvido <b>o que</b> deve ser impresso — quem valida o direito
 * de emitir e de onde vem o nome sao os servicos de cada perfil — e cuida do que
 * e igual nos dois casos: exigir edicao publicada, achar o layout daquela
 * edicao, manter um unico certificado logico com codigo estavel e renderizar.
 */
@Service
public class EmissaoService {

    /** PDF pronto para download, junto do codigo impresso nele. */
    public record CertificadoGerado(byte[] pdf, String codigoValidacao, String nomeArquivo) {}

    private final CertificadoEmitidoRepository repositorio;
    private final LayoutRepository layouts;
    private final CertificadoRenderer renderer;
    private final GeradorCodigoValidacao gerador;
    private final IndiceDeVerificacao indice;
    private final String baseVerificacao;

    public EmissaoService(CertificadoEmitidoRepository repositorio,
                          LayoutRepository layouts,
                          CertificadoRenderer renderer,
                          GeradorCodigoValidacao gerador,
                          IndiceDeVerificacao indice,
                          GoianaoProperties props) {
        this.repositorio = repositorio;
        this.layouts = layouts;
        this.renderer = renderer;
        this.gerador = gerador;
        this.indice = indice;
        this.baseVerificacao = props.baseVerificacao();
    }

    @Transactional
    public CertificadoGerado emitir(Edicao edicao, TipoCertificado tipo, String email,
                                    String nomeImpresso, UnidadeJudiciaria unidade, Selo selo) {

        exigirEdicaoPublicada(edicao);
        LayoutCertificado layout = exigirLayout(edicao, selo, tipo);

        // Um certificado logico por (edicao, tipo, pessoa, unidade): a reemissao
        // reencontra o registro e mantem o mesmo codigo de validacao.
        Optional<CertificadoEmitido> existente = repositorio
                .findByEdicaoIdAndTipoAndEmailEmissorAndUnidadeId(
                        edicao.getId(), tipo, email, unidade.getId());

        CertificadoEmitido certificado;
        if (existente.isPresent()) {
            certificado = existente.get();
            certificado.registrarReemissao(nomeImpresso, selo);
        } else {
            certificado = repositorio.save(new CertificadoEmitido(
                    edicao, tipo, email, nomeImpresso, unidade, selo, gerador.gerar()));
        }

        String codigo = certificado.getCodigoValidacao();

        // O certificado mora na base da edicao; a pagina publica que confere o
        // codigo nao tem sessao e nao saberia em qual base procurar (011/RF-10).
        indice.registrar(codigo, edicao.getId());

        byte[] pdf = renderer.renderizar(layout, new CertificadoRenderer.DadosCertificado(
                nomeImpresso, unidade.getNome(), codigo, urlDeVerificacao(codigo)));

        return new CertificadoGerado(pdf, codigo,
                nomeDoArquivo(edicao, selo, tipo, unidade));
    }

    /** URL que o QR codifica; a pagina publica resolve o codigo (007/RF-4). */
    public String urlDeVerificacao(String codigo) {
        String base = baseVerificacao == null ? "" : baseVerificacao.replaceAll("/+$", "");
        return base + "/verificar/" + codigo;
    }

    /**
     * Rascunho nao emite; qualquer edicao publicada emite, inclusive as
     * anteriores (constituicao, principio 8).
     */
    private void exigirEdicaoPublicada(Edicao edicao) {
        if (!edicao.estaPublicada()) {
            throw new ConflitoException("A edição " + edicao.getAno()
                    + " ainda está em rascunho: não há emissão até ela ser publicada.");
        }
    }

    private LayoutCertificado exigirLayout(Edicao edicao, Selo selo, TipoCertificado tipo) {
        return layouts.findByEdicaoIdAndSeloAndTipo(edicao.getId(), selo, tipo)
                .orElseThrow(() -> new ConflitoException(
                        "A edição " + edicao.getAno() + " não tem layout configurado para "
                                + selo.rotulo() + " / " + tipo.rotulo()
                                + ". Procure a administração do prêmio."));
    }

    @Transactional(readOnly = true)
    public boolean temLayout(Long edicaoId, Selo selo, TipoCertificado tipo) {
        return layouts.existsByEdicaoIdAndSeloAndTipo(edicaoId, selo, tipo);
    }

    @Transactional(readOnly = true)
    public Optional<CertificadoEmitido> jaEmitido(Long edicaoId, TipoCertificado tipo,
                                                  String email, Long unidadeId) {
        return repositorio.findByEdicaoIdAndTipoAndEmailEmissorAndUnidadeId(
                edicaoId, tipo, email, unidadeId);
    }

    private String nomeDoArquivo(Edicao edicao, Selo selo, TipoCertificado tipo,
                                 UnidadeJudiciaria unidade) {
        String unidadeSlug = Texto.canonicalizar(unidade.getNome())
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return String.format(Locale.ROOT, "certificado-goianao-%d-%s-%s-%s.pdf",
                edicao.getAno(),
                tipo.name().toLowerCase(Locale.ROOT),
                selo.name().toLowerCase(Locale.ROOT),
                unidadeSlug);
    }
}
