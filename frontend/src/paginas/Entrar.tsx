import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { api, ErroApi, urlDaApi } from '../api/cliente'
import brasao from '../assets/brasao-tjgo.png'
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

/** Portas de entrada que o ambiente abre, decididas pelo backend. */
interface SituacaoLogin {
  sso: boolean
  senha: boolean
  mock: boolean
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
 * Quais caminhos aparecem é decisão do <b>backend</b>, consultada em
 * <code>/api/auth/situacao</code> — não do build. A mesma imagem sobe em
 * homologação, onde também vale e-mail e senha, e em produção, onde vale só o
 * SSO. Não há nada a mudar no código no dia da virada.
 */
export function Entrar() {
  const { identidade, entrar, entrarComSenha, adotarToken, carregando } = useSessao()
  const navegar = useNavigate()

  const [situacao, setSituacao] = useState<SituacaoLogin | null>(null)
  const [semServidor, setSemServidor] = useState(false)
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

  /*
   * Quais portas de entrada este ambiente abre. Quem decide é o backend, não o
   * build: a mesma imagem sobe em homologação, onde vale o login por senha, e
   * em produção, onde vale só o SSO. Se a consulta falhar, assume-se o conjunto
   * mais restrito — errar para menos aqui só esconde um formulário; errar para
   * mais desenharia uma porta que o servidor recusa.
   */
  useEffect(() => {
    api
      .get<SituacaoLogin>('/api/auth/situacao')
      .then(setSituacao)
      .catch((e: unknown) => {
        // ErroApi = a API respondeu, com erro. Qualquer outra coisa (TypeError
        // "Failed to fetch") = a chamada nem chegou: certificado recusado,
        // rede, CORS. Sao problemas diferentes e merecem avisos diferentes.
        setSemServidor(!(e instanceof ErroApi))
        setSituacao({ sso: false, senha: false, mock: false })
      })
  }, [])

  // A lista mockada só existe onde o login mockado está ligado.
  useEffect(() => {
    if (!situacao?.mock) return
    api
      .get<UsuarioMock[]>('/api/auth/usuarios-mock')
      .then(setUsuarios)
      .catch((e: ErroApi) => setErro(e.message))
  }, [situacao])

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

  async function autenticar(email: string) {
    setEntrando(email)
    setErro(null)
    try {
      await entrar(email)
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível entrar.')
      setEntrando(null)
    }
  }

  return (
    <div className="entrada">
      <section className="entrada-apresentacao">
        <div className="entrada-marca">
          <img src={brasao} alt="" className="entrada-brasao" />
          <span className="marca-orgao">Tribunal de Justiça do Estado de Goiás</span>
        </div>

        <div className="entrada-chamada">
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
            e por aqui que entra quem tem cadastro de verdade.

            So aparece onde o ambiente aceita — homologacao e desenvolvimento.
            Em producao vale so o SSO, e desenhar um formulario que o servidor
            recusaria seria convidar a tentar.
          */}
          {situacao?.senha && (
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
          )}

          {!situacao && <Carregando />}

          {situacao?.sso && (
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

          {situacao?.mock && (
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
                      key={usuario.email}
                      className="usuario-opcao"
                      disabled={entrando !== null}
                      onClick={() => void autenticar(usuario.email)}
                    >
                      <span>
                        <span className="principal">{usuario.nome}</span>
                        <br />
                        <span className="secundaria mono">{usuario.email}</span>
                      </span>
                      <span className="papeis">
                        {usuario.papeis.map((papel) => (
                          <span key={papel} className={`papel-marca papel-${papel.toLowerCase()}`}>
                            {ROTULO_PAPEL[papel]}
                          </span>
                        ))}
                        {entrando === usuario.email ? (
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

          {/* Ambiente sem porta de entrada nenhuma: em vez de uma caixa vazia,
              diz o que houve. Acontece quando o SSO ainda não foi configurado e
              nada mais foi habilitado — ou quando a consulta de situação falhou
              e assumimos o conjunto mais restrito. */}
          {/* A chamada nem chegou à API. Em homologação o motivo mais comum é o
              certificado: a API tem endereço próprio, o navegador não confia
              nele e, como é uma chamada de fundo, não oferece o "continuar
              assim mesmo" — a falha é silenciosa. Abrir o endereço uma vez
              resolve naquele navegador. */}
          {semServidor && (
            <Aviso tom="erro" titulo="Não foi possível falar com o servidor">
              <p>
                O navegador não conseguiu chegar ao serviço do sistema. Se ele avisar que a
                conexão não é segura,{' '}
                <a href={urlDaApi('/api/auth/situacao')} target="_blank" rel="noreferrer">
                  abra este endereço
                </a>
                , aceite o aviso e depois recarregue esta página. Persistindo, procure a equipe
                responsável pelo sistema.
              </p>
            </Aviso>
          )}

          {situacao && !semServidor && !situacao.sso && !situacao.senha && !situacao.mock && (
            <Aviso tom="atencao" titulo="Nenhuma forma de acesso disponível">
              <p>
                Este ambiente ainda não tem o login corporativo configurado. Procure a equipe
                responsável pelo sistema.
              </p>
            </Aviso>
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
