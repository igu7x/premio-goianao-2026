import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi } from '../../api/cliente'
import type { Edicao, ListaHabilitados, Semeadura, UnidadeReconhecida } from '../../api/tipos'
import { Aviso, Carregando, EstadoVazio, EtiquetaSelo } from '../../componentes/Basicos'
import { useAvisos } from '../../componentes/Avisos'
import { Icone } from '../../componentes/Icone'
import { Disco } from '../../componentes/Selo'
import { PainelDaLista } from './PainelDaLista'

/**
 * Passo posterior ao cadastro (feature 008): para cada unidade reconhecida,
 * quem esta habilitado a emitir o certificado de servidor naquela edicao.
 *
 * A lista e um <b>snapshot</b> — nao a lotacao ao vivo. E isso que permite
 * reemitir uma edicao antiga com o quadro de pessoal da epoca.
 */
export function AbaServidores({ edicao }: { edicao: Edicao }) {
  const [unidades, setUnidades] = useState<UnidadeReconhecida[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [aberta, setAberta] = useState<UnidadeReconhecida | null>(null)
  const [semeando, setSemeando] = useState<number | null>(null)
  const avisos = useAvisos()

  const carregar = useCallback(async () => {
    try {
      setUnidades(
        await api.get<UnidadeReconhecida[]>(`/api/edicoes/${edicao.id}/unidades-reconhecidas`),
      )
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar as unidades.')
    }
  }, [edicao.id])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function semear(unidade: UnidadeReconhecida) {
    setSemeando(unidade.unidadeId)
    setErro(null)
    try {
      const dados = await api.post<Semeadura>(
        `/api/edicoes/${edicao.id}/unidades/${unidade.unidadeId}/servidores/semear`,
      )
      avisos.sucesso(
        `Lista de ${unidade.nome} atualizada`,
        [
          `${dados.retornadosPeloEgesp} no EGESP`,
          `${dados.incluidos} incluído(s)`,
          `${dados.jaExistentes} já constavam`,
          dados.preservadosRemovidos > 0
            ? `${dados.preservadosRemovidos} removido(s) preservados fora da lista`
            : null,
          dados.ignoradosSemEmail > 0
            ? `${dados.ignoradosSemEmail} sem e-mail no EGESP, não incluído(s)`
            : null,
          `total ativo: ${dados.totalAtivos}`,
        ]
          .filter(Boolean)
          .join(' · '),
      )
      await carregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao semear a lista.')
    } finally {
      setSemeando(null)
    }
  }

  if (!unidades) {
    return erro ? <Aviso tom="erro">{erro}</Aviso> : <Carregando />
  }

  return (
    <>
      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      <div className="bloco">
        <div className="bloco-cabecalho">
          <div>
            <h2 className="titulo-secao">Unidades reconhecidas</h2>
            <p className="apoio">
              Semeie a lista a partir do EGESP e ajuste o que for necessário.
            </p>
          </div>
        </div>

        {unidades.length === 0 ? (
          <EstadoVazio
            titulo="Nenhuma unidade reconhecida ainda"
            descricao="As unidades aparecem aqui assim que houver reconhecimentos cadastrados na aba anterior."
          />
        ) : (
          <div className="tabela-rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Unidade</th>
                  <th>Selos</th>
                  <th>Maior selo</th>
                  <th>Habilitados</th>
                  <th className="direita">Ações</th>
                </tr>
              </thead>
              <tbody>
                {unidades.map((unidade) => (
                  <tr key={unidade.unidadeId}>
                    <td>
                      <div
                        style={{
                          display: 'flex',
                          gap: 'var(--e3)',
                          alignItems: 'center',
                        }}
                      >
                        {unidade.maiorSelo && <Disco selo={unidade.maiorSelo} tamanho="m" />}
                        <div style={{ minWidth: 0 }}>
                          <div className="unidade-nome" style={{ fontSize: 16, margin: 0 }}>
                            {unidade.nome}
                          </div>
                          <div className="secundaria">
                            reconhecida por {unidade.magistrados} magistrado(s)
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 'var(--e3)', flexWrap: 'wrap' }}>
                        {unidade.selos.map((selo) => (
                          <EtiquetaSelo key={selo} selo={selo} />
                        ))}
                      </div>
                    </td>
                    <td>{unidade.maiorSelo && <EtiquetaSelo selo={unidade.maiorSelo} />}</td>
                    <td>
                      <span className="numero-pequeno">{unidade.servidoresHabilitados}</span>
                    </td>
                    <td>
                      <div className="acoes acoes-direita">
                        <button
                          type="button"
                          className="botao botao-neutro botao-pequeno"
                          disabled={semeando === unidade.unidadeId}
                          onClick={() => void semear(unidade)}
                        >
                          {semeando === unidade.unidadeId ? (
                            <span className="giro" />
                          ) : (
                            <Icone nome="semear" tamanho={15} />
                          )}
                          {semeando === unidade.unidadeId ? 'Semeando…' : 'Semear do EGESP'}
                        </button>
                        <button
                          type="button"
                          className="botao botao-texto botao-pequeno"
                          onClick={() => setAberta(unidade)}
                        >
                          Ver lista
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="bloco-rodape">
          Ressemear mescla: acrescenta quem faltava, mantém as inclusões manuais e não traz de
          volta quem foi removido de propósito.
        </div>
      </div>

      {aberta && (
        <PainelDaLista
          caminho={`/api/edicoes/${edicao.id}/unidades/${aberta.unidadeId}/servidores`}
          aoFechar={() => {
            setAberta(null)
            void carregar()
          }}
          carregarLista={() =>
            api.get<ListaHabilitados>(
              `/api/edicoes/${edicao.id}/unidades/${aberta.unidadeId}/servidores`,
            )
          }
        />
      )}
    </>
  )
}
