import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { SituacaoDaAtualizacao, Usuario } from '../api/tipos'
import { ProvedorDeAvisos } from '../componentes/Avisos'
import { instalarApiFalsa } from '../teste/api-falsa'
import { Usuarios } from './Usuarios'

function usuario(parcial: Partial<Usuario>): Usuario {
  return {
    id: 1,
    cpfMascarado: null,
    nome: 'Fulano',
    email: 'fulano@tjgo.jus.br',
    unidadeLotacao: null,
    areaAtuacao: null,
    papeis: ['SERVIDOR'],
    ativo: true,
    temSenha: false,
    criadoEm: '2026-09-16T10:00:00',
    ...parcial,
  }
}

const NUNCA: SituacaoDaAtualizacao = {
  estado: 'NUNCA_EXECUTADA',
  escopo: null,
  unidadesTotal: 0,
  unidadesProcessadas: 0,
  unidadeAtual: null,
  pessoas: 0,
  criados: 0,
  atualizados: 0,
  semEmail: 0,
  unidadesComFalha: 0,
  iniciadaEm: null,
  terminadaEm: null,
  mensagem: null,
}

const USUARIOS = [
  usuario({ id: 1, nome: 'DAHYENNE MARA MARTINS LIMA ALVES', email: 'dmmlalves@tjgo.jus.br' }),
  usuario({ id: 2, nome: 'João Ribeiro', email: 'jribeiro@tjgo.jus.br', papeis: ['SUPERADMIN'] }),
  usuario({ id: 3, nome: 'Diuly Caliny', email: 'dcpsilva@tjgo.jus.br', ativo: false }),
]

function renderizar() {
  render(
    <ProvedorDeAvisos>
      <Usuarios />
    </ProvedorDeAvisos>,
  )
}

describe('Promover superadmin', () => {
  it('sugere enquanto digita, pelo nome sem acento e sem caixa', async () => {
    instalarApiFalsa([
      ['/api/usuarios/atualizacao-rh', { corpo: NUNCA }],
      ['/api/usuarios', { corpo: USUARIOS }],
    ])
    renderizar()

    await userEvent.click(await screen.findByRole('button', { name: /promover superadmin/i }))
    await userEvent.type(screen.getByLabelText('Usuário'), 'dahy')

    const sugestao = await screen.findByRole('option', { name: /dahyenne/i })
    await userEvent.click(sugestao)

    expect(screen.getByLabelText('Usuário')).toHaveValue('dmmlalves@tjgo.jus.br')
  })

  it('não sugere quem já é superadmin nem quem está desativado', async () => {
    instalarApiFalsa([
      ['/api/usuarios/atualizacao-rh', { corpo: NUNCA }],
      ['/api/usuarios', { corpo: USUARIOS }],
    ])
    renderizar()

    await userEvent.click(await screen.findByRole('button', { name: /promover superadmin/i }))

    await userEvent.type(screen.getByLabelText('Usuário'), 'joao')
    expect(screen.queryByRole('option')).not.toBeInTheDocument()

    await userEvent.clear(screen.getByLabelText('Usuário'))
    await userEvent.type(screen.getByLabelText('Usuário'), 'diuly')
    expect(screen.queryByRole('option')).not.toBeInTheDocument()
  })
})

describe('Atualizar base de usuários', () => {
  it('oferece as duas opções e dispara a escolhida', async () => {
    const chamadas = instalarApiFalsa([
      // A api falsa não distingue GET de POST: o estado inicial serve aos dois.
      ['/api/usuarios/atualizacao-rh', { corpo: NUNCA }],
      ['/api/usuarios', { corpo: USUARIOS }],
    ])
    renderizar()

    await userEvent.click(await screen.findByRole('button', { name: /atualizar base de usuários/i }))
    expect(screen.getByRole('button', { name: /base completa do rh/i })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^tjgo/i }))

    await waitFor(() => {
      const disparo = chamadas.find(
        (c) => c.url.includes('/atualizacao-rh') && c.metodo === 'POST',
      )
      expect(disparo?.corpo).toContain('"escopo":"TJGO"')
    })
  })

  it('retoma o acompanhamento de uma atualização que já estava rodando', async () => {
    instalarApiFalsa([
      ['/api/usuarios/atualizacao-rh', {
        corpo: {
          ...NUNCA,
          estado: 'EM_ANDAMENTO',
          escopo: 'COMPLETA',
          unidadesTotal: 200,
          unidadesProcessadas: 50,
          unidadeAtual: 'CENTRAL DE EXPEDIÇAO DE MANDADOS',
        },
      }],
      ['/api/usuarios', { corpo: USUARIOS }],
    ])
    renderizar()

    expect(await screen.findByText(/50 de 200 unidade/i)).toBeInTheDocument()
    expect(screen.getByText(/agora em central de expediçao/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /atualizando… 25%/i })).toBeDisabled()
  })
})
