import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { Unidade } from '../api/tipos'
import { ProvedorDeAvisos } from '../componentes/Avisos'
import { instalarApiFalsa } from '../teste/api-falsa'
import { Unidades } from './Unidades'

const COM_RESPONSAVEL: Unidade = {
  id: 1,
  nome: 'PRESIDENCIA',
  ativo: true,
  codigoSiedos: 600000009,
  comarca: null,
  responsavel: { id: 9, nome: 'Igor Freitas', email: 'ifccteixeira@tjgo.jus.br' },
}

const SEM_RESPONSAVEL: Unidade = {
  id: 2,
  nome: 'SECRETARIA GERAL DA PRESIDENCIA',
  ativo: true,
  codigoSiedos: 600000010,
  comarca: null,
  responsavel: null,
}

const RELATORIO = {
  linhasLidas: 3,
  designados: 2,
  usuariosCriados: 1,
  papelConcedido: 0,
  substituidos: 0,
  jaEram: 0,
  reconhecimentos: 2,
  edicaoAno: 2026,
  erros: [{ linha: 3, conteudo: 'Fulano;errado;9999;ouro', motivo: 'Unidade não está cadastrada.' }],
}

function renderizar() {
  render(
    <MemoryRouter>
      <ProvedorDeAvisos>
        <Unidades />
      </ProvedorDeAvisos>
    </MemoryRouter>,
  )
}

describe('Planilha de magistrados responsáveis', () => {
  it('sobe o CSV e mostra as linhas que não entraram', async () => {
    const chamadas = instalarApiFalsa([
      ['/api/unidades/responsaveis/importar', { corpo: RELATORIO }],
      ['/api/unidades', { corpo: [COM_RESPONSAVEL, SEM_RESPONSAVEL] }],
    ])

    renderizar()
    await screen.findByText('PRESIDENCIA')

    const csv = new File(
      ['nome;email;unidade;selo\nIgor;ifcc@tjgo.jus.br;600000009;diamante\n'],
      'responsaveis.csv',
      { type: 'text/csv' },
    )
    await userEvent.upload(document.querySelector<HTMLInputElement>('#planilha-responsaveis')!, csv)

    // O relatório é o que sobra do envio: é nele que a linha ruim aparece.
    await screen.findByText(/planilha importada/i)
    expect(screen.getByText(/unidade não está cadastrada/i)).toBeInTheDocument()
    expect(screen.getByText('Fulano;errado;9999;ouro')).toBeInTheDocument()

    const envio = chamadas.find((c) => c.url.includes('responsaveis/importar'))
    expect(envio?.metodo).toBe('POST')
  })

  it('o modelo de teste usa os códigos reais das unidades da tela', async () => {
    instalarApiFalsa([['/api/unidades', { corpo: [COM_RESPONSAVEL, SEM_RESPONSAVEL] }]])

    // O download é interceptado para conferir o conteúdo gerado.
    let gerado = ''
    const blobOriginal = globalThis.Blob
    vi.stubGlobal(
      'Blob',
      class extends blobOriginal {
        constructor(partes: BlobPart[], opcoes?: BlobPropertyBag) {
          gerado = String(partes[0])
          super(partes, opcoes)
        }
      },
    )
    vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:x', revokeObjectURL: () => {} })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    renderizar()
    await screen.findByText('PRESIDENCIA')
    await userEvent.click(screen.getByRole('button', { name: /baixar modelo de teste/i }))

    await waitFor(() => expect(gerado).toContain('nome;email;unidade;selo'))
    expect(gerado).toContain(';600000009;')
    expect(gerado).toContain(';600000010;')
    // Nomes fictícios, no domínio reservado: ninguém confunde com a lista real.
    expect(gerado).toContain('@tjgo.example')
    vi.unstubAllGlobals()
  })

  it('a unidade leva para a página dela', async () => {
    instalarApiFalsa([['/api/unidades', { corpo: [COM_RESPONSAVEL] }]])

    renderizar()

    const link = await screen.findByRole('link', { name: /presidencia/i })
    expect(link).toHaveAttribute('href', '/unidades/1')
  })
})
