import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Edicao, Magistrado } from '../../api/tipos'
import { instalarApiFalsa } from '../../teste/api-falsa'
import { AbaMagistrados } from './AbaMagistrados'

const MAGISTRADO: Magistrado = {
  id: 7,
  cpf: '20450670252',
  cpfFormatado: '204.506.702-52',
  nome: 'Rafael Siqueira Bittencourt',
  reconhecimentos: [
    { id: 1, unidadeId: 1, unidadeNome: '1ª Vara Cível da Comarca de Goiânia', selo: 'OURO' },
  ],
}

function edicao(parcial: Partial<Edicao>): Edicao {
  return {
    id: 1,
    ano: 2025,
    descricao: null,
    status: 'RASCUNHO',
    vigente: false,
    emitivel: false,
    aceitaInclusoes: true,
    criadoEm: '2026-01-10T09:00:00',
    atualizadoEm: null,
    ...parcial,
  }
}

describe('Cadastro de reconhecidos na tela (features 004 e 009)', () => {
  it('em rascunho, permite incluir, importar em lote e remover', async () => {
    instalarApiFalsa([['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }]])

    render(<AbaMagistrados edicao={edicao({ status: 'RASCUNHO' })} />)

    await screen.findByText('Rafael Siqueira Bittencourt')
    expect(screen.getByRole('button', { name: /novo magistrado/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /importar planilha/i })).toBeInTheDocument()
    // A remocao so existe em rascunho; e a acao destrutiva da feature 004.
    expect(screen.getByRole('button', { name: '' })).toBeInTheDocument()
  })

  it('na edição vigente, entra em modo somente-inclusão (009/RF-3)', async () => {
    instalarApiFalsa([['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }]])

    render(
      <AbaMagistrados
        edicao={edicao({ status: 'PUBLICADA', vigente: true, aceitaInclusoes: true })}
      />,
    )

    await screen.findByText('Rafael Siqueira Bittencourt')

    expect(screen.getByText(/apenas inclusões/i)).toBeInTheDocument()
    // Continua dando para acrescentar...
    expect(screen.getByRole('button', { name: /novo magistrado/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /adicionar unidade/i })).toBeEnabled()
    // ...mas nao para editar nem remover o que ja existe.
    expect(screen.queryByRole('button', { name: /importar planilha/i })).not.toBeInTheDocument()
    expect(screen.getByRole('table').querySelectorAll('.botao-perigo')).toHaveLength(0)
  })

  it('na edição publicada e não vigente, o cadastro fica congelado', async () => {
    instalarApiFalsa([['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }]])

    render(
      <AbaMagistrados
        edicao={edicao({ status: 'PUBLICADA', vigente: false, aceitaInclusoes: false })}
      />,
    )

    await screen.findByText('Rafael Siqueira Bittencourt')

    expect(screen.getByText(/cadastro está congelado/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /novo magistrado/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /adicionar unidade/i })).toBeDisabled()
  })

  it('a inclusão de unidade usa o endpoint aditivo, e não um PUT', async () => {
    const chamadas = instalarApiFalsa([
      ['/api/unidades/egesp', { corpo: [{ nome: '2ª Vara Cível', comarca: 'Goiânia', unidadeId: null, jaCadastrada: false }] }],
      ['/api/edicoes/1/magistrados/7/reconhecimentos', { status: 201, corpo: MAGISTRADO }],
      ['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }],
    ])

    render(
      <AbaMagistrados
        edicao={edicao({ status: 'PUBLICADA', vigente: true, aceitaInclusoes: true })}
      />,
    )

    await screen.findByText('Rafael Siqueira Bittencourt')
    await userEvent.click(screen.getByRole('button', { name: /adicionar unidade/i }))

    await screen.findByRole('dialog')
    await waitFor(() => expect(screen.getByRole('combobox', { name: /unidade/i })).toBeInTheDocument())
    await userEvent.selectOptions(screen.getByRole('combobox', { name: /unidade/i }), '2ª Vara Cível')
    await userEvent.click(screen.getByRole('button', { name: /^incluir$/i }))

    await waitFor(() => {
      const inclusao = chamadas.find((c) => c.url.includes('/reconhecimentos'))
      expect(inclusao?.metodo).toBe('POST')
      expect(inclusao?.corpo).toContain('2ª Vara Cível')
    })
  })
})
