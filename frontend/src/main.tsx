import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './App'
import { ProvedorDeAvisos } from './componentes/Avisos'
import { ProvedorDeSessao } from './sessao/SessaoContexto'
import './estilos/base.css'
import './estilos/paginas.css'

const raiz = document.getElementById('raiz')
if (!raiz) {
  throw new Error('Elemento #raiz nao encontrado no index.html.')
}

createRoot(raiz).render(
  <StrictMode>
    <BrowserRouter>
      <ProvedorDeSessao>
        <ProvedorDeAvisos>
          <App />
        </ProvedorDeAvisos>
      </ProvedorDeSessao>
    </BrowserRouter>
  </StrictMode>,
)
