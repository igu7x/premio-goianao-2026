import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErroApi } from '../api/cliente'
import type { Edicao } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import {
  Aviso,
  Carregando,
  EstadoVazio,
  Modal,
  SituacaoEdicao,
  formatarData,
} from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'

/**
 * Ciclo de vida das edições: criar, publicar e definir a vigente (feature 002).
 *
 * A lista é um livro de atas, não uma grade de dados: cada edição ocupa uma
 * faixa própria com o ano gravado em serifa, porque o ano <b>é</b> a identidade
 * da edição. A vigente se anuncia pelo filete dourado na lateral — o mesmo ouro
 * que o sistema reserva ao reconhecimento.
 */
export function Edicoes() {
  const [edicoes, setEdicoes] = useState<Edicao[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [detalhesDoErro, setDetalhesDoErro] = useState<string[]>([])
  const [criando, setCriando] = useState(false)
  const [ocupado, setOcupado] = useState<number | null>(null)
  const avisos = useAvisos()

  const carregar = useCallback(async () => {
    try {
      setEdicoes(await api.get<Edicao[]>('/api/edicoes'))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar as edições.')
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function executar(edicao: Edicao, acao: 'publicar' | 'vigente') {
    setOcupado(edicao.id)
    setErro(null)
    setDetalhesDoErro([])
    try {
      await api.post(`/api/edicoes/${edicao.id}/${acao}`)
      await carregar()
      avisos.sucesso(
        acao === 'publicar'
          ? `Edição ${edicao.ano} publicada`
          : `Edição ${edicao.ano} agora é a vigente`,
        acao === 'publicar'
          ? 'Os layouts foram travados e a emissão está liberada.'
          : 'É o contexto padrão de quem for emitir um certificado.',
      )
    } catch (e) {
      if (e instanceof ErroApi) {
        setErro(e.message)
        setDetalhesDoErro(e.detalhes)
      }
    } finally {
      setOcupado(null)
    }
  }

  return (
    <div className="pagina">
      <header className="cabecalho-pagina">
        <div>
          <span className="rotulo">Cadastro</span>
          <h1 className="titulo-pagina" style={{ marginTop: 4 }}>
            Edições do prêmio
          </h1>
          <p>
            Cada edição guarda seus próprios layouts, reconhecidos e listas de servidores. A
            edição vigente é o contexto padrão da emissão; as anteriores continuam disponíveis
            para reemissão com os dados da época.
          </p>
        </div>
        <button type="button" className="botao" onClick={() => setCriando(true)}>
          <Icone nome="mais" tamanho={16} />
          Nova edição
        </button>
      </header>

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro" titulo={erro} detalhes={detalhesDoErro} />
        </div>
      )}

      {!edicoes ? (
        !erro && <Carregando />
      ) : edicoes.length === 0 ? (
        <div className="bloco">
          <EstadoVazio
            titulo="Nenhuma edição cadastrada"
            descricao="A edição é identificada pelo ano e nasce em rascunho. Publique-a depois de configurar os oito layouts."
            acao={
              <button type="button" className="botao" onClick={() => setCriando(true)}>
                <Icone nome="mais" tamanho={16} />
                Criar edição
              </button>
            }
          />
        </div>
      ) : (
        <>
          <div className="registros">
            {edicoes.map((edicao, indice) => (
              <article
                key={edicao.id}
                className={edicao.vigente ? 'registro registro-vigente' : 'registro'}
                style={{ animationDelay: `${indice * 45}ms` }}
              >
                <div className="registro-marca">
                  <Link className="registro-ano" to={`/edicoes/${edicao.id}`}>
                    {edicao.ano}
                  </Link>
                  <SituacaoEdicao status={edicao.status} vigente={edicao.vigente} />
                </div>

                <div className="registro-corpo">
                  <p>
                    {edicao.descricao ??
                      (edicao.status === 'RASCUNHO'
                        ? 'Em preparação. Nada é emitido enquanto não for publicada.'
                        : 'Publicada — emitível com os dados e layouts congelados na publicação.')}
                  </p>
                  <div className="registro-meta">criada em {formatarData(edicao.criadoEm)}</div>
                </div>

                <div className="registro-acoes">
                  {edicao.status === 'RASCUNHO' && (
                    <button
                      type="button"
                      className="botao botao-neutro botao-pequeno"
                      disabled={ocupado === edicao.id}
                      onClick={() => void executar(edicao, 'publicar')}
                    >
                      {ocupado === edicao.id && <span className="giro" />}
                      Publicar
                    </button>
                  )}
                  {edicao.status === 'PUBLICADA' && !edicao.vigente && (
                    <button
                      type="button"
                      className="botao botao-neutro botao-pequeno"
                      disabled={ocupado === edicao.id}
                      onClick={() => void executar(edicao, 'vigente')}
                    >
                      {ocupado === edicao.id && <span className="giro" />}
                      Tornar vigente
                    </button>
                  )}
                  <Link className="botao botao-pequeno" to={`/edicoes/${edicao.id}`}>
                    Configurar
                    <Icone nome="seta" tamanho={14} />
                  </Link>
                </div>
              </article>
            ))}
          </div>

          <p className="apoio" style={{ marginTop: 'var(--e5)' }}>
            Publicar exige as 8 combinações de layout configuradas — assim nenhum reconhecimento
            incluído depois fica sem certificado para emitir.
          </p>
        </>
      )}

      {criando && (
        <ModalNovaEdicao
          aoFechar={() => setCriando(false)}
          aoCriar={async () => {
            setCriando(false)
            await carregar()
          }}
        />
      )}
    </div>
  )
}

function ModalNovaEdicao({
  aoFechar,
  aoCriar,
}: {
  aoFechar: () => void
  aoCriar: () => Promise<void>
}) {
  const [ano, setAno] = useState(String(new Date().getFullYear()))
  const [descricao, setDescricao] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  async function salvar() {
    setSalvando(true)
    setErro(null)
    try {
      await api.post('/api/edicoes', { ano: Number(ano), descricao: descricao || null })
      await aoCriar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao criar a edição.')
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo="Nova edição"
      descricao="A edição nasce em rascunho: nada é emitido até a publicação."
      aoFechar={aoFechar}
      rodape={
        <>
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            Cancelar
          </button>
          <button type="button" className="botao" disabled={salvando} onClick={() => void salvar()}>
            {salvando && <span className="giro" />}
            {salvando ? 'Criando…' : 'Criar edição'}
          </button>
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      <div className="campo">
        <label htmlFor="ano">Ano</label>
        <input
          id="ano"
          type="number"
          min={2000}
          max={2100}
          value={ano}
          onChange={(evento) => setAno(evento.target.value)}
        />
        <span className="campo-dica">Identifica a edição e não pode se repetir.</span>
      </div>

      <div className="campo">
        <label htmlFor="descricao">Descrição (opcional)</label>
        <textarea
          id="descricao"
          rows={3}
          maxLength={500}
          value={descricao}
          onChange={(evento) => setDescricao(evento.target.value)}
        />
      </div>
    </Modal>
  )
}
