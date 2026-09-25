import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useMatch } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Edicao } from '../api/tipos'
import { useAmbiente } from '../sessao/Ambiente'
import { useSessao } from '../sessao/SessaoContexto'
import { Icone, type NomeDeIcone } from './Icone'
import { SeletorDeEdicao } from './SeletorDeEdicao'
import brasao from '../assets/brasao-tjgo.png'

interface ItemDeMenu {
  para: string
  rotulo: string
  icone: NomeDeIcone
}

interface GrupoDeMenu {
  titulo: string
  itens: ItemDeMenu[]
}

/** Rótulo do topo: diz em que parte do sistema a pessoa está. */
function contexto(caminho: string): string {
  if (
    caminho.startsWith('/usuarios') ||
    caminho.startsWith('/unidades') ||
    caminho.startsWith('/sincronizacao')
  ) {
    return 'Superadministração'
  }
  if (caminho.startsWith('/verificar')) return 'Conferência pública'
  if (caminho.startsWith('/edicoes')) return 'Configuração do prêmio'
  if (caminho.startsWith('/minhas-unidades')) return 'Servidores da unidade'
  if (caminho.startsWith('/meus-certificados')) return 'Emissão de certificados'
  return 'Visão geral'
}

/**
 * Casca da aplicacao: navegacao lateral fixa e faixa superior com a identidade.
 *
 * O menu e a <b>uniao</b> dos papeis do usuario (001/RF-3): quem acumula
 * administrador e magistrado ve os dois grupos ao mesmo tempo, sem precisar
 * trocar de contexto.
 */
export function Estrutura({ children }: { children?: React.ReactNode }) {
  const { identidade, sair, tem } = useSessao()
  const local = useLocation()
  // Quando o usuário está dentro de uma edição, a lateral mostra isso: sem essa
  // pista, "Edições do prêmio" fica aceso e nada diz em qual delas se está.
  const dentroDaEdicao = useMatch('/edicoes/:edicaoId')
  const [edicaoAberta, setEdicaoAberta] = useState<Edicao | null>(null)
  // O rodapé só avisa o que é verdade neste ambiente. Em produção, com SSO e
  // RH de verdade, não há aviso nenhum — e era justamente ali que o texto fixo
  // dizia "ambiente de homologação, dados de RH mockados".
  const ambiente = useAmbiente()

  useEffect(() => {
    const id = dentroDaEdicao?.params.edicaoId
    if (!id) {
      setEdicaoAberta(null)
      return
    }
    let ativo = true
    api
      .get<Edicao>(`/api/edicoes/${id}`)
      .then((e) => ativo && setEdicaoAberta(e))
      .catch(() => ativo && setEdicaoAberta(null))
    return () => {
      ativo = false
    }
  }, [dentroDaEdicao?.params.edicaoId])

  const grupos: GrupoDeMenu[] = []

  // Superadministração vem primeiro: é de onde se concede acesso a tudo mais.
  if (tem('SUPERADMIN')) {
    grupos.push({
      titulo: 'Superadministração',
      itens: [
        { para: '/usuarios', rotulo: 'Usuários do sistema', icone: 'equipe' },
        { para: '/sincronizacao', rotulo: 'Sincronização de Unidades', icone: 'trocar' },
      ],
    })
  }

  if (tem('ADMINISTRADOR')) {
    grupos.push({
      titulo: 'Administração',
      itens: [
        { para: '/', rotulo: 'Visão geral', icone: 'painel' },
        { para: '/edicoes', rotulo: 'Edições do prêmio', icone: 'edicoes' },
      ],
    })
  }

  if (tem('MAGISTRADO') || tem('SERVIDOR')) {
    const itens: ItemDeMenu[] = [
      { para: '/meus-certificados', rotulo: 'Meus certificados', icone: 'certificado' },
    ]
    if (tem('MAGISTRADO')) {
      itens.push({ para: '/minhas-unidades', rotulo: 'Servidores da unidade', icone: 'equipe' })
    }
    grupos.push({ titulo: 'Reconhecimento', itens })
  }

  grupos.push({
    titulo: 'Público',
    itens: [{ para: '/verificar', rotulo: 'Conferir certificado', icone: 'verificar' }],
  })

  const iniciais = (identidade?.nome ?? '?')
    .split(' ')
    .filter((parte) => parte.length > 2)
    .slice(0, 2)
    .map((parte) => parte[0])
    .join('')
    .toUpperCase()

  return (
    <div className="aplicacao">
      <aside className="lateral">
        <NavLink to="/" className="marca">
          {/* alt vazio: o nome do tribunal já vem logo abaixo, em texto. */}
          <img src={brasao} alt="" className="marca-brasao" />
          <span className="marca-orgao">Tribunal de Justiça de Goiás</span>
          <span className="marca-nome">Prêmio Goianão</span>
        </NavLink>

        <nav className="navegacao">
          {grupos.map((grupo) => (
            <div className="navegacao-grupo" key={grupo.titulo}>
              <div className="navegacao-titulo">{grupo.titulo}</div>
              {grupo.itens.map((item) => (
                <NavLink
                  key={item.para}
                  to={item.para}
                  end={item.para === '/'}
                  className={({ isActive }) =>
                    isActive || (item.para !== '/' && local.pathname.startsWith(item.para))
                      ? 'navegacao-item ativo'
                      : 'navegacao-item'
                  }
                >
                  <Icone nome={item.icone} tamanho={17} />
                  {item.rotulo}
                </NavLink>
              ))}

              {grupo.titulo === 'Administração' && edicaoAberta && (
                <div className="navegacao-sub">
                  <span className="navegacao-sub-item ativo" aria-current="page">
                    Edição {edicaoAberta.ano}
                    {edicaoAberta.vigente && <em>vigente</em>}
                  </span>
                </div>
              )}
            </div>
          ))}
        </nav>

        {ambiente && (ambiente.mock || ambiente.senha || !ambiente.rhReal) && (
          <div className="lateral-rodape">
            Ambiente de teste
            <br />
            {[
              ambiente.mock ? 'login de teste' : null,
              ambiente.senha ? 'login por senha' : null,
              !ambiente.rhReal ? 'dados de RH mockados' : null,
            ]
              .filter(Boolean)
              .join(' · ')}
          </div>
        )}
      </aside>

      <div className="painel">
        <header className="topo">
          <div className="topo-contexto">
            <span className="rotulo">{contexto(local.pathname)}</span>
            {/* Cada edição tem a sua base: a edição da sessão fica sempre à
                vista, porque tudo abaixo dela é daquela edição (011/RF-4). */}
            <SeletorDeEdicao />
          </div>

          <div className="usuario">
            <div className="usuario-dados">
              <div className="usuario-nome">{identidade?.nome}</div>
              <div className="usuario-papeis">
                {identidade?.papeis.map((papel) => papel.toLowerCase()).join(' + ')}
              </div>
            </div>
            <span className="inicial" aria-hidden="true">
              {iniciais}
            </span>
            <button
              type="button"
              className="botao botao-neutro botao-pequeno"
              onClick={() => void sair()}
            >
              <Icone nome="sair" tamanho={15} />
              Sair
            </button>
          </div>
        </header>

        {/* Quase toda tela chega pelo Outlet; a conferência pública é passada
            como filha, porque a mesma página também roda fora da casca. */}
        <main>{children ?? <Outlet />}</main>
      </div>
    </div>
  )
}
