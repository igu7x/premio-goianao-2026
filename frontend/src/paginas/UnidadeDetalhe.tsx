import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, ErroApi } from '../api/cliente'
import type { LotacaoAplicada, LotadoDoRh, Unidade } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio, Modal } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'

/**
 * Página de uma unidade — exclusiva do superadministrador.
 *
 * Existe para responder, num lugar só, a pergunta que a tela de sincronização
 * não respondia sem uma edição escolhida: <b>quem trabalha aqui, e quem desses
 * já existe no sistema?</b> Lotação não é lista de habilitados: a primeira é o
 * que o RH diz hoje, a segunda é um retrato datado de quem pode emitir numa
 * edição (princípio 3b). Cadastrar aqui dá acesso; habilitar continua sendo ato
 * da edição.
 */
export function UnidadeDetalhe() {
  const { id } = useParams<{ id: string }>()
  const navegar = useNavigate()
  const [unidade, setUnidade] = useState<Unidade | null>(null)
  const [lotados, setLotados] = useState<LotadoDoRh[] | null>(null)
  const [puxando, setPuxando] = useState(false)
  const [cadastrando, setCadastrando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const avisos = useAvisos()

  const carregarUnidade = useCallback(async () => {
    try {
      setUnidade(await api.get<Unidade>(`/api/unidades/${id}`))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar a unidade.')
    }
  }, [id])

  useEffect(() => {
    void carregarUnidade()
  }, [carregarUnidade])

  /** Leitura pura: o RH é consultado, nada é gravado. */
  const puxarDoRh = useCallback(async () => {
    setPuxando(true)
    setErro(null)
    try {
      setLotados(await api.get<LotadoDoRh[]>(`/api/sincronizacao/unidades/${id}/lotados`))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao consultar o RH.')
    } finally {
      setPuxando(false)
    }
  }, [id])

  async function cadastrarTodos() {
    setCadastrando(true)
    setErro(null)
    try {
      const resumo = await api.post<LotacaoAplicada>(
        `/api/sincronizacao/unidades/${id}/lotados/cadastrar`,
        {},
      )
      setConfirmando(false)
      avisos.sucesso(
        `${resumo.criados} usuário(s) criado(s)`,
        [
          resumo.atualizados > 0
            ? `${resumo.atualizados} já existia(m) e teve(tiveram) a lotação atualizada`
            : null,
          resumo.semEmail > 0 ? `${resumo.semEmail} sem e-mail, fora do cadastro` : null,
        ]
          .filter(Boolean)
          .join(' · ') || 'Todos já estavam cadastrados e lotados aqui.',
      )
      await puxarDoRh()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao cadastrar os lotados.')
    } finally {
      setCadastrando(false)
    }
  }

  const cadastraveis = (lotados ?? []).filter((p) => !p.semEmail).length
  const semEmail = (lotados ?? []).filter((p) => p.semEmail).length
  const faltando = (lotados ?? []).filter((p) => !p.semEmail && !p.jaCadastrada).length

  return (
    <div className="pagina">
      <header className="cabecalho-pagina">
        <div>
          {/* Volta para onde se veio — a aba de unidades da edição, que é de
              onde esta página é aberta desde que a lista saiu do menu. */}
          <button
            type="button"
            className="botao botao-texto botao-pequeno"
            style={{ padding: 0 }}
            onClick={() => navegar(-1)}
          >
            <Icone nome="seta" tamanho={14} />
            Voltar
          </button>
          <h1 className="titulo-pagina" style={{ marginTop: 8 }}>
            {unidade?.nome ?? 'Unidade'}
          </h1>
          {unidade && (
            <p className="secundaria mono">
              {unidade.codigoSiedos === null
                ? 'sem código do SIEDOS — esta unidade ainda não foi casada com o RH'
                : `código ${unidade.codigoSiedos}`}
              {unidade.comarca ? ` · comarca de ${unidade.comarca}` : ''}
            </p>
          )}
        </div>
      </header>

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {!unidade ? (
        !erro && <Carregando />
      ) : (
        <div className="grade">
          <div className="bloco">
            <div className="bloco-cabecalho">
              <div>
                <h2 className="titulo-secao">Superior responsável</h2>
                <p className="apoio">
                  {unidade.responsavel
                    ? 'Quem responde por esta unidade no prêmio. Ele gerencia a lista de servidores habilitados dela.'
                    : 'Ninguém designado ainda. A designação é feita na aba “Unidades e responsáveis” da edição, uma a uma ou pela planilha de magistrados.'}
                </p>
              </div>
            </div>
            <div className="bloco-corpo">
              {unidade.responsavel ? (
                <>
                  <div className="principal">{unidade.responsavel.nome}</div>
                  <div className="secundaria">{unidade.responsavel.email}</div>
                </>
              ) : (
                <span className="secundaria">— sem responsável —</span>
              )}
            </div>
          </div>

          <div className="bloco">
            <div className="bloco-cabecalho">
              <div>
                <h2 className="titulo-secao">Quem trabalha aqui, segundo o RH</h2>
                <p className="apoio">
                  Consultar é leitura. Cadastrar cria no sistema quem ainda não existe, com papel
                  de servidor e a lotação desta unidade — <strong>não</strong> habilita ninguém a
                  emitir, que é coisa da lista de uma edição.
                </p>
              </div>
              <div className="acoes">
                <button
                  type="button"
                  className={lotados ? 'botao botao-neutro' : 'botao'}
                  disabled={puxando || unidade.codigoSiedos === null}
                  onClick={() => void puxarDoRh()}
                >
                  {puxando ? <span className="giro" /> : <Icone nome="trocar" tamanho={16} />}
                  {puxando ? 'Consultando…' : lotados ? 'Consultar de novo' : 'Puxar do RH'}
                </button>
                {lotados && cadastraveis > 0 && (
                  <button
                    type="button"
                    className="botao"
                    disabled={cadastrando}
                    onClick={() => setConfirmando(true)}
                  >
                    <Icone nome="mais" tamanho={16} />
                    Cadastrar todos
                  </button>
                )}
              </div>
            </div>

            {unidade.codigoSiedos === null ? (
              <div className="bloco-corpo">
                <Aviso tom="atencao" titulo="Sem código do SIEDOS">
                  <p>
                    Esta unidade ainda não foi casada com o RH, então não há como perguntar quem
                    está lotado nela. Compare as unidades em <strong>Sincronização de Unidades</strong>{' '}
                    para gravar o código.
                  </p>
                </Aviso>
              </div>
            ) : !lotados ? (
              <EstadoVazio
                titulo="Nada consultado ainda"
                descricao="Clique em “Puxar do RH” para ver quem o tribunal aponta como lotado nesta unidade. A consulta não altera nada."
              />
            ) : lotados.length === 0 ? (
              <EstadoVazio
                titulo="O RH não aponta ninguém nesta unidade"
                descricao="Pode ser unidade recém-criada, extinta ou sem lotação própria. Nada foi alterado."
              />
            ) : (
              <>
                <div className="tabela-rolagem">
                  <table className="tabela">
                    <thead>
                      <tr>
                        <th>Pessoa</th>
                        <th>Matrícula</th>
                        <th>No sistema</th>
                      </tr>
                    </thead>
                    <tbody>
                      {lotados.map((pessoa) => (
                        <tr key={pessoa.matricula ?? pessoa.email ?? pessoa.nome}>
                          <td>
                            <div className="principal">{pessoa.nome}</div>
                            <div className="secundaria">
                              {pessoa.email ?? '— sem e-mail no RH nem no AD —'}
                            </div>
                          </td>
                          <td className="secundaria mono">{pessoa.matricula ?? '—'}</td>
                          <td>
                            {pessoa.semEmail ? (
                              <span className="secundaria">
                                fica de fora: sem e-mail o login não a reconheceria
                              </span>
                            ) : !pessoa.jaCadastrada ? (
                              <span className="etiqueta etiqueta-so-na-api">Só no RH</span>
                            ) : pessoa.lotacaoCerta ? (
                              <span className="etiqueta etiqueta-sincronizado">Cadastrada</span>
                            ) : (
                              <span className="etiqueta etiqueta-desatualizado">
                                Cadastrada com outra lotação
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="bloco-rodape">
                  {lotados.length} pessoa(s) no RH · {faltando} ainda sem cadastro
                  {semEmail > 0 && ` · ${semEmail} sem e-mail corporativo`}
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {confirmando && (
        <Modal
          titulo={`Cadastrar ${cadastraveis} pessoa(s)?`}
          descricao={unidade?.nome}
          aoFechar={() => !cadastrando && setConfirmando(false)}
          rodape={
            <>
              <button
                type="button"
                className="botao botao-neutro"
                disabled={cadastrando}
                onClick={() => setConfirmando(false)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="botao"
                disabled={cadastrando}
                onClick={() => void cadastrarTodos()}
              >
                {cadastrando && <span className="giro" />}
                {cadastrando ? 'Cadastrando…' : 'Cadastrar todos'}
              </button>
            </>
          }
        >
          <Aviso tom="atencao" titulo="O que esta ação faz">
            <ul>
              <li>Cria como usuário quem ainda não existe, com papel de servidor.</li>
              <li>
                Grava a lotação desta unidade em todos — inclusive em quem já estava cadastrado com
                outra lotação.
              </li>
              <li>
                Não altera o papel de quem já existe: o RH sabe onde a pessoa trabalha, não o que
                ela pode fazer no prêmio.
              </li>
            </ul>
          </Aviso>
          <p className="apoio">
            Cadastrar <strong>não</strong> habilita ninguém a emitir certificado. Quem emite é
            definido pela lista de habilitados de cada edição, que é um retrato datado — é isso que
            permite reemitir anos depois exatamente o mesmo documento.
          </p>
          {semEmail > 0 && (
            <p className="apoio">
              {semEmail} pessoa(s) ficam de fora por não ter e-mail corporativo nem no RH nem no
              AD. Sem e-mail, o login não as reconheceria.
            </p>
          )}
        </Modal>
      )}
    </div>
  )
}
