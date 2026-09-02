import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi, lerToken } from '../../api/cliente'
import type { Edicao, Layout, LayoutsDaEdicao, Selo, TipoCertificado } from '../../api/tipos'
import { Aviso, Carregando } from '../../componentes/Basicos'
import { Icone } from '../../componentes/Icone'
import { Disco, rotuloDoSelo } from '../../componentes/Selo'
import { EditorDeLayout } from './EditorDeLayout'

const SELOS: Selo[] = ['BRONZE', 'PRATA', 'OURO', 'DIAMANTE']
const TIPOS: TipoCertificado[] = ['MAGISTRADO', 'SERVIDOR']

/**
 * As oito combinacoes selo x tipo de uma edicao (feature 003).
 *
 * A grade mostra sempre as oito, configuradas ou nao — e a forma mais direta de
 * o administrador ver quanto falta para poder publicar.
 */
export function AbaLayouts({ edicao }: { edicao: Edicao }) {
  const [dados, setDados] = useState<LayoutsDaEdicao | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [editando, setEditando] = useState<{
    selo: Selo
    tipo: TipoCertificado
    layout: Layout | null
  } | null>(null)

  const carregar = useCallback(async () => {
    try {
      setDados(await api.get<LayoutsDaEdicao>(`/api/edicoes/${edicao.id}/layouts`))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar os layouts.')
    }
  }, [edicao.id])

  useEffect(() => {
    void carregar()
  }, [carregar])

  if (erro) {
    return <Aviso tom="erro">{erro}</Aviso>
  }
  if (!dados) {
    return <Carregando />
  }

  const porCombinacao = new Map(dados.layouts.map((l) => [`${l.selo}-${l.tipo}`, l]))

  return (
    <>
      {dados.pendencias.length > 0 && (
        <div style={{ marginBottom: 'var(--e5)' }}>
          <Aviso
            tom={edicao.status === 'RASCUNHO' ? 'atencao' : 'erro'}
            titulo={`Faltam ${dados.pendencias.length} de 8 combinações`}
            detalhes={dados.pendencias}
          >
            <p>A edição só pode ser publicada com todas configuradas.</p>
          </Aviso>
        </div>
      )}

      {!dados.editavel && (
        <div style={{ marginBottom: 'var(--e5)' }}>
          <Aviso tom="informacao">
            Os layouts desta edição estão travados desde a publicação. É o que garante que uma
            reemissão feita daqui a anos saia idêntica à original.
          </Aviso>
        </div>
      )}

      {!dados.fonteInstitucionalDisponivel && (
        <div style={{ marginBottom: 'var(--e5)' }}>
          <Aviso tom="informacao">
            A fonte institucional do TJGO ainda não foi instalada; os certificados usam a fonte
            padrão do PDF. Para trocar, coloque o arquivo em{' '}
            <code className="mono">backend/src/main/resources/fontes/institucional.ttf</code>.
          </Aviso>
        </div>
      )}

      {TIPOS.map((tipo) => (
        <section key={tipo} style={{ marginBottom: 'var(--e6)' }}>
          <header
            style={{
              display: 'flex',
              alignItems: 'baseline',
              justifyContent: 'space-between',
              gap: 'var(--e4)',
              marginBottom: 'var(--e4)',
            }}
          >
            <h2 className="titulo-secao">
              Certificado de {tipo === 'MAGISTRADO' ? 'magistrado' : 'servidor'}
            </h2>
            <span className="rotulo">
              {SELOS.filter((s) => porCombinacao.has(`${s}-${tipo}`)).length} de 4 configurados
            </span>
          </header>
          <div className="grade grade-4 escalonar">
            {SELOS.map((selo) => {
              const layout = porCombinacao.get(`${selo}-${tipo}`) ?? null
              return (
                <article className="layout-cartao" key={`${selo}-${tipo}`}>
                  <span className="layout-disco">
                    <Disco selo={selo} tamanho="m" />
                  </span>
                  <span
                    className={
                      layout ? 'layout-estado layout-estado-pronto' : 'layout-estado'
                    }
                  >
                    {layout ? 'Configurado' : 'Pendente'}
                  </span>
                  <div className="layout-arte">
                    {layout ? (
                      <ArteDoLayout layout={layout} />
                    ) : (
                      <div className="layout-arte-vazia">
                        <div>
                          <Icone nome="enviar" tamanho={20} />
                          <div style={{ marginTop: 6 }}>Sem arte configurada</div>
                        </div>
                      </div>
                    )}
                  </div>
                  <div className="layout-corpo">
                    <div>
                      <div className={`certificado-liga selo-${selo.toLowerCase()}`}>Selo {rotuloDoSelo(selo)}</div>
                      <div className="layout-combinacao">
                        {tipo === 'MAGISTRADO' ? 'Magistrado' : 'Servidor'}
                      </div>
                    </div>
                    <button
                      type="button"
                      className="botao botao-neutro botao-pequeno"
                      disabled={!dados.editavel && !layout}
                      onClick={() => setEditando({ selo, tipo, layout })}
                    >
                      {layout ? (dados.editavel ? 'Editar' : 'Ver') : 'Configurar'}
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        </section>
      ))}

      {editando && (
        <EditorDeLayout
          edicaoId={edicao.id}
          selo={editando.selo}
          tipo={editando.tipo}
          layout={editando.layout}
          editavel={dados.editavel}
          aoFechar={() => setEditando(null)}
          aoSalvar={async () => {
            setEditando(null)
            await carregar()
          }}
        />
      )}
    </>
  )
}

/**
 * A miniatura da arte vem de um endpoint autenticado, entao nao da para apontar
 * o src direto: o navegador nao envia o cabecalho Authorization em <img>.
 */
function ArteDoLayout({ layout }: { layout: Layout }) {
  const [url, setUrl] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true
    let criada: string | null = null

    fetch(layout.imagemUrl, { headers: { Authorization: `Bearer ${lerToken() ?? ''}` } })
      .then((resposta) => (resposta.ok ? resposta.blob() : Promise.reject(resposta)))
      .then((blob) => {
        if (!ativo) return
        criada = URL.createObjectURL(blob)
        setUrl(criada)
      })
      .catch(() => undefined)

    return () => {
      ativo = false
      if (criada) URL.revokeObjectURL(criada)
    }
  }, [layout.imagemUrl])

  if (!url) {
    return <div className="layout-arte-vazia">Carregando arte…</div>
  }
  return <img src={url} alt={`Arte ${layout.selo} ${layout.tipo}`} />
}
