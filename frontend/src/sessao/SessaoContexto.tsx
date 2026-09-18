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
  /**
   * Passa a agir sobre outra edição. Cada edição tem a sua base, então trocar
   * é trocar de sessão: o servidor confere que a pessoa existe lá e devolve
   * um token novo, com os papéis daquela edição.
   */
  trocarEdicao: (edicaoId: number) => Promise<void>
  /**
   * Pergunta de novo ao servidor em quais edições a pessoa existe. A lista vem
   * no login; uma edição criada depois não estaria nela até a próxima carga.
   */
  recarregarIdentidade: () => Promise<void>
  tem: (papel: Papel) => boolean
}

function identidadeDa(sessao: Sessao): Identidade {
  return {
    email: sessao.email,
    nome: sessao.nome,
    papeis: sessao.papeis,
    edicao: sessao.edicao,
    edicoesDisponiveis: sessao.edicoesDisponiveis,
  }
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
    setIdentidade(identidadeDa(sessao))
  }, [])

  const entrar = useCallback(async (credencial: string) => {
    const sessao = await api.post<Sessao>('/api/auth/login', { credencial })
    guardarToken(sessao.token)
    setIdentidade(identidadeDa(sessao))
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
   * Recarrega a página depois de trocar, em vez de só atualizar o estado:
   * cada tela aberta guardou dados da base anterior, e recarregar é o único
   * jeito de garantir que nenhuma continue mostrando o ano errado. O caminho
   * atual é mantido — quem estava em Usuários vê os usuários da outra edição.
   */
  const recarregarIdentidade = useCallback(async () => {
    setIdentidade(await api.get<Identidade>('/api/auth/me'))
  }, [])

  const trocarEdicao = useCallback(async (edicaoId: number) => {
    const sessao = await api.post<Sessao>(`/api/auth/edicao/${edicaoId}`)
    guardarToken(sessao.token)
    window.location.assign(window.location.pathname + window.location.search)
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
      trocarEdicao,
      recarregarIdentidade,
      tem: (papel) => identidade?.papeis.includes(papel) ?? false,
    }),
    [
      identidade,
      carregando,
      entrar,
      entrarComSenha,
      adotarToken,
      sair,
      trocarEdicao,
      recarregarIdentidade,
    ],
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
