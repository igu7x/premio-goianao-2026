import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ComparacaoServidores, Edicao, UnidadeComparada } from '../api/tipos'
import { ProvedorDeAvisos } from '../componentes/Avisos'
import { instalarApiFalsa } from '../teste/api-falsa'
import { Sincronizacao } from './Sincronizacao'

const EDICAO: Edicao = {
  id: 1,
  ano: 2026,
  descricao: null,
  status: 'PUBLICADA',
  vigente: true,
  emitivel: true,
  aceitaInclusoes: true,
  criadoEm: '2026-01-10T09:00:00',
  atualizadoEm: null,
}

const SO_NA_API: UnidadeComparada = {
  situacao: 'SO_NA_API',
  codigo: 4321,
  unidadeId: null,
  nomeNoSistema: null,
  nomeNaApi: '3ª Vara Cível da Comarca de Goiânia',
  comarca: 'Goiânia',
}

const DESATUALIZADA: UnidadeComparada = {
  situacao: 'DESATUALIZADO',
  codigo: 1234,
  unidadeId: 9,
  nomeNoSistema: '1a Vara Civel de Goiania',
  nomeNaApi: '1ª Vara Cível da Comarca de Goiânia',
  comarca: 'Goiânia',
}

const COMPARACAO: ComparacaoServidores = {
  unidadeId: 9,
  unidadeNome: '1a Vara Civel de Goiania',
  codigo: 1234,
  responsavelSugerido: 'Rafael Siqueira Bittencourt',
  servidores: [
    {
      situacao: 'SO_NA_API',
      matricula: 5001,
      nome: 'Marina Alves Rocha',
      email: 'marina.rocha@tjgo.example',
      servidorHabilitadoId: null,
      origem: null,
      semEmailNaApi: false,
    },
    {
      situacao: 'SO_NA_API',
      matricula: 5002,
      nome: 'Tiago Pereira Lima',
      email: null,
      servidorHabilitadoId: null,
      origem: null,
      semEmailNaApi: true,
    },
    {
      situacao: 'ORFAO',
      matricula: null,
      nome: 'Helena Costa Faria',
      email: 'helena.faria@tjgo.example',
      servidorHabilitadoId: 77,
      origem: 'MANUAL',
      semEmailNaApi: false,
    },
  ],
}

function renderizar() {
  render(
    <ProvedorDeAvisos>
      <Sincronizacao />
    </ProvedorDeAvisos>,
  )
}

/** A rota de situação precisa vir antes da de unidades: a api falsa casa por
 *  prefixo, na ordem em que as rotas foram declaradas. */
function rotasBase(ligada: boolean) {
  return [
    ['/api/sincronizacao/situacao', { corpo: { ligada, origemDosDados: ligada ? 'API corporativa do TJGO' : 'dados de demonstração (mock)' } }],
    ['/api/edicoes', { corpo: [EDICAO] }],
  ] as Array<[string, { corpo: unknown }]>
}

describe('Tela de sincronização com o RH (feature 010)', () => {
  it('avisa que a integração está desligada e compara sem gravar nada', async () => {
    const chamadas = instalarApiFalsa([
      ...rotasBase(false),
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA, SO_NA_API] }],
    ])

    renderizar()

    await screen.findByText(/integração corporativa não configurada/i)
    expect(screen.getByText(/dados de demonstração/i)).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /comparar/i }))

    await screen.findByText('1a Vara Civel de Goiania')
    expect(screen.getByRole('button', { name: /atualizar/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /cadastrar unidade/i })).toBeEnabled()

    // A comparação é leitura: só GET saiu da tela.
    expect(chamadas.every((c) => c.metodo === 'GET')).toBe(true)
  })

  it('não oferece inclusão a quem o RH não tem e-mail, nem desvinculação de inclusão manual', async () => {
    instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades/9/servidores', { corpo: COMPARACAO }],
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA] }],
    ])

    renderizar()

    await waitFor(() => expect(screen.getByRole('combobox', { name: /edição/i })).toBeInTheDocument())
    await userEvent.type(screen.getByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /comparar/i }))

    await screen.findByText('1a Vara Civel de Goiania')
    await userEvent.click(screen.getByRole('button', { name: /servidores/i }))

    await screen.findByRole('dialog')
    await screen.findByText('Marina Alves Rocha')

    const inclusoes = screen.getAllByRole('button', { name: /incluir na lista/i })
    expect(inclusoes[0]).toBeEnabled()
    expect(inclusoes[1]).toBeDisabled()
    expect(screen.getByText(/não tem e-mail corporativo/i)).toBeInTheDocument()

    // Inclusão manual foi ajuste humano: permanece, e sem botão (010/CA-6).
    expect(screen.queryByRole('button', { name: /desvincular/i })).not.toBeInTheDocument()
    expect(screen.getByText(/foi um ajuste humano e permanece/i)).toBeInTheDocument()

    // O responsável do RH é sugestão, não designação.
    expect(screen.getByText(/como responsável por/i)).toBeInTheDocument()
  })

  it('inclui pela matrícula e recarrega a comparação', async () => {
    const chamadas = instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades/9/servidores', { corpo: COMPARACAO }],
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA] }],
    ])

    renderizar()

    await waitFor(() => expect(screen.getByRole('combobox', { name: /edição/i })).toBeInTheDocument())
    await userEvent.type(screen.getByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /comparar/i }))

    await screen.findByText('1a Vara Civel de Goiania')
    await userEvent.click(screen.getByRole('button', { name: /servidores/i }))
    await screen.findByText('Marina Alves Rocha')

    await userEvent.click(screen.getAllByRole('button', { name: /incluir na lista/i })[0])

    await waitFor(() => {
      const inclusao = chamadas.find((c) => c.metodo === 'POST')
      expect(inclusao?.url).toContain('/api/sincronizacao/unidades/9/servidores')
      // Matrícula e edição no corpo; dado pessoal nunca na URL.
      expect(inclusao?.corpo).toContain('5001')
      expect(inclusao?.url).not.toContain('@')
    })
  })
})
