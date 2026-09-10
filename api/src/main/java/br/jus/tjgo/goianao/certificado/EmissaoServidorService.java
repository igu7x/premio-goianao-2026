package br.jus.tjgo.goianao.certificado;

import br.jus.tjgo.goianao.certificado.dto.EdicaoOpcaoResposta;
import br.jus.tjgo.goianao.certificado.dto.OpcaoEmissaoResposta;
import br.jus.tjgo.goianao.comum.Selo;
import br.jus.tjgo.goianao.comum.TipoCertificado;
import br.jus.tjgo.goianao.comum.Texto;
import br.jus.tjgo.goianao.comum.erro.AcessoNegadoException;
import br.jus.tjgo.goianao.edicao.Edicao;
import br.jus.tjgo.goianao.edicao.EdicaoService;
import br.jus.tjgo.goianao.magistrado.MagistradoService;
import br.jus.tjgo.goianao.seguranca.UsuarioAutenticado;
import br.jus.tjgo.goianao.servidor.ServidorHabilitadoService;
import br.jus.tjgo.goianao.unidade.UnidadeJudiciaria;
import br.jus.tjgo.goianao.unidade.UnidadeService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissao do certificado de servidor (006).
 *
 * <p>Duas coisas distinguem esta emissao da do magistrado. A elegibilidade vem
 * da <b>lista de servidores habilitados</b> daquela edicao (008) e nunca do
 * EGESP ao vivo — e o que permite reemitir uma edicao antiga com o quadro de
 * pessoal <b>daquela epoca</b>. E o nome impresso vem do <b>SSO</b>, porque a
 * lista registra quem pode emitir (e-mail x unidade), nao a grafia oficial do
 * nome (006/RF-1, constituicao principio 3a).
 */
@Service
public class EmissaoServidorService {

    private static final TipoCertificado TIPO = TipoCertificado.SERVIDOR;

    private final ServidorHabilitadoService habilitados;
    private final MagistradoService magistrados;
    private final UnidadeService unidades;
    private final EdicaoService edicoes;
    private final EmissaoService emissao;

    public EmissaoServidorService(ServidorHabilitadoService habilitados,
                                  MagistradoService magistrados,
                                  UnidadeService unidades,
                                  EdicaoService edicoes,
                                  EmissaoService emissao) {
        this.habilitados = habilitados;
        this.magistrados = magistrados;
        this.unidades = unidades;
        this.edicoes = edicoes;
        this.emissao = emissao;
    }

    @Transactional(readOnly = true)
    public List<EdicaoOpcaoResposta> edicoesDisponiveis(String email) {
        return habilitados.edicoesPublicadasHabilitadas(email).stream()
                .map(edicoes::buscar)
                .sorted(Comparator.comparing(Edicao::getAno).reversed())
                .map(EdicaoOpcaoResposta::de)
                .toList();
    }

    /**
     * Uma opcao por unidade em que o e-mail esta habilitado <b>e</b> que foi
     * reconhecida na edicao; cada uma com o maior selo daquela unidade.
     */
    @Transactional(readOnly = true)
    public List<OpcaoEmissaoResposta> opcoes(Long edicaoId, String email) {
        Edicao edicao = edicoes.resolverAlvo(edicaoId);

        List<OpcaoEmissaoResposta> opcoes = new ArrayList<>();
        for (Long unidadeId : habilitados.unidadesHabilitadas(edicao.getId(), email)) {
            Optional<Selo> maiorSelo = magistrados.maiorSeloDaUnidade(edicao.getId(), unidadeId);
            if (maiorSelo.isEmpty()) {
                // Unidade que saiu da lista de reconhecidas: sem selo, nao emite.
                continue;
            }
            UnidadeJudiciaria unidade = unidades.buscar(unidadeId);
            Optional<CertificadoEmitido> emitido =
                    emissao.jaEmitido(edicao.getId(), TIPO, email, unidadeId);

            opcoes.add(new OpcaoEmissaoResposta(
                    unidadeId,
                    unidade.getNome(),
                    maiorSelo.get(),
                    emissao.temLayout(edicao.getId(), maiorSelo.get(), TIPO),
                    emitido.isPresent(),
                    emitido.map(CertificadoEmitido::getCodigoValidacao).orElse(null),
                    emitido.map(CertificadoEmitido::getEmitidoEm).orElse(null),
                    emitido.map(CertificadoEmitido::getTotalEmissoes).orElse(0)));
        }

        opcoes.sort(Comparator.comparing(OpcaoEmissaoResposta::unidadeNome));
        return opcoes;
    }

    @Transactional
    public EmissaoService.CertificadoGerado emitir(Long edicaoId, Long unidadeId,
                                                   UsuarioAutenticado usuario) {
        Edicao edicao = edicoes.resolverAlvo(edicaoId);
        String email = usuario.email();

        if (!habilitados.estaHabilitado(edicao.getId(), unidadeId, email)) {
            throw new AcessoNegadoException(
                    "Você não consta na lista de servidores habilitados desta unidade"
                            + " na edição selecionada.");
        }

        Selo maiorSelo = magistrados.maiorSeloDaUnidade(edicao.getId(), unidadeId)
                .orElseThrow(() -> new AcessoNegadoException(
                        "Esta unidade não foi reconhecida na edição selecionada."));

        UnidadeJudiciaria unidade = unidades.buscar(unidadeId);
        String nomeImpresso = nomeParaImprimir(edicao.getId(), unidadeId, usuario);

        return emissao.emitir(edicao, TIPO, email, nomeImpresso, unidade, maiorSelo);
    }

    /** Nome do SSO; so na ausencia dele recorre ao nome semeado na lista (006/RF-1). */
    private String nomeParaImprimir(Long edicaoId, Long unidadeId, UsuarioAutenticado usuario) {
        String doSso = Texto.aparar(usuario.nome());
        if (doSso != null) {
            return doSso;
        }
        return habilitados.nomeSalvo(edicaoId, unidadeId, usuario.email())
                .map(Texto::aparar)
                .orElse("Servidor");
    }
}
