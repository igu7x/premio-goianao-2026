import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Carregando } from './componentes/Basicos'
import { Estrutura } from './componentes/Estrutura'
import { Edicoes } from './paginas/Edicoes'
import { EdicaoDetalhe } from './paginas/EdicaoDetalhe'
import { Entrar } from './paginas/Entrar'
import { Inicio } from './paginas/Inicio'
import { MeusCertificados } from './paginas/MeusCertificados'
import { MinhasUnidades } from './paginas/MinhasUnidades'
import { Verificar } from './paginas/Verificar'
import { useSessao } from './sessao/SessaoContexto'
import type { Papel } from './api/tipos'

/** Guarda de rota. A autorizacao de verdade e do backend; aqui so evitamos
 *  levar o usuario a uma tela que ele nao poderia usar (001/RNF-2). */
function Exige({ papeis, children }: { papeis: Papel[]; children: JSX.Element }) {
  const { identidade, tem } = useSessao()
  if (!identidade) {
    return <Navigate to="/entrar" replace />
  }
  return papeis.some(tem) ? children : <Navigate to="/" replace />
}

function Protegido({ children }: { children: JSX.Element }) {
  const { identidade, carregando } = useSessao()
  const local = useLocation()

  if (carregando) {
    return <Carregando texto="Verificando sua sessão…" />
  }
  if (!identidade) {
    return <Navigate to="/entrar" replace state={{ de: local.pathname }} />
  }
  return children
}

/**
 * A conferência é pública, mas quem já está logado não deve perder a casca ao
 * abri-la pelo menu: seria expulsá-lo do sistema para responder a uma pergunta
 * de dez segundos. Sem sessão — o caso do QR — a mesma tela abre inteira.
 */
function Conferencia() {
  const { identidade, carregando } = useSessao()

  if (carregando) {
    return <Carregando texto="Verificando sua sessão…" />
  }
  return identidade ? (
    <Estrutura>
      <Verificar embutido />
    </Estrutura>
  ) : (
    <Verificar />
  )
}

/** Cada perfil chega numa tela util: o admin no painel, os demais na emissao. */
function TelaInicial() {
  const { tem } = useSessao()
  return tem('ADMINISTRADOR') ? <Inicio /> : <Navigate to="/meus-certificados" replace />
}

export function App() {
  return (
    <Routes>
      <Route path="/entrar" element={<Entrar />} />

      {/* Conferencia publica: nao exige sessao, e o destino do QR. Com sessao,
          continua dentro da casca (ver Conferencia). */}
      <Route path="/verificar" element={<Conferencia />} />
      <Route path="/verificar/:codigo" element={<Conferencia />} />

      <Route
        element={
          <Protegido>
            <Estrutura />
          </Protegido>
        }
      >
        <Route path="/" element={<TelaInicial />} />
        <Route
          path="/edicoes"
          element={
            <Exige papeis={['ADMINISTRADOR']}>
              <Edicoes />
            </Exige>
          }
        />
        <Route
          path="/edicoes/:edicaoId"
          element={
            <Exige papeis={['ADMINISTRADOR']}>
              <EdicaoDetalhe />
            </Exige>
          }
        />
        <Route
          path="/meus-certificados"
          element={
            <Exige papeis={['MAGISTRADO', 'SERVIDOR']}>
              <MeusCertificados />
            </Exige>
          }
        />
        <Route
          path="/minhas-unidades"
          element={
            <Exige papeis={['MAGISTRADO']}>
              <MinhasUnidades />
            </Exige>
          }
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
