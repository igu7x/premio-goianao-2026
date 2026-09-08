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
  /** Entrada por e-mail e senha, do cadastro próprio de usuários. */
  entrarComSenha: (email: string, senha: string) => Promise<void>
  /** Adota um token já emitido — o caminho de volta do SSO. */
  adotarToken: (token: string) => Promise<void>
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

  const entrarComSenha = useCallback(async (email: string, senha: string) => {
    const sessao = await api.post<Sessao>('/api/auth/login-senha', { email, senha })
    guardarToken(sessao.token)
    setIdentidade({ cpf: sessao.cpf, nome: sessao.nome, papeis: sessao.papeis })
  }, [])

  const entrar = useCallback(async (credencial: string) => {
    const sessao = await api.post<Sessao>('/api/auth/login', { credencial })
    guardarToken(sessao.token)
    setIdentidade({ cpf: sessao.cpf, nome: sessao.nome, papeis: sessao.papeis })
  }, [])

  /**
   * Entrada pelo SSO: o backend já validou o id_token do Keycloak e emitiu o
   * nosso JWT, que chega no fragmento da URL. Aqui só guardamos e perguntamos
   * ao /me quem é — os papéis vêm do servidor, nunca do token.
   */
  const adotarToken = useCallback(async (token: string) => {
    guardarToken(token)
    try {
      setIdentidade(await api.get<Identidade>('/api/auth/me'))
    } catch (e) {
      descartarToken()
      throw e
    }
  }, [])

  /**
   * Encerrar só do nosso lado não basta quando há SSO: a sessão do Keycloak
   * continua de pé no navegador e o próximo "entrar" reautentica sem pedir
   * nada — o usuário clica em Sair e volta logado, o que é pior do que não ter
   * botão. Por isso, com SSO ativo, o navegador termina no logout do provedor.
   */
  const sair = useCallback(async () => {
    let urlDoProvedor: string | null = null
    try {
      urlDoProvedor = (await api.get<{ url: string | null }>('/api/auth/sso/logout-url')).url
    } catch {
      /* sem SSO ou indisponível: encerra só localmente */
    }

    try {
      await api.post('/api/auth/logout')
    } catch {
      /* encerrar localmente e o que importa */
    }
    descartarToken()
    setIdentidade(null)

    if (urlDoProvedor) {
      window.location.assign(urlDoProvedor)
    }
  }, [])

  const valor = useMemo<ContextoSessao>(
    () => ({
      identidade,
      carregando,
      entrar,
      entrarComSenha,
      adotarToken,
      sair,
      tem: (papel) => identidade?.papeis.includes(papel) ?? false,
    }),
    [identidade, carregando, entrar, entrarComSenha, adotarToken, sair],
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
