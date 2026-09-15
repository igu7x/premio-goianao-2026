import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi, salvarArquivo } from '../api/cliente'
import type { EdicaoOpcao, OpcaoEmissao } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio, formatarDataHora } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import { Disco, rotuloDoSelo } from '../componentes/Selo'
import { useSessao } from '../sessao/SessaoContexto'

/**
 * Emissão pelo próprio reconhecido (features 005 e 006).
 *
 * Cada opção é um cartão com a cara do que entrega — o selo cunhado e a unidade
 * em serifa. Antes eram linhas de tabela, e a coisa mais valiosa do sistema
 * parecia um item de lista qualquer.
 */
export function MeusCertificados() {
  const { tem } = useSessao()

  return (
    <div className="pagina">
      <header className="cabecalho-pagina">
        <div>
          <span className="rotulo">Emissão</span>
          <h1 className="titulo-pagina" style={{ marginTop: 6 }}>
            Meus certificados
          </h1>
          <p>
            Seus dados vêm do login corporativo — nada aqui é digitado. A edição vigente já vem
            selecionada; para reemitir um certificado antigo, escolha a edição correspondente.
          </p>
        </div>
      </header>

      {tem('MAGISTRADO') && (
        <PainelDeEmissao
          titulo="Como magistrado reconhecido"
          descricao="Uma opção por unidade pela qual você foi reconhecido, com o selo daquele reconhecimento."
          base="/api/magistrado/certificados"
        />
      )}

      {tem('SERVIDOR') && (
        <PainelDeEmissao
          titulo="Como servidor da unidade"
          descricao="Uma opção por unidade em que você consta como habilitado, sempre com o maior selo que ela recebeu."
          base="/api/servidor/certificados"
        />
      )}
    </div>
  )
}

function PainelDeEmissao({
  titulo,
  descricao,
  base,
}: {
  titulo: string
  descricao: string
  base: string
}) {
  const [edicoes, setEdicoes] = useState<EdicaoOpcao[] | null>(null)
  const [edicaoId, setEdicaoId] = useState<number | null>(null)
  const [opcoes, setOpcoes] = useState<OpcaoEmissao[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [emitindo, setEmitindo] = useState<number | null>(null)
  const avisos = useAvisos()

  useEffect(() => {
    let ativo = true
    api
      .get<EdicaoOpcao[]>(`${base}/edicoes`)
      .then((lista) => {
        if (!ativo) return
        setEdicoes(lista)
        setEdicaoId((lista.find((e) => e.vigente) ?? lista[0])?.id ?? null)
      })
      .catch((e: ErroApi) => ativo && setErro(e.message))
    return () => {
      ativo = false
    }
  }, [base])

  const carregarOpcoes = useCallback(async () => {
    if (edicaoId === null) {
      setOpcoes([])
      return
    }
    try {
      setOpcoes(await api.get<OpcaoEmissao[]>(`${base}?edicaoId=${edicaoId}`))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar as opções.')
    }
  }, [base, edicaoId])

  useEffect(() => {
    void carregarOpcoes()
  }, [carregarOpcoes])

  async function emitir(opcao: OpcaoEmissao) {
    setEmitindo(opcao.unidadeId)
    setErro(null)
    try {
      const { blob, nomeArquivo, codigo } = await api.baixarPdf(`${base}/emitir`, {
        edicaoId,
        unidadeId: opcao.unidadeId,
      })
      salvarArquivo(blob, nomeArquivo)
      avisos.sucesso(
        `Certificado de ${opcao.unidadeNome} baixado`,
        codigo
          ? `Código de validação ${codigo} — impresso no PDF e estável entre reemissões.`
          : undefined,
      )
      await carregarOpcoes()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao emitir o certificado.')
    } finally {
      setEmitindo(null)
    }
  }

  if (!edicoes) {
    // O aviso de erro do corpo fica abaixo deste ponto: sem esta saída, uma
    // falha na primeira chamada deixaria a tela girando e calada.
    return erro ? (
      <section className="bloco" style={{ marginBottom: 'var(--e5)' }}>
        <div className="bloco-corpo">
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      </section>
    ) : (
      <Carregando />
    )
  }

  return (
    <section className="bloco" style={{ marginBottom: 'var(--e5)' }}>
      <div className="bloco-cabecalho">
        <div>
          <h2 className="titulo-secao">{titulo}</h2>
          <p className="apoio">{descricao}</p>
        </div>
        {edicoes.length > 0 && (
          <div className="seletor-edicao">
            <label className="rotulo" htmlFor={`edicao-${base}`}>
              Edição
            </label>
            <select
              id={`edicao-${base}`}
              value={edicaoId ?? ''}
              onChange={(evento) => setEdicaoId(Number(evento.target.value))}
            >
              {edicoes.map((edicao) => (
                <option key={edicao.id} value={edicao.id}>
                  {edicao.ano}
                  {edicao.vigente ? ' (vigente)' : ''}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {erro && (
        <div style={{ padding: 'var(--e4) var(--e5) 0' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {edicoes.length === 0 ? (
        <EstadoVazio
          titulo="Nenhum certificado disponível"
          descricao="Você ainda não consta como reconhecido nem como habilitado em nenhuma edição publicada. Se acredita que deveria constar, procure a administração do prêmio."
        />
      ) : !opcoes ? (
        !erro && <Carregando />
      ) : opcoes.length === 0 ? (
        <EstadoVazio
          titulo="Nada a emitir nesta edição"
          descricao="Escolha outra edição no seletor acima."
        />
      ) : (
        <div className="grade-certificados">
          {opcoes.map((opcao, indice) => (
            <article
              key={opcao.unidadeId}
              className={`certificado-cartao liga-${opcao.selo.toLowerCase()}`}
              style={{ animationDelay: `${indice * 60}ms` }}
            >
              <div className="certificado-topo">
                <Disco selo={opcao.selo} tamanho="g" />
                <div style={{ minWidth: 0 }}>
                  <div className="certificado-unidade">{opcao.unidadeNome}</div>
                  <div className={`certificado-liga selo-${opcao.selo.toLowerCase()}`}>
                    Selo {rotuloDoSelo(opcao.selo)}
                  </div>
                </div>
              </div>

              <button
                type="button"
                className="botao"
                disabled={!opcao.layoutDisponivel || emitindo === opcao.unidadeId}
                onClick={() => void emitir(opcao)}
              >
                {emitindo === opcao.unidadeId ? (
                  <span className="giro" />
                ) : (
                  <Icone nome="baixar" tamanho={16} />
                )}
                {emitindo === opcao.unidadeId
                  ? 'Gerando…'
                  : opcao.jaEmitido
                    ? 'Baixar novamente'
                    : 'Emitir certificado'}
              </button>

              <div className="certificado-meta">
                {opcao.jaEmitido && opcao.codigoValidacao && (
                  <span className="codigo-emitido">
                    <Icone nome="confirmado" tamanho={13} />
                    {opcao.codigoValidacao}
                  </span>
                )}
                {opcao.jaEmitido ? (
                  <span>
                    emitido em {formatarDataHora(opcao.emitidoEm)}
                    {opcao.totalEmissoes > 1 && ` · ${opcao.totalEmissoes} emissões`}
                  </span>
                ) : (
                  <span>ainda não emitido</span>
                )}
                {!opcao.layoutDisponivel && (
                  <span className="certificado-indisponivel">
                    layout indisponível nesta edição
                  </span>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
