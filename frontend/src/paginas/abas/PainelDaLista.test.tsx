import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { ListaHabilitados } from '../../api/tipos'
import { ProvedorDeAvisos } from '../../componentes/Avisos'
import { ProvedorDeSessao } from '../../sessao/SessaoContexto'
import { instalarApiFalsa } from '../../teste/api-falsa'
import { PainelDaLista } from './PainelDaLista'

const LISTA: ListaHabilitados = {
  edicaoId: 1,
  edicaoAno: 2026,
  unidadeId: 9,
  unidadeNome: 'CENTRAL DE INTIMAÇÃO REMOTA',
  podeEditar: true,
  podeSemear: true,
  servidores: [],
}

function renderizar() {
  render(
    <MemoryRouter>
      <ProvedorDeSessao>
        <ProvedorDeAvisos>
          <PainelDaLista
            caminho="/api/edicoes/1/unidades/9/servidores"
            carregarLista={async () => LISTA}
            aoFechar={() => {}}
          />
        </ProvedorDeAvisos>
      </ProvedorDeSessao>
    </MemoryRouter>,
  )
}

describe('Inclusão de servidor na lista de habilitados', () => {
  it('escolhe quem já está no sistema, em vez de digitar o e-mail', async () => {
    const chamadas = instalarApiFalsa([
      [
        '/api/pessoas?termo=',
        {
          corpo: {
            pessoas: [
              {
                origem: 'SISTEMA',
                matricula: null,
                nome: 'Marcos Vinícius de Paula',
                email: 'marcos.paula@tjgo.example',
                cpfMascarado: null,
                temEmail: true,
              },
            ],
            rhRespondeu: true,
          },
        },
      ],
      ['/api/edicoes/1/unidades/9/servidores', { corpo: {} }],
      ['/auth/me', { status: 401, corpo: {} }],
    ])

    renderizar()

    await userEvent.type(await screen.findByLabelText(/incluir servidor/i), 'marcos')
    await userEvent.click(await screen.findByRole('button', { name: /marcos vinícius/i }))

    // O e-mail vai como veio do cadastro: uma letra digitada a mais criaria um
    // habilitado que nunca conseguiria emitir.
    const inclusao = chamadas.find((c) => c.metodo === 'POST')
    expect(inclusao?.corpo).toContain('marcos.paula@tjgo.example')
  })

  it('quando não encontra ninguém, manda cadastrar em vez de aceitar o texto', async () => {
    instalarApiFalsa([
      ['/api/pessoas?termo=', { corpo: { pessoas: [], rhRespondeu: true } }],
      ['/auth/me', { status: 401, corpo: {} }],
    ])

    renderizar()

    await userEvent.type(await screen.findByLabelText(/incluir servidor/i), 'fulano')

    expect(await screen.findByText(/ninguém encontrado para/i)).toBeInTheDocument()
  })
})
