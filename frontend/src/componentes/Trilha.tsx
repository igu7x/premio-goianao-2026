import { useNavigate } from 'react-router-dom'
import { Icone } from './Icone'

interface Props {
  /** Para onde o botão volta. Sem isto, volta no histórico do navegador. */
  para?: string
  /** Texto do botão de voltar — o nome do lugar de origem, não "Voltar". */
  rotulo: string
  /** Onde o usuário está agora; aparece depois do separador. */
  atual?: string
}

/**
 * Caminho de volta no topo das telas internas.
 *
 * O rótulo nomeia o destino ("Edições") em vez de dizer "Voltar": quem lê sabe
 * para onde vai antes de clicar. A seta recua no hover, reforçando a direção.
 *
 * Existe porque uma tela de detalhe sem saída explícita obriga o usuário a
 * recorrer ao botão do navegador — que numa aplicação de página única nem sempre
 * faz o que ele espera.
 */
export function Trilha({ para, rotulo, atual }: Props) {
  const navegar = useNavigate()

  return (
    <nav className="trilha" aria-label="Você está em">
      <button
        type="button"
        className="trilha-voltar"
        onClick={() => (para ? navegar(para) : navegar(-1))}
      >
        <Icone nome="voltar" tamanho={14} />
        {rotulo}
      </button>
      {atual && (
        <>
          <span className="trilha-separador" aria-hidden="true">
            /
          </span>
          <span className="trilha-atual">{atual}</span>
        </>
      )}
    </nav>
  )
}
