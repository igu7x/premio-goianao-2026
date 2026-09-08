import { vi } from 'vitest'

type Rota = string | RegExp

interface Resposta {
  status?: number
  corpo?: unknown
}

/**
 * Substitui o `fetch` global por um roteador simples de URL para resposta.
 *
 * Os componentes chamam a API pelo cliente real (`api/cliente.ts`), então o
 * teste exercita também o cabeçalho de autorização e o tratamento de erro —
 * só o transporte é trocado.
 */
export function instalarApiFalsa(rotas: Array<[Rota, Resposta]>) {
  const chamadas: Array<{ url: string; metodo: string; corpo: string | null }> = []

  vi.stubGlobal(
    'fetch',
    vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof entrada === 'string' ? entrada : String(entrada)
      chamadas.push({
        url,
        metodo: init?.method ?? 'GET',
        corpo: typeof init?.body === 'string' ? init.body : null,
      })

      const rota = rotas.find(([alvo]) =>
        typeof alvo === 'string' ? url.startsWith(alvo) : alvo.test(url),
      )
      if (!rota) {
        return new Response(JSON.stringify({ mensagem: `Rota não mapeada: ${url}` }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        })
      }

      const { status = 200, corpo = null } = rota[1]
      return new Response(JSON.stringify(corpo), {
        status,
        headers: { 'Content-Type': 'application/json' },
      })
    }),
  )

  return chamadas
}
