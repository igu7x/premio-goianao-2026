import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { Edicao, Unidade } from '../../api/tipos'
import { ProvedorDeAvisos } from '../../componentes/Avisos'
import { instalarApiFalsa } from '../../teste/api-falsa'
import { AbaUnidades } from './AbaUnidades'

const COM_RESPONSAVEL: Unidade = {
  id: 1,
  nome: 'PRESIDENCIA',
  ativo: true,
  codigoSiedos: 600000009,
  comarca: null,
  responsavel: { id: 9, nome: 'Igor Freitas', email: 'ifccteixeira@tjgo.jus.br' },
  habilitados: 38,
}

const SEM_RESPONSAVEL: Unidade = {
  id: 2,
  nome: 'SECRETARIA GERAL DA PRESIDENCIA',
  ativo: true,
  codigoSiedos: 600000010,
  comarca: null,
  responsavel: null,
  habilitados: 0,
}

const RELATORIO = {
  linhasLidas: 3,
  designados: 2,
  usuariosCriados: 1,
  papelConcedido: 0,
  substituidos: 0,
  jaEram: 0,
  reconhecimentos: 2,
  listasSemeadas: 2,
  habilitados: 47,
  edicaoAno: 2026,
  erros: [{ linha: 3, conteudo: 'Fulano;errado;9999;ouro', motivo: 'Unidade não está cadastrada.' }],
}

const EDICAO: Edicao = {
  id: 7,
  ano: 2026,
  descricao: null,
  status: 'PUBLICADA',
  vigente: true,
  emitivel: true,
  aceitaInclusoes: true,
  criadoEm: '2026-01-10T09:00:00',
  atualizadoEm: null,
}

function renderizar() {
  render(
    <MemoryRouter>
      <ProvedorDeAvisos>
        <AbaUnidades edicao={EDICAO} />
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

    // O relatório também diz quantas listas a planilha semeou do RH.
    expect(screen.getByText(/2 lista\(s\) de servidores semeada\(s\)/i)).toBeInTheDocument()

    const envio = chamadas.find((c) => c.url.includes('responsaveis/importar'))
    expect(envio?.metodo).toBe('POST')
    // Os selos e a semeadura são da edição aberta, não da vigente por suposição.
    expect(envio?.url).toContain('edicaoId=7')
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

  it('designar semeia a lista da unidade e conta quem entrou', async () => {
    const chamadas = instalarApiFalsa([
      [
        '/api/usuarios',
        {
          corpo: [
            {
              id: 9,
              nome: 'Igor Freitas',
              email: 'ifccteixeira@tjgo.jus.br',
              ativo: true,
              papeis: ['MAGISTRADO'],
            },
          ],
        },
      ],
      [
        /\/responsavel/,
        {
          corpo: {
            unidade: { ...SEM_RESPONSAVEL, responsavel: COM_RESPONSAVEL.responsavel },
            semeadura: {
              retornadosPeloEgesp: 12,
              incluidos: 12,
              jaExistentes: 0,
              preservadosRemovidos: 0,
              ignoradosSemEmail: 0,
              totalAtivos: 12,
            },
            aviso: null,
          },
        },
      ],
      ['/api/unidades', { corpo: [SEM_RESPONSAVEL] }],
    ])

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: /designar/i }))
    await userEvent.selectOptions(
      await screen.findByRole('combobox', { name: /magistrado/i }),
      '9',
    )
    // O da linha e o do modal têm o mesmo rótulo; o do modal é o segundo.
    const [, confirmar] = screen.getAllByRole('button', { name: /^designar$/i })
    await userEvent.click(confirmar)

    // O aviso é a prova de que a equipe veio junto com a designação.
    await screen.findByText(/12 incluído\(s\)/i)
    const designacao = chamadas.find((c) => c.url.includes('/responsavel'))
    expect(designacao?.metodo).toBe('PUT')
    expect(designacao?.url).toContain('edicaoId=7')
  })

  it('a unidade leva para a página dela', async () => {
    instalarApiFalsa([['/api/unidades', { corpo: [COM_RESPONSAVEL] }]])

    renderizar()

    const link = await screen.findByRole('link', { name: /presidencia/i })
    expect(link).toHaveAttribute('href', '/unidades/1')
  })
})
