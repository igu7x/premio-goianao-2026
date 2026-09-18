import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type {
  ComparacaoServidores,
  Edicao,
  ImportacaoDaUnidade,
  UnidadeComparada,
} from '../api/tipos'
import { ProvedorDeAvisos } from '../componentes/Avisos'
import { instalarApiFalsa } from '../teste/api-falsa'
import { Sincronizacao } from './Sincronizacao'

// A comparação de servidores usa a edição da sessão (feature 011).
vi.mock('../sessao/SessaoContexto', () => ({
  useSessao: () => ({
    identidade: {
      email: 'super@tjgo.example',
      nome: 'Superadministrador',
      papeis: ['SUPERADMIN'],
      edicao: { id: 1, ano: 2026, vigente: true, papeis: ['SUPERADMIN'] },
      edicoesDisponiveis: [],
    },
  }),
}))

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
  codigoPai: 5000,
  nomePai: 'Diretoria do Foro de Goiânia',
  nivel: 4,
}

const DESATUALIZADA: UnidadeComparada = {
  situacao: 'DESATUALIZADO',
  codigo: 1234,
  unidadeId: 9,
  nomeNoSistema: '1a Vara Civel de Goiania',
  nomeNaApi: '1ª Vara Cível da Comarca de Goiânia',
  comarca: 'Goiânia',
  codigoPai: 5000,
  nomePai: 'Diretoria do Foro de Goiânia',
  nivel: 4,
}

/** Órfã: existe só aqui, então o RH não tem pai nem nível para ela. */
const ORFA: UnidadeComparada = {
  situacao: 'ORFAO',
  codigo: 777,
  unidadeId: 12,
  nomeNoSistema: 'Vara Extinta de Exemplo',
  nomeNaApi: null,
  comarca: 'Goiânia',
  codigoPai: null,
  nomePai: null,
  nivel: null,
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

const IMPORTACAO: ImportacaoDaUnidade = {
  lotadosNoRh: 24,
  usuariosCriados: 12,
  usuariosAtualizados: 3,
  habilitadosIncluidos: 9,
  jaHabilitados: 0,
  preservadosRemovidos: 2,
  semEmail: 1,
  totalAtivos: 21,
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
    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))

    await screen.findByText('1a Vara Civel de Goiania')
    expect(screen.getByRole('button', { name: /atualizar/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /cadastrar unidade/i })).toBeEnabled()

    // A comparação é leitura: só GET saiu da tela.
    expect(chamadas.every((c) => c.metodo === 'GET')).toBe(true)
  })

  it('compara a base completa do RH sem mandar código nenhum', async () => {
    const chamadas = instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA, SO_NA_API] }],
    ])

    renderizar()

    await userEvent.click(
      await screen.findByRole('button', { name: /comparar a base completa do rh/i }),
    )

    await screen.findByText('1a Vara Civel de Goiania')

    const consulta = chamadas.find((c) => c.url.includes('/api/sincronizacao/unidades'))
    expect(consulta?.metodo).toBe('GET')
    // Sem código o RH devolve a base inteira de unidades — é esse o pedido.
    expect(consulta?.url).not.toContain('codigo=')
  })

  it('o atalho do TJGO preenche o código sem disparar a comparação', async () => {
    const chamadas = instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA] }],
    ])

    renderizar()

    await userEvent.click(await screen.findByRole('button', { name: /tjgo \(tribunal inteiro\)/i }))

    expect(screen.getByLabelText(/código da unidade/i)).toHaveValue('600000009')
    // Preencher não é comparar: a chamada só sai no clique do botão ao lado.
    expect(chamadas.some((c) => c.url.includes('/api/sincronizacao/unidades?'))).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))
    await screen.findByText('1a Vara Civel de Goiania')
    expect(
      chamadas.find((c) => c.url.includes('/api/sincronizacao/unidades?'))?.url,
    ).toContain('codigo=600000009')
  })

  it('mostra o filtro mesmo quando a comparação traz poucas unidades', async () => {
    instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA] }],
    ])

    renderizar()

    await userEvent.type(await screen.findByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))

    await screen.findByText('1a Vara Civel de Goiania')
    expect(screen.getByLabelText(/filtrar por nome, comarca ou código/i)).toBeInTheDocument()
    expect(screen.getByText(/1 de 1 unidade/i)).toBeInTheDocument()
  })

  it('mostra o código do pai na linha da unidade, e nada quando ele não veio', async () => {
    instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA, ORFA] }],
    ])

    renderizar()

    await userEvent.type(screen.getByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))

    await screen.findByText('1a Vara Civel de Goiania')
    expect(screen.getByText(/código 1234 · pai: 5000/)).toBeInTheDocument()
    expect(screen.getByText(/sob Diretoria do Foro de Goiânia/)).toBeInTheDocument()

    // Órfã não veio do RH: sem pai, a linha não inventa nada no lugar.
    expect(screen.getByText(/código 777/)).not.toHaveTextContent(/pai/i)
  })

  it('não oferece inclusão a quem o RH não tem e-mail, nem desvinculação de inclusão manual', async () => {
    instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades/9/servidores', { corpo: COMPARACAO }],
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA] }],
    ])

    renderizar()

    await waitFor(() => expect(screen.getByLabelText(/código da unidade/i)).toBeInTheDocument())
    await userEvent.type(screen.getByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))

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

    await waitFor(() => expect(screen.getByLabelText(/código da unidade/i)).toBeInTheDocument())
    await userEvent.type(screen.getByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))

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

  it('a importação em lote só acontece depois de confirmada, e resume os números', async () => {
    const chamadas = instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades/9/importar', { corpo: IMPORTACAO }],
      ['/api/sincronizacao/unidades/9/servidores', { corpo: COMPARACAO }],
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA] }],
    ])

    renderizar()

    await waitFor(() => expect(screen.getByLabelText(/código da unidade/i)).toBeInTheDocument())
    await userEvent.type(screen.getByLabelText(/código da unidade/i), '1234')
    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))

    await screen.findByText('1a Vara Civel de Goiania')
    await userEvent.click(screen.getByRole('button', { name: /servidores/i }))
    await screen.findByText('Marina Alves Rocha')

    await userEvent.click(screen.getByRole('button', { name: /importar unidade inteira/i }))

    // A confirmação existe justamente para haver um passo antes da gravação.
    await screen.findByText(/importar a unidade inteira\?/i)
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
    expect(screen.getByText(/o papel dele não muda/i)).toBeInTheDocument()
    expect(screen.getByText(/não volta/i)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^importar$/i }))

    await waitFor(() => {
      const importacao = chamadas.find((c) => c.metodo === 'POST')
      expect(importacao?.url).toContain('/api/sincronizacao/unidades/9/importar')
      expect(importacao?.corpo).toContain('"edicaoId":1')
    })

    // Preservados e sem e-mail aparecem porque explicam a conta não fechar;
    // "já constavam" fica de fora quando é zero.
    const resumo = await screen.findByText(/12 usuário\(s\) criado\(s\)/i)
    expect(resumo).toHaveTextContent(/2 preservado\(s\) fora da lista/i)
    expect(resumo).toHaveTextContent(/1 sem e-mail no RH/i)
    expect(resumo).not.toHaveTextContent(/já constavam/i)

    // A comparação da unidade é relida depois de importar.
    const leituras = chamadas.filter((c) => c.url.includes('/servidores?edicaoId='))
    expect(leituras.length).toBeGreaterThan(1)
  })
})

