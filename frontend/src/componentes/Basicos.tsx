import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { StatusEdicao } from '../api/tipos'
import { Icone } from './Icone'

// O selo virou peça própria (disco metálico); reexportado aqui porque as telas
// já o importavam deste módulo.
export { Disco, EtiquetaSelo, rotuloDoSelo } from './Selo'

/* ------------------------------------------------------------------ */
/* Situacao da edicao                                                  */
/* ------------------------------------------------------------------ */

export function SituacaoEdicao({ status, vigente }: { status: StatusEdicao; vigente: boolean }) {
  if (vigente) {
    return <span className="etiqueta etiqueta-vigente">Vigente</span>
  }
  if (status === 'PUBLICADA') {
    return <span className="etiqueta etiqueta-publicada">Publicada</span>
  }
  return <span className="etiqueta etiqueta-rascunho">Rascunho</span>
}

/* ------------------------------------------------------------------ */
/* Avisos                                                              */
/* ------------------------------------------------------------------ */

type TomDeAviso = 'erro' | 'atencao' | 'sucesso' | 'informacao'

export function Aviso({
  tom = 'informacao',
  titulo,
  detalhes,
  children,
}: {
  tom?: TomDeAviso
  titulo?: string
  detalhes?: string[]
  children?: ReactNode
}) {
  return (
    <div className={`aviso aviso-${tom}`} role={tom === 'erro' ? 'alert' : undefined}>
      {tom !== 'informacao' && (
        <Icone nome={tom === 'sucesso' ? 'confirmado' : 'atencao'} tamanho={17} />
      )}
      <div>
        {titulo && <strong>{titulo}</strong>}
        {children}
        {detalhes && detalhes.length > 0 && (
          // Acima de três itens a lista vira colunas: as oito combinações de
          // layout empilhadas transformavam o aviso num paredão amarelo que
          // dominava a tela inteira.
          <ul className={detalhes.length > 3 ? 'aviso-colunas' : undefined}>
            {detalhes.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Estados de tela                                                     */
/* ------------------------------------------------------------------ */

export function Carregando({ texto = 'Carregando…' }: { texto?: string }) {
  return (
    <div className="carregando">
      <span className="giro" />
      {texto}
    </div>
  )
}

/** Esqueleto para o carregamento de blocos com conteúdo tabular. */
export function Esqueleto({ linhas = 3 }: { linhas?: number }) {
  return (
    <div className="esqueleto-bloco" aria-hidden="true">
      {Array.from({ length: linhas }).map((_, i) => (
        <div
          key={i}
          className="barra-esqueleto"
          style={{ width: `${100 - i * 12}%`, animationDelay: `${i * 90}ms` }}
        />
      ))}
    </div>
  )
}

export function EstadoVazio({
  titulo,
  descricao,
  acao,
}: {
  titulo: string
  descricao?: string
  acao?: ReactNode
}) {
  return (
    <div className="vazio">
      <h3>{titulo}</h3>
      {descricao && <p>{descricao}</p>}
      {acao && <div style={{ marginTop: 'var(--e4)' }}>{acao}</div>}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Modal                                                               */
/* ------------------------------------------------------------------ */

export function Modal({
  titulo,
  descricao,
  largo,
  aoFechar,
  rodape,
  children,
}: {
  titulo: string
  descricao?: string
  largo?: boolean
  aoFechar: () => void
  rodape?: ReactNode
  children: ReactNode
}) {
  // Escape fecha, e o fundo para de rolar enquanto o painel está aberto: sem
  // isso a página de trás desliza junto e o modal parece solto.
  useEffect(() => {
    const aoTeclar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') aoFechar()
    }
    document.addEventListener('keydown', aoTeclar)
    const rolagemAnterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', aoTeclar)
      document.body.style.overflow = rolagemAnterior
    }
  }, [aoFechar])

  /*
   * O modal e renderizado no <body>, nao onde o componente esta na arvore.
   *
   * A cortina e position: fixed, e fixed se posiciona em relacao a viewport —
   * exceto quando algum ancestral cria bloco de contencao, o que qualquer
   * transform, filter ou animacao de transform faz. A .pagina anima com
   * translateY na entrada, e isso bastava para o modal passar a se centrar na
   * area de conteudo em vez da janela: aparecia deslocado para a direita e com
   * o cabecalho cortado acima da tela.
   *
   * Pelo portal a cortina fica fora de qualquer ancestral do layout, entao o
   * problema nao volta quando alguem acrescentar uma animacao nova.
   */
  return createPortal(
    <div
      className="cortina"
      role="dialog"
      aria-modal="true"
      aria-label={titulo}
      onMouseDown={(evento) => {
        // Fecha so quando o clique comeca fora do painel: arrastar de dentro
        // para fora (comum no editor de layout) nao pode fechar o modal.
        if (evento.target === evento.currentTarget) {
          aoFechar()
        }
      }}
    >
      <div className={largo ? 'modal modal-largo' : 'modal'}>
        <header className="modal-cabecalho">
          <div>
            <h2 className="titulo-secao">{titulo}</h2>
            {descricao && (
              <p className="apoio" style={{ marginTop: 4 }}>
                {descricao}
              </p>
            )}
          </div>
          <button type="button" className="fechar" onClick={aoFechar} aria-label="Fechar">
            <Icone nome="fechar" tamanho={20} />
          </button>
        </header>
        <div className="modal-corpo">{children}</div>
        {rodape && <footer className="modal-rodape">{rodape}</footer>}
      </div>
    </div>,
    document.body,
  )
}

/* ------------------------------------------------------------------ */
/* Datas                                                               */
/* ------------------------------------------------------------------ */

export function formatarDataHora(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  const data = new Date(iso)
  if (Number.isNaN(data.getTime())) {
    return '—'
  }
  return data.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatarData(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  const data = new Date(iso)
  return Number.isNaN(data.getTime()) ? '—' : data.toLocaleDateString('pt-BR')
}
