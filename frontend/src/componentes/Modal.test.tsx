import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Modal } from './Basicos'

/**
 * O modal precisa sair no <body>, e não onde o componente está na árvore.
 *
 * A cortina é `position: fixed`, e fixed se posiciona em relação à viewport —
 * exceto quando algum ancestral cria bloco de contenção, o que qualquer
 * transform, filter ou animação de transform faz. A `.pagina` anima com
 * translateY na entrada, e isso bastou para o modal passar a se centrar na área
 * de conteúdo em vez da janela: aparecia deslocado para a direita e com o
 * cabeçalho cortado acima da tela.
 *
 * O teste amarra o portal para que uma animação nova em qualquer ancestral não
 * traga o problema de volta — ele reapareceria de um jeito difícil de associar
 * à causa.
 */
describe('Modal', () => {
  it('renderiza no body, fora do container que o invocou', () => {
    const { container } = render(
      <div className="pagina" style={{ transform: 'translateY(0)' }}>
        <Modal titulo="Editar usuário" aoFechar={vi.fn()}>
          <p>conteúdo</p>
        </Modal>
      </div>,
    )

    // Nada da cortina dentro do container que renderizou o modal…
    expect(container.querySelector('.cortina')).toBeNull()
    // …e ela existe no documento, pendurada no body.
    const cortina = document.body.querySelector('.cortina')
    expect(cortina).not.toBeNull()
    expect(cortina?.parentElement).toBe(document.body)
  })

  it('fecha com Escape e devolve a rolagem ao sair', () => {
    const aoFechar = vi.fn()
    const { unmount } = render(
      <Modal titulo="Editar usuário" aoFechar={aoFechar}>
        <p>conteúdo</p>
      </Modal>,
    )

    expect(document.body.style.overflow).toBe('hidden')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(aoFechar).toHaveBeenCalled()

    unmount()
    // A página de trás tem de voltar a rolar: esquecer isso trava o scroll do
    // sistema inteiro depois de fechar um modal.
    expect(document.body.style.overflow).toBe('')
  })

  it('mostra título e conteúdo', () => {
    render(
      <Modal titulo="Editar usuário" descricao="uma descrição" aoFechar={vi.fn()}>
        <p>conteúdo</p>
      </Modal>,
    )

    expect(screen.getByRole('dialog', { name: 'Editar usuário' })).toBeInTheDocument()
    expect(screen.getByText('uma descrição')).toBeInTheDocument()
    expect(screen.getByText('conteúdo')).toBeInTheDocument()
  })
})
