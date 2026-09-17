import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, ErroApi } from '../api/cliente'
import type { Edicao } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, SituacaoEdicao } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import { Trilha } from '../componentes/Trilha'
import { AbaLayouts } from './abas/AbaLayouts'
import { AbaMagistrados } from './abas/AbaMagistrados'
import { AbaServidores } from './abas/AbaServidores'

type Aba = 'layouts' | 'magistrados' | 'servidores'

const ABAS: Array<{ id: Aba; rotulo: string }> = [
  { id: 'layouts', rotulo: 'Layouts do certificado' },
  { id: 'magistrados', rotulo: 'Magistrados reconhecidos' },
  { id: 'servidores', rotulo: 'Servidores por unidade' },
]

/**
 * Configuração completa de uma edição, na ordem em que o trabalho acontece:
 * primeiro as artes, depois quem foi reconhecido e, por fim, quem pode emitir
 * em cada unidade.
 */
export function EdicaoDetalhe() {
  const { edicaoId } = useParams()
  const navegar = useNavigate()
  const avisos = useAvisos()
  const id = Number(edicaoId)

  const [edicao, setEdicao] = useState<Edicao | null>(null)
  const [outras, setOutras] = useState<Edicao[]>([])
  const [erro, setErro] = useState<string | null>(null)
  const [detalhes, setDetalhes] = useState<string[]>([])
  const [aba, setAba] = useState<Aba>('layouts')
  const [ocupado, setOcupado] = useState(false)

  const carregar = useCallback(async () => {
    try {
      setEdicao(await api.get<Edicao>(`/api/edicoes/${id}`))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar a edição.')
    }
  }, [id])

  useEffect(() => {
    void carregar()
    api
      .get<Edicao[]>('/api/edicoes')
      .then(setOutras)
      .catch(() => setOutras([]))
  }, [carregar])

  // Trocar de edição não deve zerar a aba: quem está conferindo layouts de 2025
  // normalmente quer ver os layouts de 2026 em seguida.
  useEffect(() => {
    setErro(null)
    setDetalhes([])
  }, [id])

  async function acao(caminho: 'publicar' | 'vigente') {
    setOcupado(true)
    setErro(null)
    setDetalhes([])
    try {
      await api.post(`/api/edicoes/${id}/${caminho}`)
      await carregar()
      avisos.sucesso(
        caminho === 'publicar' ? 'Edição publicada' : 'Edição definida como vigente',
        caminho === 'publicar'
          ? 'Os layouts foram travados e a emissão está liberada.'
          : 'Passa a ser a edição padrão na hora de emitir.',
      )
    } catch (e) {
      if (e instanceof ErroApi) {
        setErro(e.message)
        setDetalhes(e.detalhes)
      }
    } finally {
      setOcupado(false)
    }
  }

  if (!edicao) {
    return erro ? (
      <div className="pagina">
        <Aviso tom="erro">{erro}</Aviso>
      </div>
    ) : (
      <Carregando />
    )
  }

  return (
    <div className="pagina">
      <Trilha para="/edicoes" rotulo="Edições" atual={`Edição ${edicao.ano}`} />

      <header className="cabecalho-pagina">
        <div>
          <div className="titulo-com-troca">
            <h1 className="titulo-pagina">Edição {edicao.ano}</h1>

            {outras.length > 1 && (
              <div className="troca-edicao">
                <Icone nome="trocar" tamanho={14} />
                <select
                  aria-label="Ir para outra edição"
                  value={edicao.id}
                  onChange={(evento) => navegar(`/edicoes/${evento.target.value}`)}
                >
                  {outras.map((outra) => (
                    <option key={outra.id} value={outra.id}>
                      {outra.ano}
                      {outra.vigente ? ' · vigente' : outra.status === 'RASCUNHO' ? ' · rascunho' : ''}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
          <p>{edicao.descricao ?? 'Sem descrição.'}</p>
        </div>

        <div className="acoes">
          <SituacaoEdicao status={edicao.status} vigente={edicao.vigente} />
          {edicao.status === 'RASCUNHO' && (
            <button
              type="button"
              className="botao"
              disabled={ocupado}
              onClick={() => void acao('publicar')}
            >
              {ocupado && <span className="giro" />}
              Publicar edição
            </button>
          )}
          {edicao.status === 'PUBLICADA' && !edicao.vigente && (
            <button
              type="button"
              className="botao botao-neutro"
              disabled={ocupado}
              onClick={() => void acao('vigente')}
            >
              Tornar vigente
            </button>
          )}
        </div>
      </header>

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro" titulo={erro} detalhes={detalhes} />
        </div>
      )}

      {edicao.status === 'PUBLICADA' && (
        <div style={{ marginBottom: 'var(--e5)' }}>
          <Aviso tom="informacao">
            <Icone nome="confirmado" tamanho={16} />
            <span style={{ marginLeft: 6 }}>
              {edicao.vigente
                ? 'Edição publicada e vigente. Os layouts estão travados; ainda é possível incluir novos magistrados e reconhecimentos, mas não editar ou remover o que já existe.'
                : 'Edição publicada e não vigente: totalmente congelada. Continua emitível para reemissão, com os dados e layouts da época.'}
            </span>
          </Aviso>
        </div>
      )}

      <nav className="abas">
        {ABAS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={aba === item.id ? 'aba ativa' : 'aba'}
            onClick={() => setAba(item.id)}
          >
            {item.rotulo}
          </button>
        ))}
      </nav>

      {/* A chave força a remontagem: o conteúdo entra por baixo a cada troca. */}
      <div className="aba-conteudo" key={`${edicao.id}-${aba}`}>
        {aba === 'layouts' && <AbaLayouts edicao={edicao} />}
        {aba === 'magistrados' && <AbaMagistrados edicao={edicao} />}
        {aba === 'servidores' && <AbaServidores edicao={edicao} />}
      </div>
    </div>
  )
}
