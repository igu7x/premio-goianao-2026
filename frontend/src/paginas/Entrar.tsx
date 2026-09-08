import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { api, ErroApi, urlDaApi } from '../api/cliente'
import type { Papel, Selo, UsuarioMock } from '../api/tipos'
import { Aviso, Carregando } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import { Disco } from '../componentes/Selo'
import { useSessao } from '../sessao/SessaoContexto'

const ROTULO_PAPEL: Record<Papel, string> = {
  SUPERADMIN: 'Superadmin',
  ADMINISTRADOR: 'Administrador',
  MAGISTRADO: 'Magistrado',
  SERVIDOR: 'Servidor',
}

const LIGAS: Selo[] = ['BRONZE', 'PRATA', 'OURO', 'DIAMANTE']

interface SituacaoSso {
  habilitado: boolean
}

/** O retorno do SSO chega no fragmento: /entrar#token=...&destino=... */
function lerFragmento(): { token?: string; erro?: string; destino?: string } {
  const bruto = window.location.hash.replace(/^#/, '')
  if (!bruto) return {}
  const partes = new URLSearchParams(bruto)
  return {
    token: partes.get('token') ?? undefined,
    erro: partes.get('erro') ?? undefined,
    destino: partes.get('destino') ?? undefined,
  }
}

/**
 * Entrada no sistema.
 *
 * Dois caminhos convivem. O corporativo (Keycloak do TJGO) é um redirecionamento
 * conduzido pelo backend — o navegador nunca vê o segredo do client — e o token
 * volta no <b>fragmento</b> da URL, que não é enviado ao servidor e por isso não
 * aparece em log de proxy nem no cabeçalho Referer.
 *
 * O segundo é a lista de identidades de teste, que existe enquanto o client do
 * Keycloak não é criado. Ela some sozinha quando o SSO é configurado: o backend
 * informa a situação em <code>/api/auth/sso/situacao</code>, e não há nada a
 * mudar no código no dia da virada.
 */
export function Entrar() {
  const { identidade, entrar, entrarComSenha, adotarToken, carregando } = useSessao()
  const navegar = useNavigate()

  const [sso, setSso] = useState<SituacaoSso | null>(null)
  const [usuarios, setUsuarios] = useState<UsuarioMock[] | null>(null)
  const [entrando, setEntrando] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [entrandoComSenha, setEntrandoComSenha] = useState(false)
  const [voltandoDoSso, setVoltandoDoSso] = useState(() => Boolean(lerFragmento().token))

  // Retorno do Keycloak. O fragmento é limpo antes de qualquer outra coisa,
  // para o token não ficar na barra de endereços nem no histórico.
  useEffect(() => {
    const { token, erro: erroDoSso, destino } = lerFragmento()
    if (!token && !erroDoSso) return

    window.history.replaceState(null, '', window.location.pathname)

    if (erroDoSso) {
      setErro(erroDoSso)
      setVoltandoDoSso(false)
      return
    }
    if (token) {
      adotarToken(token)
        .then(() => navegar(destino && destino.startsWith('/') ? destino : '/', { replace: true }))
        .catch(() => {
          setErro('O login corporativo respondeu, mas a sessão não pôde ser aberta.')
          setVoltandoDoSso(false)
        })
    }
  }, [adotarToken, navegar])

  useEffect(() => {
    api
      .get<SituacaoSso>('/api/auth/sso/situacao')
      .then(setSso)
      .catch(() => setSso({ habilitado: false }))
  }, [])

  // A lista mockada só é buscada quando o SSO não está disponível.
  useEffect(() => {
    if (!sso || sso.habilitado) return
    api
      .get<UsuarioMock[]>('/api/auth/usuarios-mock')
      .then(setUsuarios)
      .catch((e: ErroApi) => setErro(e.message))
  }, [sso])

  if (carregando || voltandoDoSso) {
    return <Carregando texto="Verificando sua sessão…" />
  }
  if (identidade) {
    return <Navigate to="/" replace />
  }

  async function autenticarComSenha() {
    setEntrandoComSenha(true)
    setErro(null)
    try {
      await entrarComSenha(email.trim(), senha)
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível entrar.')
      setEntrandoComSenha(false)
    }
  }

  async function autenticar(cpf: string) {
    setEntrando(cpf)
    setErro(null)
    try {
      await entrar(cpf)
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível entrar.')
      setEntrando(null)
    }
  }

  return (
    <div className="entrada">
      <section className="entrada-apresentacao">
        <div>
          <div className="entrada-marca">
            <span className="marca-orgao">Tribunal de Justiça do Estado de Goiás</span>
          </div>
          <h1>
            Prêmio
            <br />
            Goianão
          </h1>
          <p>
            Emissão dos certificados de reconhecimento das unidades judiciárias premiadas.
            Magistrados e servidores emitem o próprio certificado, com o selo conquistado pela
            unidade na edição.
          </p>
        </div>

        <div className="entrada-selos">
          {LIGAS.map((selo) => (
            <div className="entrada-selo" key={selo}>
              <Disco selo={selo} tamanho="g" />
              {selo}
            </div>
          ))}
        </div>
      </section>

      <section className="entrada-formulario">
        <div className="entrada-caixa">
          <span className="rotulo">Acesso</span>
          <h2 className="titulo-pagina" style={{ marginTop: 8 }}>
            Entrar
          </h2>

          {erro && (
            <div style={{ marginTop: 'var(--e4)' }}>
              <Aviso tom="erro">{erro}</Aviso>
            </div>
          )}

          {/*
            E-mail e senha vem primeiro, e nao atras das identidades de teste:
            e por aqui que entra quem tem cadastro de verdade. A lista mockada
            existe para percorrer o sistema sem cadastrar ninguem.
          */}
          <form
            className="entrada-credenciais"
            onSubmit={(evento) => {
              evento.preventDefault()
              void autenticarComSenha()
            }}
          >
            <div className="campo">
              <label htmlFor="email">E-mail</label>
              <input
                id="email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(evento) => setEmail(evento.target.value)}
              />
            </div>
            <div className="campo">
              <label htmlFor="senha">Senha</label>
              <input
                id="senha"
                type="password"
                autoComplete="current-password"
                value={senha}
                onChange={(evento) => setSenha(evento.target.value)}
              />
            </div>
            <button
              type="submit"
              className="botao"
              disabled={entrandoComSenha || !email.trim() || !senha}
              style={{ width: '100%', justifyContent: 'center' }}
            >
              {entrandoComSenha && <span className="giro" />}
              {entrandoComSenha ? 'Entrando…' : 'Entrar'}
            </button>
          </form>

          {!sso && <Carregando />}

          {sso?.habilitado && (
            <>
              <p className="apoio" style={{ marginTop: 12 }}>
                Use as mesmas credenciais dos demais sistemas do tribunal.
              </p>
              {/* Navegação de página inteira, não fetch: o fluxo é uma
                  sequência de redirecionamentos até o Keycloak e de volta. */}
              <a
                className="botao"
                style={{ marginTop: 'var(--e5)', width: '100%', justifyContent: 'center' }}
                href={urlDaApi('/api/auth/sso/login')}
              >
                <Icone nome="sair" tamanho={16} />
                Entrar com o login do TJGO
              </a>
            </>
          )}

          {sso && !sso.habilitado && (
            <>
              <div className="regua" style={{ marginTop: 'var(--e6)' }}>
                <span>ou entre como</span>
              </div>
              <p className="apoio" style={{ marginTop: 12 }}>
                A integração com o SSO do tribunal ainda está em homologação. Até lá, escolha uma
                das identidades de teste abaixo — seus dados e permissões são os mesmos que o SSO
                fornecerá.
              </p>

              {!usuarios && !erro && <Carregando texto="Carregando identidades de teste…" />}

              {usuarios && (
                <div className="lista-usuarios">
                  {usuarios.map((usuario) => (
                    <button
                      type="button"
                      key={usuario.cpf}
                      className="usuario-opcao"
                      disabled={entrando !== null}
                      onClick={() => void autenticar(usuario.cpf)}
                    >
                      <span>
                        <span className="principal">{usuario.nome}</span>
                        <br />
                        <span className="secundaria mono">{usuario.cpfFormatado}</span>
                      </span>
                      <span className="papeis">
                        {usuario.papeis.map((papel) => (
                          <span key={papel} className={`papel-marca papel-${papel.toLowerCase()}`}>
                            {ROTULO_PAPEL[papel]}
                          </span>
                        ))}
                        {entrando === usuario.cpf ? (
                          <span className="giro" />
                        ) : (
                          <Icone nome="seta" tamanho={16} />
                        )}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </>
          )}

          <p className="apoio" style={{ marginTop: 'var(--e5)' }}>
            Precisa apenas conferir a autenticidade de um certificado?{' '}
            <a href="/verificar">Use a conferência pública</a>, sem login.
          </p>
        </div>
      </section>
    </div>
  )
}
