import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { instalarApiFalsa } from '../teste/api-falsa'
import { Verificar } from './Verificar'

function abrirEm(rota: string) {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <Routes>
        <Route path="/verificar" element={<Verificar />} />
        <Route path="/verificar/:codigo" element={<Verificar />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Conferência pública (feature 007)', () => {
  it('CA-1/CA-3: o código da URL é conferido sozinho e os dados aparecem', async () => {
    instalarApiFalsa([
      [
        '/api/public/certificados/',
        {
          corpo: {
            valido: true,
            codigo: '47RR-CTCH-321N',
            nome: 'Rafael Siqueira Bittencourt',
            unidade: '1ª Vara Cível da Comarca de Goiânia',
            edicaoAno: 2025,
            selo: 'OURO',
            tipo: 'MAGISTRADO',
            emitidoEm: '2026-08-30T10:15:00',
          },
        },
      ],
    ])

    abrirEm('/verificar/47RR-CTCH-321N')

    expect(await screen.findByText(/certificado autêntico/i)).toBeInTheDocument()
    expect(screen.getByText('Rafael Siqueira Bittencourt')).toBeInTheDocument()
    expect(screen.getByText('1ª Vara Cível da Comarca de Goiânia')).toBeInTheDocument()
    expect(screen.getByText('2025')).toBeInTheDocument()
  })

  it('CA-5: a tela não tem onde exibir CPF', async () => {
    instalarApiFalsa([
      [
        '/api/public/certificados/',
        {
          corpo: {
            valido: true,
            codigo: '47RR-CTCH-321N',
            nome: 'Rafael Siqueira Bittencourt',
            unidade: '1ª Vara Cível da Comarca de Goiânia',
            edicaoAno: 2025,
            selo: 'OURO',
            tipo: 'MAGISTRADO',
            emitidoEm: '2026-08-30T10:15:00',
          },
        },
      ],
    ])

    const { container } = abrirEm('/verificar/47RR-CTCH-321N')
    await screen.findByText(/certificado autêntico/i)

    // O texto de apoio da página menciona a palavra CPF ao explicar que ele não
    // é exibido; o que precisa estar limpo é o painel de resultado.
    const resultado = container.querySelector('.resultado')?.textContent ?? ''
    expect(resultado).not.toMatch(/CPF/i)
    expect(resultado).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/)
    expect(resultado).not.toMatch(/\b\d{11}\b/)
  })

  it('CA-2: código inexistente diz que não foi encontrado, sem vazar nada', async () => {
    instalarApiFalsa([
      [
        '/api/public/certificados/',
        { status: 404, corpo: { valido: false, codigo: 'ZZZZ-9999-YYYY' } },
      ],
    ])

    abrirEm('/verificar/ZZZZ-9999-YYYY')

    expect(await screen.findByText(/nenhum certificado encontrado/i)).toBeInTheDocument()
    expect(screen.queryByText(/reconhecido:/i)).not.toBeInTheDocument()
  })

  it('sem código na URL, apenas oferece o formulário', () => {
    instalarApiFalsa([])
    abrirEm('/verificar')

    expect(screen.getByRole('textbox', { name: /código de validação/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /conferir/i })).toBeDisabled()
  })
})
