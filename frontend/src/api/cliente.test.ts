import { afterEach, describe, expect, it, vi } from 'vitest'
import { urlDaApi } from './cliente'

/**
 * A base da API é resolvida em tempo de execução, a partir do `config.js` que o
 * container escreve na subida. Estes testes cobrem o caminho que já custou um
 * deploy: página servida em HTTPS com a variável de ambiente apontando para
 * `http://`, que o navegador bloqueia como conteúdo misto — e o erro que chega
 * ao usuário é só "Failed to fetch".
 */
describe('base da API', () => {
  function configurar(base: string | undefined, protocolo: 'http:' | 'https:') {
    window.__GOIANAO_CONFIG__ = base === undefined ? undefined : { apiBaseUrl: base }
    Object.defineProperty(window, 'location', {
      value: { ...window.location, protocol: protocolo },
      writable: true,
    })
  }

  afterEach(() => {
    window.__GOIANAO_CONFIG__ = undefined
    vi.restoreAllMocks()
  })

  it('sem configuração, usa caminho relativo — a mesma origem', () => {
    configurar(undefined, 'http:')
    expect(urlDaApi('/api/auth/me')).toBe('/api/auth/me')
  })

  it('descarta a barra final para não gerar // no meio da URL', () => {
    configurar('https://api.exemplo/', 'https:')
    expect(urlDaApi('/api/auth/me')).toBe('https://api.exemplo/api/auth/me')
  })

  it('promove http para https quando a página está em https', () => {
    const aviso = vi.spyOn(console, 'warn').mockImplementation(() => {})
    configurar('http://api.exemplo', 'https:')

    expect(urlDaApi('/api/auth/me')).toBe('https://api.exemplo/api/auth/me')
    expect(aviso).toHaveBeenCalled()
  })

  it('deixa http intacto quando a própria página está em http', () => {
    configurar('http://api.exemplo', 'http:')
    expect(urlDaApi('/api/auth/me')).toBe('http://api.exemplo/api/auth/me')
  })

  it('não mexe em caminho que já é absoluto', () => {
    configurar('https://api.exemplo', 'https:')
    expect(urlDaApi('https://outro/destino')).toBe('https://outro/destino')
  })
})
