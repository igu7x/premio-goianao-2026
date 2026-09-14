import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi } from '../api/cliente'
import type {
  ComparacaoServidores,
  Edicao,
  ItemSincronizacao,
  ServidorComparado,
  SituacaoIntegracao,
  UnidadeComparada,
} from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio, Modal } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'

const BASE = '/api/sincronizacao'

/** Primeiro o que pede decisão; por último o que já está certo — quem abre a
 *  tela quer ver o que mudou, não confirmar o que não mudou. */
const ORDEM: ItemSincronizacao[] = ['DESATUALIZADO', 'SO_NA_API', 'ORFAO', 'SINCRONIZADO']

const ROTULO: Record<ItemSincronizacao, string> = {
  SINCRONIZADO: 'Sincronizado',
  DESATUALIZADO: 'Desatualizado',
  SO_NA_API: 'Só no RH',
  ORFAO: 'Órfão',
}

const CLASSE: Record<ItemSincronizacao, string> = {
  SINCRONIZADO: 'etiqueta etiqueta-sincronizado',
  DESATUALIZADO: 'etiqueta etiqueta-desatualizado',
  SO_NA_API: 'etiqueta etiqueta-so-na-api',
  ORFAO: 'etiqueta etiqueta-orfao',
}

/** O que cada grupo de unidades significa e o que o botão dali faz. Fica no
 *  cabeçalho do grupo para não repetir a mesma frase em cada linha. */
const EXPLICACAO_UNIDADE: Record<ItemSincronizacao, string> = {
  DESATUALIZADO: 'O RH tem outro nome ou outra comarca para estas unidades. Atualizar adota o que vem do RH.',
  SO_NA_API: 'Existem no RH e ainda não aqui. Cadastrar cria a unidade com nome, comarca e código.',
  ORFAO: 'Estão cadastradas aqui e não vieram mais do RH — extinção, remanejamento ou código trocado. Esta tela não apaga unidade: certificados já emitidos apontam para elas.',
  SINCRONIZADO: 'Iguais dos dois lados. Nada a fazer.',
}

function Etiqueta({ situacao }: { situacao: ItemSincronizacao }) {
  return <span className={CLASSE[situacao]}>{ROTULO[situacao]}</span>
}

/** Cada linha precisa de uma chave estável mesmo antes de existir localmente. */
function chaveDa(unidade: UnidadeComparada): string {
  return unidade.unidadeId !== null ? `u${unidade.unidadeId}` : `c${unidade.codigo}`
}

/**
 * Sincronização com o RH (feature 010) — exclusiva do superadministrador.
 *
 * <b>Comparar não grava nada.</b> A separação existe porque o que está em jogo
 * é o nome impresso em certificado e quem tem direito de emitir: carga
 * automática erra em silêncio, e aqui cada alteração é um clique de alguém que
 * leu a linha.
 */
