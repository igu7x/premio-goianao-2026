import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { Icone } from './Icone'

interface AvisoFlutuante {
  id: number
  tom: 'sucesso' | 'erro'
  titulo: string
  texto?: string
  saindo?: boolean
}

interface ContextoAvisos {
  sucesso: (titulo: string, texto?: string) => void
  erro: (titulo: string, texto?: string) => void
}

const Contexto = createContext<ContextoAvisos | null>(null)

const DURACAO = 6000

/**
 * Avisos flutuantes para confirmar o que acabou de acontecer.
 *
 * Servem ao retorno de ações pontuais — um certificado emitido, uma lista
 * semeada — que antes empurravam o conteúdo da página para baixo com um bloco
 * fixo. O aviso aparece no canto, some sozinho e não desloca nada.
 *
 * Erro de formulário continua inline, junto do campo: ali o usuário precisa do
 * texto enquanto corrige, e um aviso que some seria pior.
 */
export function ProvedorDeAvisos({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<AvisoFlutuante[]>([])

  const remover = useCallback((id: number) => {
    // Marca como saindo para a animação rodar antes de tirar do DOM.
    setAvisos((atual) => atual.map((a) => (a.id === id ? { ...a, saindo: true } : a)))
    setTimeout(() => setAvisos((atual) => atual.filter((a) => a.id !== id)), 220)
  }, [])

  const acrescentar = useCallback(
    (tom: AvisoFlutuante['tom'], titulo: string, texto?: string) => {
      const id = Date.now() + Math.random()
      setAvisos((atual) => [...atual, { id, tom, titulo, texto }])
      setTimeout(() => remover(id), DURACAO)
    },
    [remover],
  )

  const valor = useMemo<ContextoAvisos>(
    () => ({
      sucesso: (titulo, texto) => acrescentar('sucesso', titulo, texto),
      erro: (titulo, texto) => acrescentar('erro', titulo, texto),
    }),
    [acrescentar],
  )

  return (
    <Contexto.Provider value={valor}>
      {children}
      <div className="pilha-avisos" role="status" aria-live="polite">
        {avisos.map((aviso) => (
          <div
            key={aviso.id}
            className={[
              'aviso-flutuante',
              aviso.tom === 'erro' ? 'aviso-flutuante-erro' : '',
              aviso.saindo ? 'saindo' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => remover(aviso.id)}
          >
            <Icone nome={aviso.tom === 'erro' ? 'atencao' : 'confirmado'} tamanho={17} />
            <div className="aviso-flutuante-texto">
              <strong>{aviso.titulo}</strong>
              {aviso.texto && <span>{aviso.texto}</span>}
            </div>
          </div>
        ))}
      </div>
    </Contexto.Provider>
  )
}

export function useAvisos(): ContextoAvisos {
  const contexto = useContext(Contexto)
  if (!contexto) {
    throw new Error('useAvisos precisa estar dentro de ProvedorDeAvisos.')
  }
  return contexto
}
