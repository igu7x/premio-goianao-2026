import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Unidade } from '../api/tipos'
import { ProvedorDeAvisos } from '../componentes/Avisos'
import { instalarApiFalsa } from '../teste/api-falsa'
import { Unidades } from './Unidades'

const COM_RESPONSAVEL: Unidade = {
  id: 1,
  nome: 'PRESIDENCIA',
  ativo: true,
  codigoSiedos: 600000009,
  comarca: null,
  responsavel: { id: 9, nome: 'Igor Freitas', email: 'ifccteixeira@tjgo.jus.br' },
}

const SEM_RESPONSAVEL: Unidade = {
  id: 2,
  nome: 'SECRETARIA GERAL DA PRESIDENCIA',
  ativo: true,
  codigoSiedos: 600000010,
  comarca: null,
  responsavel: null,
}

function renderizar() {
  render(
    <ProvedorDeAvisos>
      <Unidades />
    </ProvedorDeAvisos>,
  )
}

describe('Associar responsáveis a partir do RH', () => {
  it('varre em rodadas, seguindo o cursor até a última', async () => {
    // Duas rodadas: a primeira vem cheia (50), a segunda encerra.
    let rodada = 0
    const chamadas = instalarApiFalsa([
      [
        '/api/sincronizacao/unidades/responsaveis',
        {
          get corpo() {
            rodada += 1
            return rodada === 1
              ? {
                  processadas: 50,
                  ultimoId: 77,
                  designados: 40,
                  usuariosCriados: 30,
                  papelConcedido: 2,
                  semResponsavelNoRh: 8,
                  semEmail: 2,
                  restantes: 10,
                }
              : {
                  processadas: 3,
                  ultimoId: 90,
                  designados: 3,
                  usuariosCriados: 1,
                  papelConcedido: 0,
                  semResponsavelNoRh: 0,
                  semEmail: 0,
                  restantes: 10,
                }
          },
        },
      ],
      ['/api/unidades', { corpo: [COM_RESPONSAVEL, SEM_RESPONSAVEL] }],
    ])

    renderizar()

    await userEvent.click(
      await screen.findByRole('button', { name: /associar responsáveis pelo rh/i }),
    )
    // Confirmação antes: a ação cria usuários e concede papel.
    expect(screen.getByText(/ganham? o papel de magistrado/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.url.includes('responsaveis'))).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: /^associar$/i }))

    await waitFor(() => {
      const rodadas = chamadas.filter((c) => c.url.includes('responsaveis'))
      expect(rodadas).toHaveLength(2)
      // A segunda rodada continua de onde a primeira parou.
      expect(rodadas[0].url).not.toContain('desde=')
      expect(rodadas[1].url).toContain('desde=77')
    })

    // 40 + 3 designados nas duas rodadas.
    await screen.findByText(/43 unidade\(s\) com responsável designado/i)
  })

  it('não oferece a varredura quando todas já têm responsável', async () => {
    instalarApiFalsa([['/api/unidades', { corpo: [COM_RESPONSAVEL] }]])

    renderizar()

    await screen.findByText(/todas têm responsável designado/i)
    expect(
      screen.queryByRole('button', { name: /associar responsáveis pelo rh/i }),
    ).not.toBeInTheDocument()
  })
})
