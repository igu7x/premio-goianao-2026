import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  api,
  descartarToken,
  EVENTO_SESSAO_EXPIRADA,
  guardarToken,
  lerToken,
} from '../api/cliente'
import type { Identidade, Papel, Sessao } from '../api/tipos'

interface ContextoSessao {
  identidade: Identidade | null
  carregando: boolean
  entrar: (credencial: string) => Promise<void>
  sair: () => Promise<void>
  tem: (papel: Papel) => boolean
}

const Contexto = createContext<ContextoSessao | null>(null)

export function ProvedorDeSessao({ children }: { children: ReactNode }) {
  const [identidade, setIdentidade] = useState<Identidade | null>(null)
  const [carregando, setCarregando] = useState(true)

  // Ao abrir o app com um token guardado, quem decide se ele ainda vale e o
  // backend: /auth/me devolve os papeis recalculados, nunca os do token antigo.
  useEffect(() => {
    let ativo = true
    if (!lerToken()) {
      setCarregando(false)
      return
    }
    api
      .get<Identidade>('/api/auth/me')
      .then((dados) => ativo && setIdentidade(dados))
      .catch(() => descartarToken())
      .finally(() => ativo && setCarregando(false))
    return () => {
      ativo = false
    }
  }, [])

  useEffect(() => {
    const aoExpirar = () => setIdentidade(null)
    window.addEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar)
    return () => window.removeEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar)
  }, [])

  const entrar = useCallback(async (credencial: string) => {
    const sessao = await api.post<Sessao>('/api/auth/login', { credencial })
    guardarToken(sessao.token)
    setIdentidade({ cpf: sessao.cpf, nome: sessao.nome, papeis: sessao.papeis })
  }, [])

  const sair = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } catch {
      /* encerrar localmente e o que importa */
    }
    descartarToken()
    setIdentidade(null)
  }, [])

  const valor = useMemo<ContextoSessao>(
    () => ({
      identidade,
      carregando,
      entrar,
      sair,
      tem: (papel) => identidade?.papeis.includes(papel) ?? false,
    }),
    [identidade, carregando, entrar, sair],
  )

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

export function useSessao(): ContextoSessao {
  const contexto = useContext(Contexto)
  if (!contexto) {
    throw new Error('useSessao precisa estar dentro de ProvedorDeSessao.')
  }
  return contexto
}
