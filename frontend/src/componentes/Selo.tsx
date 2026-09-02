import type { Selo } from '../api/tipos'

const ROTULO: Record<Selo, string> = {
  BRONZE: 'Bronze',
  PRATA: 'Prata',
  OURO: 'Ouro',
  DIAMANTE: 'Diamante',
}

export function rotuloDoSelo(selo: Selo): string {
  return ROTULO[selo]
}

/**
 * O disco metálico — elemento de identidade do produto.
 *
 * O sistema entrega medalhas, e durante muito tempo o selo apareceu na
 * interface como um pontinho colorido: a coisa mais importante representada
 * pelo elemento mais banal. Aqui ele é cunhado de verdade — liga em degradê,
 * anel gravado, sombra interna — e a mesma peça serve à lista, ao cartão e à
 * conferência pública, mudando só de tamanho.
 */
export function Disco({
  selo,
  tamanho = 'm',
}: {
  selo: Selo
  tamanho?: 'p' | 'm' | 'g'
}) {
  return (
    <span
      className={`disco disco-${selo.toLowerCase()} disco-${tamanho}`}
      aria-hidden="true"
    >
      <span className="disco-inicial">{ROTULO[selo].charAt(0)}</span>
    </span>
  )
}

/** Disco mais o nome da liga — a forma usada em listas e tabelas. */
export function EtiquetaSelo({
  selo,
  tamanho = 'p',
}: {
  selo: Selo
  tamanho?: 'p' | 'm' | 'g'
}) {
  return (
    <span className={`selo selo-${selo.toLowerCase()}`}>
      <Disco selo={selo} tamanho={tamanho} />
      {ROTULO[selo]}
    </span>
  )
}
