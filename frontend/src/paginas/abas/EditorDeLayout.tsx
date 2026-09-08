import { useEffect, useId, useRef, useState } from 'react'
import { abrirEmNovaAba, api, ErroApi, lerToken, urlDaApi } from '../../api/cliente'
import { Icone } from '../../componentes/Icone'
import type {
  Alinhamento,
  AreaCodigo,
  AreaTexto,
  Layout,
  Selo,
  TipoCertificado,
} from '../../api/tipos'
import { Aviso, Modal, rotuloDoSelo } from '../../componentes/Basicos'

type Alvo = 'nome' | 'unidade' | 'codigo' | 'qr'

interface Props {
  edicaoId: number
  selo: Selo
  tipo: TipoCertificado
  layout: Layout | null
  /** Falso a partir da primeira emissão da edição: aí o layout trava (003/RF-8). */
  editavel: boolean
  aoFechar: () => void
  aoSalvar: () => Promise<void>
}

const ROTULO_ALVO: Record<Alvo, string> = {
  nome: 'Nome',
  unidade: 'Unidade',
  codigo: 'Código',
  qr: 'QR',
}

const AMOSTRA: Record<Alvo, string> = {
  nome: 'Nome do Reconhecido',
  unidade: '1ª Vara Cível da Comarca de Goiânia',
  codigo: 'ABCD-1234-EFGH',
  qr: '',
}

/**
 * Editor visual de posicionamento (003/RF-3).
 *
 * O administrador arrasta e redimensiona as caixas sobre a arte; o que se
 * persiste sao coordenadas em <b>pixels da imagem-base</b>, nunca da tela — por
 * isso todo movimento e convertido pela escala do canvas. Fonte, cor e tamanho
 * nao aparecem aqui de proposito: a fonte e institucional, a cor e preta e o
 * tamanho e auto-ajustado a caixa na hora de gerar o PDF.
 */
