import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Edicao, Magistrado, PessoaDoRh } from '../../api/tipos'
import { instalarApiFalsa } from '../../teste/api-falsa'
import { AbaMagistrados } from './AbaMagistrados'

const MAGISTRADO: Magistrado = {
  id: 7,
  email: 'rafael.bittencourt@tjgo.example',
  cpf: null,
  cpfFormatado: null,
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
      ['/api/unidades/cadastradas', { corpo: [{ id: 3, nome: '2ª Vara Cível', codigoSiedos: 203010005, comarca: 'Goiânia' }] }],
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

/** Como o RH devolve na busca: em maiúsculas e sem o e-mail completado pelo AD. */
const NA_BUSCA: PessoaDoRh = {
  origem: 'RH',
  matricula: 5001,
  nome: 'MARINA ALVES ROCHA',
  email: null,
  cpfMascarado: '***.456.789-**',
  temEmail: false,
}

describe('Escolha do magistrado no RH, em vez do e-mail digitado', () => {
  it('a escolha completa o e-mail pela consulta por matrícula', async () => {
    const chamadas = instalarApiFalsa([
      ['/api/rh/pessoas/situacao', { corpo: { disponivel: true } }],
      [
        '/api/rh/pessoas/5001',
        { corpo: { ...NA_BUSCA, email: 'marina.rocha@tjgo.example', temEmail: true } },
      ],
      ['/api/pessoas?termo=', { corpo: { pessoas: [NA_BUSCA], rhRespondeu: true } }],
      ['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }],
    ])

    render(<AbaMagistrados edicao={edicao({ status: 'RASCUNHO' })} />)
    await screen.findByText('Rafael Siqueira Bittencourt')
    await userEvent.click(screen.getByRole('button', { name: /novo magistrado/i }))

    await userEvent.type(await screen.findByLabelText('Magistrado'), 'rocha')
    await userEvent.click(await screen.findByText('MARINA ALVES ROCHA'))

    // O e-mail nunca e digitado, e a busca nao o traz: quem completa pelo AD e
    // a consulta por matricula.
    await screen.findByText('marina.rocha@tjgo.example')
    expect(chamadas.some((c) => c.url.includes('/api/rh/pessoas/5001'))).toBe(true)
    // O nome continua editavel: e ele que sai impresso no certificado.
    expect(screen.getByLabelText(/nome completo/i)).toHaveValue('MARINA ALVES ROCHA')
    expect(screen.getByRole('button', { name: /cadastrar/i })).toBeEnabled()
  })

  it('quem não tem e-mail no RH nem no AD não pode ser cadastrado', async () => {
    instalarApiFalsa([
      ['/api/rh/pessoas/situacao', { corpo: { disponivel: true } }],
      ['/api/rh/pessoas/5001', { corpo: NA_BUSCA }],
      ['/api/pessoas?termo=', { corpo: { pessoas: [NA_BUSCA], rhRespondeu: true } }],
      ['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }],
    ])

    render(<AbaMagistrados edicao={edicao({ status: 'RASCUNHO' })} />)
    await screen.findByText('Rafael Siqueira Bittencourt')
    await userEvent.click(screen.getByRole('button', { name: /novo magistrado/i }))

    await userEvent.type(await screen.findByLabelText('Magistrado'), 'rocha')
    await userEvent.click(await screen.findByText('MARINA ALVES ROCHA'))

    expect(await screen.findByText(/não seria reconhecida no login/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cadastrar/i })).toBeDisabled()
  })

  it('sem integração com o RH, o e-mail volta a ser digitado', async () => {
    instalarApiFalsa([
      ['/api/rh/pessoas/situacao', { corpo: { disponivel: false } }],
      ['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }],
    ])

    render(<AbaMagistrados edicao={edicao({ status: 'RASCUNHO' })} />)
    await screen.findByText('Rafael Siqueira Bittencourt')
    await userEvent.click(screen.getByRole('button', { name: /novo magistrado/i }))

    expect(await screen.findByLabelText(/e-mail corporativo/i)).toBeInTheDocument()
    expect(screen.getByText(/busca no RH não está disponível/i)).toBeInTheDocument()
    expect(screen.queryByLabelText('Magistrado')).not.toBeInTheDocument()
  })
})

describe('Lista de unidades do reconhecimento', () => {
  it('oferece as unidades cadastradas no sistema, e não pergunta ao RH', async () => {
    const chamadas = instalarApiFalsa([
      ['/api/unidades/cadastradas', {
        corpo: [
          { id: 1, nome: 'PRESIDENCIA', codigoSiedos: 600000009, comarca: null },
          { id: 2, nome: 'ASSESSORIA DE IMPRENSA', codigoSiedos: 600000123, comarca: null },
        ],
      }],
      ['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }],
    ])

    render(
      <AbaMagistrados edicao={edicao({ status: 'PUBLICADA', vigente: true, aceitaInclusoes: true })} />,
    )
    await screen.findByText('Rafael Siqueira Bittencourt')
    await userEvent.click(screen.getByRole('button', { name: /adicionar unidade/i }))

    const campo = await screen.findByRole('combobox', { name: /unidade/i })
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /PRESIDENCIA — 600000009/ })).toBeInTheDocument(),
    )
    expect(campo).toBeEnabled()
    // A falha do RH esvaziava a lista em silêncio: a fonte agora é o cadastro.
    expect(chamadas.some((c) => c.url.includes('/api/unidades/egesp'))).toBe(false)
  })

  it('diz o motivo quando a lista não carrega, em vez de mostrá-la vazia', async () => {
    instalarApiFalsa([
      ['/api/unidades/cadastradas', { status: 500, corpo: { mensagem: 'Erro interno.' } }],
      ['/api/edicoes/1/magistrados', { corpo: [MAGISTRADO] }],
    ])

    render(
      <AbaMagistrados edicao={edicao({ status: 'PUBLICADA', vigente: true, aceitaInclusoes: true })} />,
    )
    await screen.findByText('Rafael Siqueira Bittencourt')
    await userEvent.click(screen.getByRole('button', { name: /adicionar unidade/i }))

    expect(await screen.findByText('Erro interno.')).toBeInTheDocument()
  })
})

