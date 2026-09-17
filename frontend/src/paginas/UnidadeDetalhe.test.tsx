import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { LotadoDoRh, Unidade } from '../api/tipos'
import { ProvedorDeAvisos } from '../componentes/Avisos'
import { instalarApiFalsa } from '../teste/api-falsa'
import { UnidadeDetalhe } from './UnidadeDetalhe'

const UNIDADE: Unidade = {
  id: 7,
  nome: 'PRESIDENCIA',
  ativo: true,
  codigoSiedos: 600000009,
  comarca: 'Goiânia',
  responsavel: null,
  habilitados: null,
}

const LOTADOS: LotadoDoRh[] = [
  {
    matricula: 5001,
    nome: 'Marina Alves Rocha',
    email: 'marina.rocha@tjgo.example',
    semEmail: false,
    jaCadastrada: false,
    lotacaoCerta: false,
  },
  {
    matricula: 5002,
    nome: 'Tiago Pereira Lima',
    email: null,
    semEmail: true,
    jaCadastrada: false,
    lotacaoCerta: false,
  },
]

function renderizar() {
  render(
    <MemoryRouter initialEntries={['/unidades/7']}>
      <ProvedorDeAvisos>
        <Routes>
          <Route path="/unidades/:id" element={<UnidadeDetalhe />} />
        </Routes>
      </ProvedorDeAvisos>
    </MemoryRouter>,
  )
}

describe('Página de uma unidade', () => {
  it('puxa os lotados do RH sem gravar nada e depois cadastra todos', async () => {
    const chamadas = instalarApiFalsa([
      ['/api/sincronizacao/unidades/7/lotados/cadastrar', {
        corpo: { lotadosNoRh: 2, criados: 1, atualizados: 0, semEmail: 1 },
      }],
      ['/api/sincronizacao/unidades/7/lotados', { corpo: LOTADOS }],
      ['/api/unidades/7', { corpo: UNIDADE }],
    ])

    renderizar()
    await screen.findByText('PRESIDENCIA')

    await userEvent.click(screen.getByRole('button', { name: /puxar do rh/i }))
    await screen.findByText('Marina Alves Rocha')

    // Consultar é leitura: só GET saiu da tela.
    expect(chamadas.every((c) => c.metodo === 'GET')).toBe(true)
    // Quem não tem e-mail aparece, e aparece dizendo por que fica de fora.
    expect(screen.getByText(/sem e-mail o login não a reconheceria/i)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^cadastrar todos$/i }))
    // A confirmação vem antes: a ação concede acesso.
    expect(screen.getByText(/cria como usuário quem ainda não existe/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.url.includes('/cadastrar'))).toBe(false)

    await userEvent.click(screen.getAllByRole('button', { name: /^cadastrar todos$/i })[1])
    await screen.findByText(/1 usuário\(s\) criado\(s\)/i)
  })

  it('sem código do SIEDOS não há o que perguntar ao RH', async () => {
    instalarApiFalsa([
      ['/api/unidades/7', { corpo: { ...UNIDADE, codigoSiedos: null } }],
    ])

    renderizar()

    // O aviso e o subtítulo dizem a mesma coisa: os dois valem.
    expect(await screen.findAllByText(/sem código do siedos/i)).not.toHaveLength(0)
    expect(screen.getByRole('button', { name: /puxar do rh/i })).toBeDisabled()
  })
})
