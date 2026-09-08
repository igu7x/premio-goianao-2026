/**
 * Cliente HTTP da aplicacao.
 *
 * Concentra tres coisas que nao devem ficar espalhadas pelas telas: o cabecalho
 * de autorizacao, a traducao do corpo de erro da API para uma excecao util e o
 * tratamento de 401 (sessao expirada), que derruba a sessao em qualquer ponto
 * do app — o token vale 8h e nao ha refresh.
 */

const CHAVE_TOKEN = 'goianao.sessao'
export const EVENTO_SESSAO_EXPIRADA = 'goianao:sessao-expirada'

declare global {
  interface Window {
    __GOIANAO_CONFIG__?: { apiBaseUrl?: string }
  }
}

/**
 * Base da API.
 *
 * Em desenvolvimento fica vazia: os caminhos saem relativos e o proxy do Vite
 * encaminha `/api` para o backend. Em producao, frontend e API sao dois apps
 * com rotas distintas no OpenShift, entao a base precisa apontar para fora.
 *
 * O valor vem de `public/config.js`, servido ao lado do index.html e reescrito
 * pelo container na subida. Fosse por `import.meta.env`, o Vite o congelaria no
 * build e seria preciso uma imagem por ambiente.
 */
function baseDaApi(): string {
  return (window.__GOIANAO_CONFIG__?.apiBaseUrl ?? '').replace(/\/+$/, '')
}

/** Resolve um caminho da API contra a base configurada. */
export function urlDaApi(caminho: string): string {
  return caminho.startsWith('/') ? baseDaApi() + caminho : caminho
}

export class ErroApi extends Error {
  readonly status: number
  readonly detalhes: string[]

  constructor(status: number, mensagem: string, detalhes: string[] = []) {
    super(mensagem)
    this.name = 'ErroApi'
    this.status = status
    this.detalhes = detalhes
  }
}

export function lerToken(): string | null {
  try {
    return localStorage.getItem(CHAVE_TOKEN)
  } catch {
    return null
  }
}

export function guardarToken(token: string): void {
  try {
    localStorage.setItem(CHAVE_TOKEN, token)
  } catch {
    /* navegador sem storage: a sessao vale so enquanto a aba viver */
  }
}

export function descartarToken(): void {
  try {
    localStorage.removeItem(CHAVE_TOKEN)
  } catch {
    /* nada a fazer */
  }
}

function cabecalhos(extra?: HeadersInit): Headers {
  const headers = new Headers(extra)
  const token = lerToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return headers
}

async function tratar(resposta: Response): Promise<Response> {
  if (resposta.ok) {
    return resposta
  }

  if (resposta.status === 401) {
    descartarToken()
    window.dispatchEvent(new CustomEvent(EVENTO_SESSAO_EXPIRADA))
    throw new ErroApi(401, 'Sua sessão expirou. Entre novamente.')
  }

  let mensagem = 'Não foi possível completar a operação.'
  let detalhes: string[] = []
  try {
    const corpo = await resposta.json()
    if (corpo?.mensagem) {
      mensagem = corpo.mensagem
    }
    if (Array.isArray(corpo?.detalhes)) {
      detalhes = corpo.detalhes
    }
  } catch {
    /* resposta sem corpo JSON: fica a mensagem padrao */
  }
  throw new ErroApi(resposta.status, mensagem, detalhes)
}

async function json<T>(resposta: Response): Promise<T> {
  if (resposta.status === 204) {
    return undefined as T
  }
  return (await resposta.json()) as T
}

export const api = {
  async get<T>(caminho: string): Promise<T> {
    return json<T>(await tratar(await fetch(urlDaApi(caminho), { headers: cabecalhos() })))
  },

  async post<T>(caminho: string, corpo?: unknown): Promise<T> {
    const resposta = await fetch(urlDaApi(caminho), {
      method: 'POST',
      headers: cabecalhos(corpo === undefined ? {} : { 'Content-Type': 'application/json' }),
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    })
    return json<T>(await tratar(resposta))
  },

  async put<T>(caminho: string, corpo: unknown): Promise<T> {
    const resposta = await fetch(urlDaApi(caminho), {
      method: 'PUT',
      headers: cabecalhos({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(corpo),
    })
    return json<T>(await tratar(resposta))
  },

  async remover(caminho: string): Promise<void> {
    await tratar(await fetch(urlDaApi(caminho), { method: 'DELETE', headers: cabecalhos() }))
  },

  /** Envio multipart (arte do certificado, planilha de reconhecidos). */
  async enviarArquivo<T>(caminho: string, dados: FormData, metodo = 'POST'): Promise<T> {
    // Sem Content-Type manual: o navegador precisa definir o boundary.
    const resposta = await fetch(urlDaApi(caminho), { method: metodo, headers: cabecalhos(), body: dados })
    return json<T>(await tratar(resposta))
  },

  /**
   * Baixa um PDF. Devolve tambem o codigo de validacao que o backend envia no
   * cabecalho, para a tela poder exibi-lo logo apos a emissao.
   */
  async baixarPdf(
    caminho: string,
    corpo?: unknown,
  ): Promise<{ blob: Blob; nomeArquivo: string; codigo: string | null }> {
    const resposta = await tratar(
      await fetch(urlDaApi(caminho), {
        method: 'POST',
        headers: cabecalhos(corpo === undefined ? {} : { 'Content-Type': 'application/json' }),
        body: corpo === undefined ? undefined : JSON.stringify(corpo),
      }),
    )

    return {
      blob: await resposta.blob(),
      nomeArquivo: nomeDoAnexo(resposta.headers.get('Content-Disposition')),
      codigo: resposta.headers.get('X-Codigo-Validacao'),
    }
  },
}

function nomeDoAnexo(cabecalho: string | null): string {
  const encontrado = cabecalho?.match(/filename="?([^";]+)"?/i)
  return encontrado ? encontrado[1] : 'certificado.pdf'
}

/** Dispara o download no navegador e libera a URL temporaria em seguida. */
export function salvarArquivo(blob: Blob, nomeArquivo: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = nomeArquivo
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Revogar no mesmo instante cancela o download em alguns navegadores: eles
  // ainda estao lendo o blob quando a URL deixa de existir.
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

/** Abre o PDF em outra aba (usado na pre-visualizacao do layout). */
export function abrirEmNovaAba(blob: Blob): void {
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener')
  // A aba precisa carregar antes de a URL ser invalidada.
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
