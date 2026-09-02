/**
 * Anel de progresso.
 *
 * Serve ao número que só faz sentido como fração — "6 de 8 layouts". Ler
 * "6/8" exige que a pessoa calcule; o anel entrega a proporção de imediato, e o
 * número no centro continua ali para quem quer o valor exato.
 *
 * O arco é desenhado na entrada, então o preenchimento é visto acontecendo em
 * vez de já aparecer pronto.
 */
export function Anel({
  valor,
  total,
  tamanho = 96,
  espessura = 7,
  rotulo,
}: {
  valor: number
  total: number
  tamanho?: number
  espessura?: number
  rotulo?: string
}) {
  const raio = (tamanho - espessura) / 2
  const circunferencia = 2 * Math.PI * raio
  const fracao = total > 0 ? Math.min(1, Math.max(0, valor / total)) : 0
  const completo = fracao >= 1

  return (
    <div className="anel" style={{ width: tamanho, height: tamanho }}>
      <svg width={tamanho} height={tamanho} viewBox={`0 0 ${tamanho} ${tamanho}`}>
        <circle
          cx={tamanho / 2}
          cy={tamanho / 2}
          r={raio}
          fill="none"
          stroke="var(--traco)"
          strokeWidth={espessura}
        />
        <circle
          className="anel-arco"
          cx={tamanho / 2}
          cy={tamanho / 2}
          r={raio}
          fill="none"
          stroke={completo ? 'var(--verde-3)' : 'var(--ouro)'}
          strokeWidth={espessura}
          strokeLinecap="round"
          strokeDasharray={circunferencia}
          strokeDashoffset={circunferencia * (1 - fracao)}
          transform={`rotate(-90 ${tamanho / 2} ${tamanho / 2})`}
          style={{ ['--circunferencia' as string]: `${circunferencia}` }}
        />
      </svg>

      <div className="anel-centro">
        <span className="anel-valor">
          {valor}
          <em>/{total}</em>
        </span>
        {rotulo && <span className="anel-rotulo">{rotulo}</span>}
      </div>
    </div>
  )
}
