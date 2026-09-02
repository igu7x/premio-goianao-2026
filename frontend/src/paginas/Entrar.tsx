import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { api, ErroApi } from '../api/cliente'
import type { Papel, Selo, UsuarioMock } from '../api/tipos'
import { Aviso, Carregando } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import { Disco } from '../componentes/Selo'
import { useSessao } from '../sessao/SessaoContexto'

const ROTULO_PAPEL: Record<Papel, string> = {
  ADMINISTRADOR: 'Administrador',
  MAGISTRADO: 'Magistrado',
  SERVIDOR: 'Servidor',
}

const LIGAS: Selo[] = ['BRONZE', 'PRATA', 'OURO', 'DIAMANTE']

/**
 * Entrada no sistema.
 *
 * Enquanto o SSO do TJGO não está disponível, a tela lista as identidades de
 * teste que o provedor mockado oferece (001/RF-2). Quando o SSO real entrar,
 * esta lista dá lugar ao botão único de redirecionamento OIDC — o restante da
 * aplicação não muda, porque só consome a identidade já resolvida.
 */
export function Entrar() {
  const { identidade, entrar, carregando } = useSessao()
  const [usuarios, setUsuarios] = useState<UsuarioMock[] | null>(null)
  const [entrando, setEntrando] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<UsuarioMock[]>('/api/auth/usuarios-mock')
      .then(setUsuarios)
      .catch((e: ErroApi) => setErro(e.message))
  }, [])

  if (carregando) {
    return <Carregando texto="Verificando sua sessão…" />
  }
  if (identidade) {
    return <Navigate to="/" replace />
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
            Entrar com o login corporativo
          </h2>
          <p className="apoio" style={{ marginTop: 12 }}>
            A integração com o SSO do tribunal ainda está em homologação. Até lá, escolha uma das
            identidades de teste abaixo — seus dados e permissões são os mesmos que o SSO
            fornecerá.
          </p>

          {erro && (
            <div style={{ marginTop: 'var(--e4)' }}>
              <Aviso tom="erro">{erro}</Aviso>
            </div>
          )}

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

          <p className="apoio" style={{ marginTop: 'var(--e5)' }}>
            Precisa apenas conferir a autenticidade de um certificado?{' '}
            <a href="/verificar">Use a conferência pública</a>, sem login.
          </p>
        </div>
      </section>
    </div>
  )
}
