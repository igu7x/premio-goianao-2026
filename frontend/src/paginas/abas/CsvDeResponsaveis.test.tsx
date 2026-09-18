import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Edicao, UnidadeCadastrada } from '../../api/tipos'
import { ProvedorDeAvisos } from '../../componentes/Avisos'
import { instalarApiFalsa } from '../../teste/api-falsa'
import { CsvDeResponsaveis } from './CsvDeResponsaveis'

const UNIDADES: UnidadeCadastrada[] = [
  { id: 1, nome: 'PRESIDENCIA', codigoSiedos: 600000009, comarca: null },
  { id: 2, nome: 'SECRETARIA GERAL DA PRESIDENCIA', codigoSiedos: 600000010, comarca: null },
]

const RELATORIO = {
  linhasLidas: 3,
  designados: 2,
  usuariosCriados: 1,
  papelConcedido: 0,
  substituidos: 0,
  jaEram: 0,
  reconhecimentos: 2,
  listasParaSemear: 2,
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
    <ProvedorDeAvisos>
      <CsvDeResponsaveis edicao={EDICAO} aoImportar={async () => {}} />
    </ProvedorDeAvisos>,
  )
}

describe('Planilha de magistrados responsáveis', () => {
  it('sobe o CSV e mostra o que entrou e as linhas que não entraram', async () => {
    const chamadas = instalarApiFalsa([
      ['/api/unidades/responsaveis/importar', { corpo: RELATORIO }],
      [
        '/api/unidades/responsaveis/semeadura',
        {
          corpo: {
            estado: 'CONCLUIDA',
            edicaoAno: 2026,
            unidadesTotal: 2,
            unidadesProcessadas: 2,
            unidadeAtual: null,
            incluidos: 47,
            jaExistentes: 0,
            semEmail: 0,
            unidadesComFalha: 0,
            ultimaFalha: null,
            iniciadaEm: null,
            terminadaEm: null,
            mensagem: null,
          },
        },
      ],
      ['/api/unidades/cadastradas', { corpo: UNIDADES }],
    ])

    renderizar()

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
    // E quantas listas de servidores a planilha semeou do RH.
    expect(screen.getByText(/2 lista\(s\) de servidores entraram na fila/i)).toBeInTheDocument()

    // A semeadura roda em segundo plano: a tela pergunta como vai e conta o fim.
    expect(
      await screen.findByText(/47 servidor\(es\) incluído\(s\) em 2 unidade\(s\)/i),
    ).toBeInTheDocument()

    const envio = chamadas.find((c) => c.url.includes('responsaveis/importar'))
    expect(envio?.metodo).toBe('POST')
    // Os selos e a semeadura são da edição aberta, não da vigente por suposição.
    expect(envio?.url).toContain('edicaoId=7')
  })

  it('o modelo de teste usa os códigos reais das unidades cadastradas', async () => {
    instalarApiFalsa([['/api/unidades/cadastradas', { corpo: UNIDADES }]])

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
    await userEvent.click(await screen.findByRole('button', { name: /baixar modelo de teste/i }))

    await waitFor(() => expect(gerado).toContain('nome;email;unidade;selo'))
    expect(gerado).toContain(';600000009;')
    expect(gerado).toContain(';600000010;')
    // Nomes fictícios, no domínio reservado: ninguém confunde com a lista real.
    expect(gerado).toContain('@tjgo.example')
    vi.unstubAllGlobals()
  })
})