export function Sincronizacao() {
  const [situacao, setSituacao] = useState<SituacaoIntegracao | null>(null)
  const [edicoes, setEdicoes] = useState<Edicao[] | null>(null)
  const [edicaoId, setEdicaoId] = useState<number | null>(null)
  const [codigo, setCodigo] = useState('')
  /** Nulo enquanto nenhuma comparação foi pedida — diferente de lista vazia. */
  const [unidades, setUnidades] = useState<UnidadeComparada[] | null>(null)
  const [comparando, setComparando] = useState(false)
  const [aplicando, setAplicando] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [aberta, setAberta] = useState<UnidadeComparada | null>(null)
  const avisos = useAvisos()

  useEffect(() => {
    let ativo = true
    api
      .get<SituacaoIntegracao>(`${BASE}/situacao`)
      .then((s) => ativo && setSituacao(s))
      .catch((e: ErroApi) => ativo && setErro(e.message))
    api
      .get<Edicao[]>('/api/edicoes')
      .then((lista) => {
        if (!ativo) return
        setEdicoes(lista)
        // A comparação de servidores é sempre dentro de uma edição; a vigente é
        // a que quase sempre se quer, e sem pré-seleção a tela abre inerte.
        setEdicaoId((lista.find((e) => e.vigente) ?? lista[0])?.id ?? null)
      })
      .catch((e: ErroApi) => ativo && setErro(e.message))
    return () => {
      ativo = false
    }
  }, [])

  const comparar = useCallback(async () => {
    const numero = Number(codigo.trim())
    if (!codigo.trim() || !Number.isFinite(numero)) {
      return
    }
    setComparando(true)
    setErro(null)
    try {
      setUnidades(await api.get<UnidadeComparada[]>(`${BASE}/unidades?codigo=${numero}`))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao comparar com o RH.')
    } finally {
      setComparando(false)
    }
  }, [codigo])

  /** Recompara depois de aplicar: a linha alterada muda de grupo, e ver isso é
   *  a confirmação de que a alteração pegou. */
  async function aplicar(unidade: UnidadeComparada, acao: () => Promise<void>, sucesso: () => void) {
    setAplicando(chaveDa(unidade))
    setErro(null)
    try {
      await acao()
      sucesso()
      await comparar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao aplicar a alteração.')
    } finally {
      setAplicando(null)
    }
  }

  function cadastrar(unidade: UnidadeComparada) {
    void aplicar(
      unidade,
      () => api.post(`${BASE}/unidades`, { codigo: unidade.codigo }),
      () =>
        avisos.sucesso(
          `${unidade.nomeNaApi ?? 'Unidade'} cadastrada`,
          'Já pode receber reconhecimentos e lista de habilitados.',
        ),
    )
  }

  function atualizar(unidade: UnidadeComparada) {
    void aplicar(
      unidade,
      // Sem corpo: o que vai valer é o que o RH diz hoje, e o servidor vai
      // buscá-lo de novo — mandar o nome daqui seria mandar o que já está velho.
      () => api.put(`${BASE}/unidades/${unidade.unidadeId}`, {}),
      () =>
        avisos.sucesso(
          `Unidade atualizada para “${unidade.nomeNaApi}”`,
          'Vale para os próximos certificados; os já emitidos não mudam.',
        ),
    )
  }

  const edicaoEscolhida = edicoes?.find((e) => e.id === edicaoId) ?? null

  return (
    <div className="pagina">
      <header className="cabecalho-pagina">
        <div>
          <span className="rotulo">Superadministração</span>
          <h1 className="titulo-pagina" style={{ marginTop: 4 }}>
            Sincronização com o RH
          </h1>
          <p>
            Compara o que a API corporativa (ConnectTJ/SIEDOS) diz sobre uma unidade e seus
            lotados com o que está cadastrado aqui. <strong>Comparar não grava nada</strong>:
            criar unidade, corrigir nome, incluir ou desvincular servidor acontece um de cada
            vez, no clique.
          </p>
        </div>
      </header>

      {situacao && !situacao.ligada && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="atencao" titulo="Integração corporativa não configurada">
            <p>
              Do lado do RH você está vendo {situacao.origemDosDados} — o sistema sobe assim de
              propósito, sem a API. A comparação funciona e os botões gravam de verdade no
              cadastro do prêmio; o que ainda não é real é a origem dos dados.
            </p>
          </Aviso>
        </div>
      )}

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      <div className="grade">
        <div className="bloco">
          <div className="bloco-cabecalho">
            <div>
              <h2 className="titulo-secao">Comparar</h2>
              <p className="apoio">
                O código é o da unidade no SIEDOS. A consulta traz a hierarquia a partir dele.
              </p>
            </div>
          </div>

          <div className="bloco-corpo">
            <div className="sincronizacao-consulta">
              <div className="campo">
                <label htmlFor="codigo-unidade">Código da unidade</label>
                <input
                  id="codigo-unidade"
                  className="mono"
                  inputMode="numeric"
                  placeholder="1234"
                  value={codigo}
                  onChange={(evento) => setCodigo(evento.target.value.replace(/\D/g, ''))}
                />
              </div>

              <div className="campo">
                <label htmlFor="edicao-sincronizacao">Edição</label>
                <select
                  id="edicao-sincronizacao"
                  value={edicaoId ?? ''}
                  onChange={(evento) => setEdicaoId(Number(evento.target.value))}
                >
                  {!edicoes && <option value="">Carregando…</option>}
                  {edicoes?.length === 0 && <option value="">Nenhuma edição cadastrada</option>}
                  {edicoes?.map((edicao) => (
                    <option key={edicao.id} value={edicao.id}>
                      {edicao.ano}
                      {edicao.vigente ? ' (vigente)' : ''}
                    </option>
                  ))}
                </select>
                <span className="campo-dica">
                  A edição define de qual lista de habilitados os servidores são comparados.
                </span>
              </div>

              <button
                type="button"
                className="botao"
                disabled={comparando || !codigo.trim()}
                onClick={() => void comparar()}
              >
                {comparando ? <span className="giro" /> : <Icone nome="trocar" tamanho={16} />}
                {comparando ? 'Comparando…' : 'Comparar'}
              </button>
            </div>
          </div>
        </div>

        {unidades === null ? (
          <div className="bloco">
            <EstadoVazio
              titulo="Nenhuma comparação feita"
              descricao="Informe o código da unidade no SIEDOS e compare. A leitura não altera nada no cadastro."
            />
          </div>
        ) : unidades.length === 0 ? (
          <div className="bloco">
            <EstadoVazio
              titulo="O RH não devolveu nenhuma unidade para este código"
              descricao="Confira o código no SIEDOS. Nada foi alterado."
            />
          </div>
        ) : (
          ORDEM.filter((grupo) => unidades.some((u) => u.situacao === grupo)).map((grupo) => {
            const doGrupo = unidades.filter((u) => u.situacao === grupo)
            return (
              <div className="bloco" key={grupo}>
                <div className="bloco-cabecalho">
                  <div>
                    <Etiqueta situacao={grupo} />
                    <h2 className="titulo-secao" style={{ marginTop: 8 }}>
                      {doGrupo.length} unidade{doGrupo.length === 1 ? '' : 's'}
                    </h2>
                    <p className="apoio">{EXPLICACAO_UNIDADE[grupo]}</p>
                  </div>
                </div>

                <div className="tabela-rolagem">
                  <table className="tabela">
                    <thead>
                      <tr>
                        <th>Unidade</th>
                        <th>No RH</th>
                        <th className="direita">Ações</th>
                      </tr>
                    </thead>
                    <tbody>
                      {doGrupo.map((unidade) => (
                        <tr key={chaveDa(unidade)}>
                          <td>
                            <div className="principal">
                              {unidade.nomeNoSistema ?? unidade.nomeNaApi ?? '—'}
                            </div>
                            <div className="secundaria mono">código {unidade.codigo ?? '—'}</div>
                          </td>
                          <td>
                            {unidade.situacao === 'ORFAO' ? (
                              <span className="secundaria">não veio mais do RH</span>
                            ) : (
                              <>
                                <div
                                  className={
                                    unidade.situacao === 'DESATUALIZADO'
                                      ? 'principal'
                                      : 'secundaria'
                                  }
                                >
                                  {unidade.nomeNaApi ?? '—'}
                                </div>
                                <div className="secundaria">{unidade.comarca ?? '—'}</div>
                              </>
                            )}
                          </td>
                          <td>
                            <div className="acoes acoes-direita">
                              {unidade.unidadeId !== null && (
                                <button
                                  type="button"
                                  className="botao botao-texto botao-pequeno"
                                  disabled={edicaoId === null}
                                  onClick={() => setAberta(unidade)}
                                >
                                  Servidores
                                  <Icone nome="seta" tamanho={14} />
                                </button>
                              )}
                              {unidade.situacao === 'DESATUALIZADO' && (
                                <div className="sincronizacao-acao">
                                  <button
                                    type="button"
                                    className="botao botao-pequeno"
                                    disabled={aplicando === chaveDa(unidade)}
                                    onClick={() => atualizar(unidade)}
                                  >
                                    Atualizar
                                  </button>
                                  <span className="secundaria">
                                    o nome vai impresso no certificado; os já emitidos não mudam
                                  </span>
                                </div>
                              )}
                              {unidade.situacao === 'SO_NA_API' && (
                                <button
                                  type="button"
                                  className="botao botao-pequeno"
                                  disabled={aplicando === chaveDa(unidade)}
                                  onClick={() => cadastrar(unidade)}
                                >
                                  Cadastrar unidade
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {grupo === 'ORFAO' && (
                  <div className="bloco-rodape">
                    Unidade órfã não se apaga por aqui. Se ela foi extinta, o caminho é deixar de
                    usá-la nas próximas edições — o histórico do que já foi emitido continua
                    apontando para o nome que estava no certificado.
                  </div>
                )}
              </div>
            )
          })
        )}
      </div>

      {aberta && aberta.unidadeId !== null && edicaoId !== null && (
        <PainelDeServidores
          unidadeId={aberta.unidadeId}
          edicaoId={edicaoId}
          edicaoAno={edicaoEscolhida?.ano ?? null}
          aoFechar={() => setAberta(null)}
        />
      )}
    </div>
  )
}

/** O que cada situação significa do lado das pessoas. */
const EXPLICACAO_SERVIDOR: Record<ItemSincronizacao, string> = {
  DESATUALIZADO: 'Está na lista, mas com nome ou matrícula diferentes do RH.',
  SO_NA_API: 'O RH aponta como lotado na unidade e ainda não está na lista da edição.',
  ORFAO: 'Está na lista e não consta mais na lotação do RH.',
  SINCRONIZADO: 'Iguais dos dois lados.',
}

/**
 * Comparação das pessoas de uma unidade dentro de uma edição.
 *
 * O e-mail aparece inteiro: a tela é do superadministrador, que já pode editar
 * qualquer lista (DI-10). Ele nunca vai na URL — a desvinculação usa o id da
 * linha.
 */
function PainelDeServidores({
  unidadeId,
  edicaoId,
  edicaoAno,
  aoFechar,
}: {
  unidadeId: number
  edicaoId: number
  edicaoAno: number | null
  aoFechar: () => void
}) {
  const [comparacao, setComparacao] = useState<ComparacaoServidores | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [aplicando, setAplicando] = useState<number | null>(null)
  const avisos = useAvisos()

  const recarregar = useCallback(async () => {
    try {
      setComparacao(
        await api.get<ComparacaoServidores>(
          `${BASE}/unidades/${unidadeId}/servidores?edicaoId=${edicaoId}`,
        ),
      )
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao comparar os servidores.')
    }
  }, [unidadeId, edicaoId])

  useEffect(() => {
    void recarregar()
  }, [recarregar])

  async function incluir(servidor: ServidorComparado) {
    if (servidor.matricula === null) return
    setAplicando(servidor.matricula)
    setErro(null)
    try {
      await api.post(`${BASE}/unidades/${unidadeId}/servidores`, {
        edicaoId,
        matricula: servidor.matricula,
      })
      avisos.sucesso(`${servidor.nome} entrou na lista`, 'Já pode emitir o certificado desta unidade.')
      await recarregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao incluir o servidor.')
    } finally {
      setAplicando(null)
    }
  }

  async function desvincular(servidor: ServidorComparado) {
    if (servidor.servidorHabilitadoId === null) return
    setAplicando(servidor.servidorHabilitadoId)
    setErro(null)
    try {
      await api.remover(`${BASE}/servidores/${servidor.servidorHabilitadoId}`)
      avisos.sucesso(
        `${servidor.nome} saiu da lista`,
        'A remoção é lógica: o histórico e os certificados já emitidos ficam.',
      )
      await recarregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao desvincular o servidor.')
    } finally {
      setAplicando(null)
    }
  }

  const ordenados = comparacao
    ? ORDEM.flatMap((grupo) => comparacao.servidores.filter((s) => s.situacao === grupo))
    : []

  return (
    <Modal
      largo
      titulo={comparacao?.unidadeNome ?? 'Servidores da unidade'}
      descricao={
        edicaoAno === null
          ? 'Lotação do RH cruzada com a lista de habilitados da edição.'
          : `Edição ${edicaoAno}. Lotação do RH cruzada com a lista de habilitados — nada aqui foi gravado pela comparação.`
      }
      aoFechar={aoFechar}
      rodape={
        <button type="button" className="botao botao-neutro" onClick={aoFechar}>
          Fechar
        </button>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      {!comparacao ? (
        <Carregando />
      ) : (
        <>
          {comparacao.responsavelSugerido && (
            <Aviso tom="informacao">
              O RH aponta <strong>{comparacao.responsavelSugerido}</strong> como responsável por
              esta unidade. É só uma sugestão: designar quem responde continua sendo ato do
              superadministrador, na tela Unidades.
            </Aviso>
          )}

          {ordenados.length === 0 ? (
            <EstadoVazio
              titulo="Nada a comparar"
              descricao="O RH não devolveu lotados para esta unidade e a lista da edição está vazia."
            />
          ) : (
            <div className="tabela-rolagem" style={{ maxHeight: 460, overflowY: 'auto' }}>
              <table className="tabela">
                <thead>
                  <tr>
                    <th>Situação</th>
                    <th>Servidor</th>
                    <th>Matrícula</th>
                    <th className="direita">Ação</th>
                  </tr>
                </thead>
                <tbody>
                  {ordenados.map((servidor) => (
                    <tr key={servidor.servidorHabilitadoId ?? `m${servidor.matricula}`}>
                      <td>
                        <Etiqueta situacao={servidor.situacao} />
                        <div className="secundaria" style={{ marginTop: 4, maxWidth: '26ch' }}>
                          {EXPLICACAO_SERVIDOR[servidor.situacao]}
                        </div>
                      </td>
                      <td>
                        <div className="principal">{servidor.nome}</div>
                        <div className="secundaria">{servidor.email ?? '— sem e-mail no RH —'}</div>
                        {servidor.origem && (
                          <div className="secundaria">
                            inclusão {servidor.origem === 'EGESP' ? 'pelo RH' : 'manual'}
                          </div>
                        )}
                      </td>
                      <td className="secundaria mono">{servidor.matricula ?? '—'}</td>
                      <td>
                        <div className="acoes acoes-direita">
                          <AcaoDoServidor
                            servidor={servidor}
                            ocupado={
                              aplicando !== null &&
                              (aplicando === servidor.matricula ||
                                aplicando === servidor.servidorHabilitadoId)
                            }
                            aoIncluir={() => void incluir(servidor)}
                            aoDesvincular={() => void desvincular(servidor)}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </Modal>
  )
}

function AcaoDoServidor({
  servidor,
  ocupado,
  aoIncluir,
  aoDesvincular,
}: {
  servidor: ServidorComparado
  ocupado: boolean
  aoIncluir: () => void
  aoDesvincular: () => void
}) {
  if (servidor.situacao === 'SO_NA_API') {
    return (
      <div className="sincronizacao-acao">
        <button
          type="button"
          className="botao botao-pequeno"
          disabled={ocupado || servidor.semEmailNaApi || servidor.matricula === null}
          onClick={aoIncluir}
        >
          Incluir na lista
        </button>
        {servidor.semEmailNaApi && (
          <span className="secundaria">
            o RH não tem e-mail corporativo desta pessoa — sem ele o login não a reconheceria, e
            ela não conseguiria emitir
          </span>
        )}
      </div>
    )
  }

  if (servidor.situacao === 'ORFAO') {
    // Inclusão manual foi ajuste humano deliberado: desfazê-la por causa do RH
    // seria apagar a correção de quem conhecia o caso (010/CA-6).
    if (servidor.origem === 'MANUAL') {
      return (
        <span className="secundaria">
          inclusão manual — foi um ajuste humano e permanece. Para tirá-la, use a lista da unidade.
        </span>
      )
    }
    return (
      <button
        type="button"
        className="botao botao-perigo botao-pequeno"
        disabled={ocupado || servidor.servidorHabilitadoId === null}
        onClick={aoDesvincular}
      >
        Desvincular
      </button>
    )
  }

  if (servidor.situacao === 'DESATUALIZADO') {
    // Não há endpoint que corrija a pessoa: o nome da lista é o que sai no
    // certificado, e trocá-lo é decisão de quem mantém a lista da unidade.
    return (
      <span className="secundaria">
        o nome ao lado é o do RH; a lista continua com o que já está gravado
      </span>
    )
  }

  return <span className="secundaria">—</span>
}