describe('Cadastro em lote das unidades que só existem no RH', () => {
  it('cadastra todas as faltantes depois de confirmar, no escopo da comparação', async () => {
    const chamadas = instalarApiFalsa([
      ...rotasBase(true),
      [
        '/api/sincronizacao/unidades/em-lote',
        { corpo: { criadas: 2, jaExistiam: 1, casadas: 0 } },
      ],
      ['/api/sincronizacao/unidades', { corpo: [SO_NA_API, DESATUALIZADA] }],
    ])

    renderizar()

    await userEvent.click(
      await screen.findByRole('button', { name: /comparar a base completa do rh/i }),
    )
    await screen.findByRole('button', { name: /cadastrar as 1$/i })

    // O botão anuncia quantas vai criar — e não dispara sem confirmação.
    await userEvent.click(screen.getByRole('button', { name: /cadastrar as 1$/i }))
    expect(screen.getByText(/cadastrar 1 unidade/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.url.includes('em-lote'))).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: /cadastrar todas/i }))

    const lote = await waitFor(() => {
      const c = chamadas.find((x) => x.url.includes('em-lote'))
      expect(c).toBeDefined()
      return c!
    })
    expect(lote.metodo).toBe('POST')
    // Comparou a base inteira: o lote vai sem código, como a comparação foi.
    expect(lote.url).not.toContain('codigo=')
  })

  it('o lote de um ramo leva o mesmo código da comparação', async () => {
    const chamadas = instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades/em-lote', { corpo: { criadas: 1, jaExistiam: 0, casadas: 0 } }],
      ['/api/sincronizacao/unidades', { corpo: [SO_NA_API] }],
    ])

    renderizar()

    await userEvent.type(await screen.findByLabelText(/código da unidade/i), '600000009')
    await userEvent.click(screen.getByRole('button', { name: /^comparar$/i }))
    await screen.findByRole('button', { name: /cadastrar as 1$/i })

    await userEvent.click(screen.getByRole('button', { name: /cadastrar as 1$/i }))
    await userEvent.click(screen.getByRole('button', { name: /cadastrar todas/i }))

    const lote = await waitFor(() => {
      const c = chamadas.find((x) => x.url.includes('em-lote'))
      expect(c).toBeDefined()
      return c!
    })
    expect(lote.url).toContain('codigo=600000009')
  })

  it('não oferece o lote quando não falta nenhuma unidade', async () => {
    instalarApiFalsa([
      ...rotasBase(true),
      ['/api/sincronizacao/unidades', { corpo: [DESATUALIZADA] }],
    ])

    renderizar()

    await userEvent.click(
      await screen.findByRole('button', { name: /comparar a base completa do rh/i }),
    )
    await screen.findByText('1a Vara Civel de Goiania')
    expect(screen.queryByRole('button', { name: /cadastrar as /i })).not.toBeInTheDocument()
  })
})
