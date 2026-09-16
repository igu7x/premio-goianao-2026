import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
  podeSemear: false,
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
    // Sem a permissão vinda do backend, o botão de semear não aparece.
    expect(screen.queryByRole('button', { name: /semear do egesp/i })).not.toBeInTheDocument()
  })

  it('deixa o responsável semear a lista da unidade pelo EGESP', async () => {
    const chamadas = instalarApiFalsa([
      [
        /\/servidores\/semear$/,
        {
          corpo: {
            retornadosPeloEgesp: 3,
            incluidos: 3,
            jaExistentes: 0,
            preservadosRemovidos: 0,
            ignoradosSemEmail: 0,
            totalAtivos: 3,
          },
        },
      ],
      ['/api/magistrado/servidores', { corpo: [{ ...LISTA, podeSemear: true }] }],
    ])

    renderizar()

    await userEvent.click(await screen.findByRole('button', { name: /semear do egesp/i }))

    await screen.findByText(/3 incluído\(s\)/i)
    expect(
      chamadas.some(
        (c) => c.metodo === 'POST' && c.url.endsWith('/api/edicoes/1/unidades/9/servidores/semear'),
      ),
    ).toBe(true)
  })
})
