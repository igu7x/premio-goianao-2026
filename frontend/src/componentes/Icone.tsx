/**
 * Conjunto de icones proprio, em traco de 1.5px sobre grade de 24px.
 *
 * Sao poucos e desenhados a mao para manter uma linguagem unica — e para nao
 * arrastar uma biblioteca de milhares de simbolos por causa de uma dezena.
 */

export type NomeDeIcone =
  | 'painel'
  | 'edicoes'
  | 'certificado'
  | 'equipe'
  | 'verificar'
  | 'sair'
  | 'mais'
  | 'fechar'
  | 'baixar'
  | 'enviar'
  | 'editar'
  | 'remover'
  | 'busca'
  | 'atencao'
  | 'confirmado'
  | 'seta'
  | 'olho'
  | 'planilha'
  | 'semear'
  | 'voltar'
  | 'baixo'
  | 'trocar'

interface Props {
  nome: NomeDeIcone
  tamanho?: number
  className?: string
}

const CAMINHOS: Record<NomeDeIcone, JSX.Element> = {
  painel: (
    <>
      <rect x="3" y="3" width="7.5" height="7.5" rx="1" />
      <rect x="13.5" y="3" width="7.5" height="7.5" rx="1" />
      <rect x="3" y="13.5" width="7.5" height="7.5" rx="1" />
      <rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1" />
    </>
  ),
  edicoes: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </>
  ),
  certificado: (
    <>
      <circle cx="12" cy="9" r="5.5" />
      <path d="M8.5 13.8 7 21l5-2.4L17 21l-1.5-7.2" />
    </>
  ),
  equipe: (
    <>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 20c0-3.1 2.5-5.2 5.5-5.2s5.5 2.1 5.5 5.2" />
      <path d="M16.5 5.4a3.2 3.2 0 0 1 0 6.2M17.5 14.9c1.9.6 3.2 2.4 3.2 5.1" />
    </>
  ),
  verificar: (
    <>
      <path d="M12 3 5 6v5.5c0 4 2.8 7.7 7 9.5 4.2-1.8 7-5.5 7-9.5V6l-7-3Z" />
      <path d="m9 12 2.2 2.2L15.5 10" />
    </>
  ),
  sair: (
    <>
      <path d="M14 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8" />
      <path d="m16 8 4 4-4 4M20 12H9" />
    </>
  ),
  mais: <path d="M12 5v14M5 12h14" />,
  fechar: <path d="m6 6 12 12M18 6 6 18" />,
  baixar: (
    <>
      <path d="M12 3v12" />
      <path d="m7.5 10.5 4.5 4.5 4.5-4.5" />
      <path d="M4 18v1.5A1.5 1.5 0 0 0 5.5 21h13a1.5 1.5 0 0 0 1.5-1.5V18" />
    </>
  ),
  enviar: (
    <>
      <path d="M12 21V9" />
      <path d="m7.5 13.5 4.5-4.5 4.5 4.5" />
      <path d="M4 6V4.5A1.5 1.5 0 0 1 5.5 3h13A1.5 1.5 0 0 1 20 4.5V6" />
    </>
  ),
  editar: (
    <>
      <path d="M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3Z" />
      <path d="m14.5 7.5 2 2" />
    </>
  ),
  remover: (
    <>
      <path d="M4 7h16M10 7V5h4v2M6 7l1 13h10l1-13" />
    </>
  ),
  busca: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </>
  ),
  atencao: (
    <>
      <path d="M12 4 2.8 20h18.4L12 4Z" />
      <path d="M12 10v4.5M12 17.4v.1" />
    </>
  ),
  confirmado: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12.3 2.6 2.6L16 9.5" />
    </>
  ),
  seta: <path d="m9 5 7 7-7 7" />,
  olho: (
    <>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="2.8" />
    </>
  ),
  planilha: (
    <>
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <path d="M4 9h16M4 15h16M10 3v18" />
    </>
  ),
  voltar: <path d="M15 19 8 12l7-7" />,
  baixo: <path d="m6 9 6 6 6-6" />,
  trocar: (
    <>
      <path d="M7 4v13M4.5 14 7 17l2.5-3" />
      <path d="M17 20V7M14.5 10 17 7l2.5 3" />
    </>
  ),
  semear: (
    <>
      <path d="M12 20V9" />
      <path d="M12 12c0-3.3 2.7-6 6-6 0 3.3-2.7 6-6 6Z" />
      <path d="M12 15c0-2.5-2-4.5-4.5-4.5 0 2.5 2 4.5 4.5 4.5Z" />
    </>
  ),
}

export function Icone({ nome, tamanho = 18, className }: Props) {
  return (
    <svg
      className={className}
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {CAMINHOS[nome]}
    </svg>
  )
}