export function EditorDeLayout({
  edicaoId,
  selo,
  tipo,
  layout,
  editavel,
  aoFechar,
  aoSalvar,
}: Props) {
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [urlLocal, setUrlLocal] = useState<string | null>(null)
  const [dimensoes, setDimensoes] = useState<{ largura: number; altura: number } | null>(
    layout ? { largura: layout.imagemLargura, altura: layout.imagemAltura } : null,
  )

  const [areaNome, setAreaNome] = useState<AreaTexto | null>(layout?.areaNome ?? null)
  const [areaUnidade, setAreaUnidade] = useState<AreaTexto | null>(layout?.areaUnidade ?? null)
  const [areaCodigo, setAreaCodigo] = useState<AreaCodigo | null>(layout?.areaCodigo ?? null)

  const [alvo, setAlvo] = useState<Alvo>('nome')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  /**
   * As oito peças de uma edição costumam ser a mesma arte em quatro cores, com
   * o texto no mesmo lugar. Posicionar oito vezes é trabalho repetido e deixa as
   * combinações divergirem entre si sem ninguém perceber — o nome no Ouro três
   * pixels acima do nome no Prata, visível só quando saem lado a lado.
   */
  const [replicar, setReplicar] = useState(false)

  const telaRef = useRef<HTMLDivElement>(null)
  const arraste = useRef<{
    alvo: Alvo
    modo: 'mover' | 'redimensionar'
    inicioX: number
    inicioY: number
    origem: { x: number; y: number; largura: number; altura: number }
  } | null>(null)

  const [urlRemota, setUrlRemota] = useState<string | null>(null)
  const urlDaArte = urlLocal ?? urlRemota

  useEffect(() => () => {
    if (urlLocal) URL.revokeObjectURL(urlLocal)
  }, [urlLocal])

  /**
   * A arte ja salva vem de um endpoint autenticado; como <img> nao envia o
   * cabecalho Authorization, buscamos o binario e usamos uma URL de objeto.
   */
  useEffect(() => {
    if (!layout) return
    let ativo = true
    let criada: string | null = null

    fetch(urlDaApi(layout.imagemUrl), { headers: { Authorization: `Bearer ${lerToken() ?? ''}` } })
      .then((resposta) => (resposta.ok ? resposta.blob() : Promise.reject(resposta)))
      .then((blob) => {
        if (!ativo) return
        criada = URL.createObjectURL(blob)
        setUrlRemota(criada)
      })
      .catch(() => setErro('Não foi possível carregar a arte salva.'))

    return () => {
      ativo = false
      if (criada) URL.revokeObjectURL(criada)
    }
  }, [layout])

  /** Ao escolher a arte, lemos as dimensoes reais e propomos caixas iniciais. */
  function receberArquivo(novo: File) {
    const url = URL.createObjectURL(novo)
    const imagem = new Image()
    imagem.onload = () => {
      const largura = imagem.naturalWidth
      const altura = imagem.naturalHeight
      setDimensoes({ largura, altura })
      if (!areaNome) {
        setAreaNome(caixa(0.13 * largura, 0.46 * altura, 0.74 * largura, 0.062 * altura))
        setAreaUnidade(caixa(0.13 * largura, 0.6 * altura, 0.74 * largura, 0.046 * altura))
        setAreaCodigo({
          ...caixa(0.148 * largura, 0.9 * altura, 0.285 * largura, 0.021 * altura),
          alinhamento: 'ESQUERDA',
          qr: { x: Math.round(0.08 * largura), y: Math.round(0.838 * altura),
                tamanho: Math.round(0.057 * largura) },
        })
      }
      setArquivo(novo)
      setUrlLocal(url)
    }
    imagem.onerror = () => {
      URL.revokeObjectURL(url)
      setErro('Não foi possível ler a imagem escolhida.')
    }
    imagem.src = url
  }

  /**
   * Converte um deslocamento em pixels de tela para pixels da arte. E medido a
   * cada movimento porque o canvas e responsivo: a mesma caixa vale coordenadas
   * diferentes numa janela estreita e numa larga.
   */
  function paraPixelsDaArte(deltaTela: number): number {
    const tela = telaRef.current
    if (!tela || !dimensoes) return deltaTela
    return deltaTela * (dimensoes.largura / tela.clientWidth)
  }

  function iniciarArraste(
    evento: React.PointerEvent,
    qual: Alvo,
    modo: 'mover' | 'redimensionar',
  ) {
    evento.preventDefault()
    evento.stopPropagation()
    const atual = areaDe(qual)
    if (!atual) return
    // Layout travado pela primeira emissão: selecionar para conferir as
    // coordenadas continua valendo, mover não. Deixar arrastar aqui só levaria
    // o administrador a ajustar tudo e descobrir no fim que não há como salvar.
    if (!editavel) {
      setAlvo(qual)
      return
    }

    setAlvo(qual)
    arraste.current = {
      alvo: qual,
      modo,
      inicioX: evento.clientX,
      inicioY: evento.clientY,
      origem: atual,
    }
    ;(evento.target as HTMLElement).setPointerCapture(evento.pointerId)
  }

  function moverPonteiro(evento: React.PointerEvent) {
    const estado = arraste.current
    if (!estado || !dimensoes) return

    const dx = paraPixelsDaArte(evento.clientX - estado.inicioX)
    const dy = paraPixelsDaArte(evento.clientY - estado.inicioY)
    const { origem } = estado

    if (estado.modo === 'mover') {
      aplicar(estado.alvo, {
        x: limitar(origem.x + dx, 0, dimensoes.largura - origem.largura),
        y: limitar(origem.y + dy, 0, dimensoes.altura - origem.altura),
        largura: origem.largura,
        altura: origem.altura,
      })
      return
    }

    // Redimensionar mantendo o QR quadrado: e o que o leitor espera.
    const largura = limitar(origem.largura + dx, 40, dimensoes.largura - origem.x)
    const altura =
      estado.alvo === 'qr'
        ? limitar(largura, 40, dimensoes.altura - origem.y)
        : limitar(origem.altura + dy, 20, dimensoes.altura - origem.y)

    aplicar(estado.alvo, { x: origem.x, y: origem.y, largura, altura })
  }

  function encerrarArraste() {
    arraste.current = null
  }

  function areaDe(qual: Alvo): { x: number; y: number; largura: number; altura: number } | null {
    if (qual === 'nome') return areaNome
    if (qual === 'unidade') return areaUnidade
    if (qual === 'codigo') return areaCodigo
    const qr = areaCodigo?.qr
    return qr ? { x: qr.x, y: qr.y, largura: qr.tamanho, altura: qr.tamanho } : null
  }

  function aplicar(
    qual: Alvo,
    valores: { x: number; y: number; largura: number; altura: number },
  ) {
    const arredondado = {
      x: Math.round(valores.x),
      y: Math.round(valores.y),
      largura: Math.round(valores.largura),
      altura: Math.round(valores.altura),
    }
    if (qual === 'nome' && areaNome) {
      setAreaNome({ ...areaNome, ...arredondado })
    } else if (qual === 'unidade' && areaUnidade) {
      setAreaUnidade({ ...areaUnidade, ...arredondado })
    } else if (qual === 'codigo' && areaCodigo) {
      setAreaCodigo({ ...areaCodigo, ...arredondado })
    } else if (qual === 'qr' && areaCodigo) {
      setAreaCodigo({
        ...areaCodigo,
        qr: { x: arredondado.x, y: arredondado.y, tamanho: arredondado.largura },
      })
    }
  }

  function definirAlinhamento(valor: Alinhamento) {
    if (alvo === 'nome' && areaNome) setAreaNome({ ...areaNome, alinhamento: valor })
    if (alvo === 'unidade' && areaUnidade) setAreaUnidade({ ...areaUnidade, alinhamento: valor })
    if (alvo === 'codigo' && areaCodigo) setAreaCodigo({ ...areaCodigo, alinhamento: valor })
  }

  function alternarQr() {
    if (!areaCodigo || !dimensoes) return
    if (areaCodigo.qr) {
      setAreaCodigo({ ...areaCodigo, qr: null })
      if (alvo === 'qr') setAlvo('codigo')
    } else {
      const tamanho = Math.round(0.057 * dimensoes.largura)
      setAreaCodigo({
        ...areaCodigo,
        qr: { x: Math.round(0.08 * dimensoes.largura), y: areaCodigo.y - tamanho - 20, tamanho },
      })
    }
  }

  async function salvar() {
    if (!areaNome || !areaUnidade || !areaCodigo) {
      setErro('Defina as áreas de nome, unidade e código.')
      return
    }
    if (!layout && !arquivo) {
      setErro('Envie a arte do certificado.')
      return
    }

    setSalvando(true)
    setErro(null)
    try {
      const dados = new FormData()
      if (layout) {
        if (arquivo) dados.append('imagem', arquivo)
        dados.append(
          'dados',
          new Blob([JSON.stringify({ areaNome, areaUnidade, areaCodigo })], {
            type: 'application/json',
          }),
        )
        await api.enviarArquivo(`/api/edicoes/${edicaoId}/layouts/${layout.id}`, dados, 'PUT')
      } else {
        dados.append('imagem', arquivo as File)
        dados.append(
          'dados',
          new Blob(
            [JSON.stringify({ selo, tipo, areaNome, areaUnidade, areaCodigo, substituir: true })],
            { type: 'application/json' },
          ),
        )
        await api.enviarArquivo(`/api/edicoes/${edicaoId}/layouts`, dados)
      }

      // Replicar depois de salvar, não antes: se o salvamento falhar, os
      // outros sete layouts não são tocados.
      if (replicar) {
        await api.put(`/api/edicoes/${edicaoId}/layouts/areas`, {
          areaNome,
          areaUnidade,
          areaCodigo,
        })
      }
      await aoSalvar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao salvar o layout.')
      setSalvando(false)
    }
  }

  async function previsualizar() {
    if (!layout) return
    try {
      const { blob } = await api.baixarPdf(
        `/api/edicoes/${edicaoId}/layouts/${layout.id}/preview`,
        {
          nomeExemplo: AMOSTRA.nome,
          unidadeExemplo: AMOSTRA.unidade,
          codigoExemplo: AMOSTRA.codigo,
        },
      )
      abrirEmNovaAba(blob)
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao gerar a prévia.')
    }
  }

  const areaSelecionada = areaDe(alvo)

  return (
    <Modal
      largo
      titulo={`Layout ${rotuloDoSelo(selo)} / ${tipo === 'MAGISTRADO' ? 'Magistrado' : 'Servidor'}`}
      descricao={
        editavel
          ? 'Arraste as caixas sobre a arte. As coordenadas são gravadas em pixels da imagem original.'
          : 'Esta edição já tem certificados emitidos, então o layout está travado — é o que garante que uma reemissão saia idêntica à original. Você pode conferir as posições e gerar a prévia.'
      }
      aoFechar={aoFechar}
      rodape={
        <>
          {layout && (
            <button
              type="button"
              className="botao botao-neutro"
              onClick={() => void previsualizar()}
            >
              Pré-visualizar PDF
            </button>
          )}
          {editavel && (
            <label className="replicar" title="Aplica só as posições; a arte de cada combinação continua a dela.">
              <input
                type="checkbox"
                checked={replicar}
                onChange={(evento) => setReplicar(evento.target.checked)}
              />
              Aplicar estas posições às 8 combinações
            </label>
          )}
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            {editavel ? 'Cancelar' : 'Fechar'}
          </button>
          {editavel && (
            <button
              type="button"
              className="botao"
              disabled={salvando}
              onClick={() => void salvar()}
            >
              {salvando
                ? 'Salvando…'
                : replicar
                  ? 'Salvar e aplicar a todos'
                  : 'Salvar layout'}
            </button>
          )}
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      {!urlDaArte ? (
        <div className="campo">
          <label htmlFor="arte">Arte do certificado</label>
          <input
            id="arte"
            type="file"
            accept="image/png,image/jpeg"
            onChange={(evento) => {
              const escolhido = evento.target.files?.[0]
              if (escolhido) receberArquivo(escolhido)
            }}
          />
          <span className="campo-dica">
            A4 paisagem a 300 DPI (aproximadamente 3508 × 2480 px), em PNG ou JPEG. Título, ano e
            selo já devem estar na própria arte — o sistema só escreve nome, unidade e código.
          </span>
        </div>
      ) : (
        <div className="editor">
          <div
            className="editor-tela"
            ref={telaRef}
            onPointerMove={moverPonteiro}
            onPointerUp={encerrarArraste}
            onPointerCancel={encerrarArraste}
          >
            <img src={urlDaArte} alt="Arte do certificado" />

            {/* Sobre a prancheta, não só no topo do modal: o aviso do cabeçalho
                sai da tela assim que a pessoa rola até a arte, e aí a conclusão
                é de que o editor está quebrado. */}
            {!editavel && (
              <div className="tela-travada">
                <Icone nome="atencao" tamanho={14} />
                Travado: esta edição já emitiu certificados
              </div>
            )}

            {(['nome', 'unidade', 'codigo', 'qr'] as Alvo[]).map((qual) => {
              const area = areaDe(qual)
              if (!area || !dimensoes) return null
              return (
                <div
                  key={qual}
                  className={[
                    'caixa',
                    qual === 'qr' ? 'caixa-qr' : '',
                    alvo === qual ? 'selecionada' : '',
                    editavel ? '' : 'caixa-travada',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  style={{
                    left: `${(area.x / dimensoes.largura) * 100}%`,
                    top: `${(area.y / dimensoes.altura) * 100}%`,
                    width: `${(area.largura / dimensoes.largura) * 100}%`,
                    height: `${(area.altura / dimensoes.altura) * 100}%`,
                  }}
                  onPointerDown={(evento) => iniciarArraste(evento, qual, 'mover')}
                >
                  <span className="caixa-rotulo">{ROTULO_ALVO[qual]}</span>
                  {qual !== 'qr' && <span className="caixa-amostra">{AMOSTRA[qual]}</span>}
                  {editavel && (
                    <span
                      className="punho"
                      onPointerDown={(evento) => iniciarArraste(evento, qual, 'redimensionar')}
                    />
                  )}
                </div>
              )
            })}
          </div>

          <aside className="editor-painel">
            <div>
              <span className="rotulo">Campo</span>
              <div className="editor-lista" style={{ marginTop: 8 }}>
                {(['nome', 'unidade', 'codigo'] as Alvo[]).map((qual) => (
                  <button
                    key={qual}
                    type="button"
                    className={alvo === qual ? 'editor-alvo ativo' : 'editor-alvo'}
                    onClick={() => setAlvo(qual)}
                  >
                    {ROTULO_ALVO[qual]}
                  </button>
                ))}
                <button
                  type="button"
                  className={alvo === 'qr' ? 'editor-alvo ativo' : 'editor-alvo'}
                  disabled={!editavel && !areaCodigo?.qr}
                  onClick={() => (areaCodigo?.qr ? setAlvo('qr') : alternarQr())}
                >
                  QR de verificação
                  <span className="secundaria">{areaCodigo?.qr ? 'ativo' : 'desligado'}</span>
                </button>
              </div>
            </div>

            {areaSelecionada && dimensoes && (
              <>
                <div className="editor-numeros">
                  <Numero
                    rotulo="X"
                    valor={areaSelecionada.x}
                    editavel={editavel}
                    aoMudar={(v) => aplicar(alvo, { ...areaSelecionada, x: v })}
                  />
                  <Numero
                    rotulo="Y"
                    valor={areaSelecionada.y}
                    editavel={editavel}
                    aoMudar={(v) => aplicar(alvo, { ...areaSelecionada, y: v })}
                  />
                  <Numero
                    rotulo="Largura"
                    valor={areaSelecionada.largura}
                    editavel={editavel}
                    aoMudar={(v) => aplicar(alvo, { ...areaSelecionada, largura: v })}
                  />
                  <Numero
                    rotulo={alvo === 'qr' ? 'Lado' : 'Altura'}
                    valor={areaSelecionada.altura}
                    editavel={editavel}
                    aoMudar={(v) => aplicar(alvo, { ...areaSelecionada, altura: v })}
                  />
                </div>

                {alvo !== 'qr' && (
                  <div className="campo">
                    <label htmlFor="alinhamento">Alinhamento</label>
                    <select
                      id="alinhamento"
                      disabled={!editavel}
                      value={
                        (alvo === 'nome'
                          ? areaNome?.alinhamento
                          : alvo === 'unidade'
                            ? areaUnidade?.alinhamento
                            : areaCodigo?.alinhamento) ?? 'CENTRO'
                      }
                      onChange={(evento) =>
                        definirAlinhamento(evento.target.value as Alinhamento)
                      }
                    >
                      <option value="ESQUERDA">À esquerda</option>
                      <option value="CENTRO">Centralizado</option>
                      <option value="DIREITA">A direita</option>
                    </select>
                  </div>
                )}
              </>
            )}

            {editavel && areaCodigo?.qr && (
              <button type="button" className="botao botao-texto botao-pequeno" onClick={alternarQr}>
                Remover o QR
              </button>
            )}

            <p className="editor-nota">
              Arte de {dimensoes?.largura} × {dimensoes?.altura} px. A fonte é institucional, a cor
              é preta e o tamanho se ajusta sozinho para caber na caixa — por isso vale desenhar a
              caixa com a folga que o texto pode ocupar.
            </p>

            {editavel && (
              <div className="campo">
                <label htmlFor="trocar-arte">Trocar a arte</label>
                <input
                  id="trocar-arte"
                  type="file"
                  accept="image/png,image/jpeg"
                  onChange={(evento) => {
                    const escolhido = evento.target.files?.[0]
                    if (escolhido) receberArquivo(escolhido)
                  }}
                />
              </div>
            )}
          </aside>
        </div>
      )}
    </Modal>
  )
}

function Numero({
  rotulo,
  valor,
  editavel,
  aoMudar,
}: {
  rotulo: string
  valor: number
  editavel: boolean
  aoMudar: (valor: number) => void
}) {
  const id = useId()
  return (
    <div className="campo">
      <label htmlFor={id}>{rotulo}</label>
      <input
        id={id}
        type="number"
        value={valor}
        readOnly={!editavel}
        onChange={(evento) => aoMudar(Number(evento.target.value) || 0)}
      />
    </div>
  )
}

function caixa(x: number, y: number, largura: number, altura: number): AreaTexto {
  return {
    x: Math.round(x),
    y: Math.round(y),
    largura: Math.round(largura),
    altura: Math.round(altura),
    alinhamento: 'CENTRO',
  }
}

function limitar(valor: number, minimo: number, maximo: number): number {
  return Math.max(minimo, Math.min(maximo, valor))
}
