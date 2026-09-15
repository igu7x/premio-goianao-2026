import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ListaHabilitados } from '../api/tipos'
import { ProvedorDeAvisos } from '../componentes/Avisos'
import { instalarApiFalsa } from '../teste/api-falsa'
import { MinhasUnidades } from './MinhasUnidades'

const LISTA: ListaHabilitados = {
  edicaoId: 1,
  edicaoAno: 2026,
  unidadeId: 9,
  unidadeNome: '1ª Vara Cível da Comarca de Goiânia',
  podeEditar: true,
  ehAdministrador: false,
  servidores: [],
}

function renderizar() {
  render(
    <ProvedorDeAvisos>
      <MinhasUnidades />
    </ProvedorDeAvisos>,
  )
}

describe('Minhas unidades', () => {
  it('explica a falta de edição vigente em vez de girar para sempre', async () => {
    instalarApiFalsa([
      [
        '/api/magistrado/servidores',
        { status: 404, corpo: { mensagem: 'Nenhuma edição vigente definida. Selecione uma edição.' } },
      ],
    ])

    renderizar()

    await screen.findByText(/nenhuma edição vigente definida/i)
    // O estado é do prêmio, não da tela: nem tarja de erro, nem carregando.
    expect(screen.queryByText(/carregando/i)).not.toBeInTheDocument()
  })

  it('para de carregar quando a chamada falha de verdade', async () => {
    instalarApiFalsa([
      ['/api/magistrado/servidores', { status: 500, corpo: { mensagem: 'Erro interno.' } }],
    ])

    renderizar()

    await screen.findByText(/erro interno/i)
    expect(screen.queryByText(/carregando/i)).not.toBeInTheDocument()
  })

  it('mostra um cartão por unidade quando há edição vigente', async () => {
    instalarApiFalsa([['/api/magistrado/servidores', { corpo: [LISTA] }]])

    renderizar()

    await screen.findByText('1ª Vara Cível da Comarca de Goiânia')
    expect(screen.getByRole('button', { name: /gerenciar lista/i })).toBeInTheDocument()
  })
})