describe('Busca do magistrado no sistema e no RH', () => {
  it('quem já está no sistema é escolhido direto, sem consultar o RH', async () => {
    const chamadas = instalarApiFalsa([
      ['/api/rh/pessoas/situacao', { corpo: { disponivel: true } }],
      ['/api/pessoas?termo=', {
        corpo: {
          pessoas: [{
            origem: 'SISTEMA',
            matricula: null,
            nome: 'Ana Cristina Marques Rebelo',
            email: 'ana.rebelo@tjgo.example',
            cpfMascarado: null,
            temEmail: true,
          }],
          rhRespondeu: true,
        },
      }],
      ['/api/unidades/cadastradas', { corpo: [] }],
      ['/api/edicoes/1/magistrados', { corpo: [] }],
    ])

    render(<AbaMagistrados edicao={edicao({ status: 'RASCUNHO', vigente: false, aceitaInclusoes: true })} />)
    await userEvent.click(await screen.findByRole('button', { name: /novo magistrado/i }))
    await userEvent.type(await screen.findByLabelText('Magistrado'), 'rebelo')

    await userEvent.click(await screen.findByRole('button', { name: /ana cristina marques rebelo/i }))

    expect(await screen.findByText('ana.rebelo@tjgo.example')).toBeInTheDocument()
    // Quem já tem cadastro não passa pela consulta por matrícula no RH.
    expect(chamadas.some((c) => /\/api\/rh\/pessoas\/\d/.test(c.url))).toBe(false)
  })

  it('avisa quando o RH não respondeu, mas mostra quem está no sistema', async () => {
    instalarApiFalsa([
      ['/api/rh/pessoas/situacao', { corpo: { disponivel: true } }],
      ['/api/pessoas?termo=', { corpo: { pessoas: [], rhRespondeu: false } }],
      ['/api/unidades/cadastradas', { corpo: [] }],
      ['/api/edicoes/1/magistrados', { corpo: [] }],
    ])

    render(<AbaMagistrados edicao={edicao({ status: 'RASCUNHO', vigente: false, aceitaInclusoes: true })} />)
    await userEvent.click(await screen.findByRole('button', { name: /novo magistrado/i }))
    await userEvent.type(await screen.findByLabelText('Magistrado'), 'rebelo')

    expect(await screen.findByText(/o rh não respondeu agora/i)).toBeInTheDocument()
  })
})
