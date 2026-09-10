package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.comum.erro.AcessoNegadoException;
import br.jus.tjgo.goianao.certificado.dto.EdicaoOpcaoResposta;
import br.jus.tjgo.goianao.certificado.dto.OpcaoEmissaoResposta;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.magistrado.MagistradoReconhecido;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.magistrado.Reconhecimento;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissao do certificado de magistrado (005).
 *
 * <p>O direito de emitir vem do cadastro da edicao: uma opcao por unidade
 * reconhecida, com o selo que o administrador registrou. O nome impresso
 * tambem vem do cadastro, <b>nao</b> do SSO — como o cadastro e travado ao
 * publicar, reemitir uma edicao antiga imprime sempre a mesma grafia, ainda que
 * o nome no SSO mude depois (005/RF-4, constituicao principio 3a).
 *
 * <p>Quem e o magistrado, o SSO diz pelo e-mail corporativo (DI-24).
 */
@Service
public class EmissaoMagistradoService {

    private static final TipoCertificado TIPO = TipoCertificado.MAGISTRADO;

    private final MagistradoService magistrados;
    private final EdicaoService edicoes;
    private final EmissaoService emissao;

    public EmissaoMagistradoService(MagistradoService magistrados, EdicaoService edicoes,
                                    EmissaoService emissao) {
        this.magistrados = magistrados;
        this.edicoes = edicoes;
        this.emissao = emissao;
    }

    /** Edicoes publicadas em que o magistrado tem reconhecimento (005/RF-1). */
    @Transactional(readOnly = true)
    public List<EdicaoOpcaoResposta> edicoesDisponiveis(String email) {
        return magistrados.edicoesPublicadasDe(email).stream()
                .map(edicoes::buscar)
                .sorted(Comparator.comparing(Edicao::getAno).reversed())
                .map(EdicaoOpcaoResposta::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpcaoEmissaoResposta> opcoes(Long edicaoId, String email) {
        Edicao edicao = edicoes.resolverAlvo(edicaoId);

        return magistrados.reconhecimentosDe(edicao.getId(), email).stream()
                .sorted(Comparator.comparing(r -> r.getUnidade().getNome()))
                .map(r -> montarOpcao(edicao, email, r))
                .toList();
    }

    private OpcaoEmissaoResposta montarOpcao(Edicao edicao, String email, Reconhecimento r) {
        Optional<CertificadoEmitido> emitido = emissao.jaEmitido(
                edicao.getId(), TIPO, email, r.getUnidade().getId());

        return new OpcaoEmissaoResposta(
                r.getUnidade().getId(),
                r.getUnidade().getNome(),
                r.getSelo(),
                emissao.temLayout(edicao.getId(), r.getSelo(), TIPO),
                emitido.isPresent(),
                emitido.map(CertificadoEmitido::getCodigoValidacao).orElse(null),
                emitido.map(CertificadoEmitido::getEmitidoEm).orElse(null),
                emitido.map(CertificadoEmitido::getTotalEmissoes).orElse(0));
    }

    @Transactional
    public EmissaoService.CertificadoGerado emitir(Long edicaoId, Long unidadeId, String email) {
        Edicao edicao = edicoes.resolverAlvo(edicaoId);

        // A unidade tem que ser dele naquela edicao: nada vem por parametro
        // confiavel a nao ser o proprio identificador da unidade (005/RF-6).
        Reconhecimento reconhecimento = magistrados
                .reconhecimentosDe(edicao.getId(), email).stream()
                .filter(r -> r.getUnidade().getId().equals(unidadeId))
                .findFirst()
                .orElseThrow(() -> new AcessoNegadoException(
                        "Você não foi reconhecido por esta unidade na edição selecionada."));

        MagistradoReconhecido cadastro = magistrados
                .porEmailNaEdicao(edicao.getId(), email)
                .orElseThrow(() -> new AcessoNegadoException(
                        "Você não consta como reconhecido nesta edição."));

        return emissao.emitir(edicao, TIPO, email, cadastro.getNome(),
                reconhecimento.getUnidade(), reconhecimento.getSelo());
    }
}
