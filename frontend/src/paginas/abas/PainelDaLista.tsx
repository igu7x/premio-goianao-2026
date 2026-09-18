import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErroApi } from '../../api/cliente'
import type { ListaHabilitados, PessoaDoRh, ResultadoDaBusca } from '../../api/tipos'
import { useSessao } from '../../sessao/SessaoContexto'
import { Aviso, Carregando, EstadoVazio, Modal, formatarData } from '../../componentes/Basicos'
import { Icone } from '../../componentes/Icone'

interface Props {
  caminho: string
  carregarLista: () => Promise<ListaHabilitados>
  aoFechar: () => void
}

/**
 * Lista de habilitados de uma unidade (feature 008).
 *
 * O mesmo painel serve ao administrador e ao magistrado: quem pode editar vem
 * do proprio backend, no campo {@code podeEditar} — o frontend nao recalcula
 * escopo, so obedece.
 */
export function PainelDaLista({ caminho, carregarLista, aoFechar }: Props) {
  const [lista, setLista] = useState<ListaHabilitados | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [mostrarInativos, setMostrarInativos] = useState(false)

  const recarregar = useCallback(async () => {
    try {
      setLista(await carregarLista())
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar a lista.')
    }
  }, [carregarLista])

  useEffect(() => {
    void recarregar()
  }, [recarregar])

  async function incluir(pessoa: PessoaDoRh) {
    setOcupado(true)
    setErro(null)
    try {
      // O CPF saiu do formulário junto com a digitação livre: ele é opcional e
      // só informativo, e o que vem do RH chega mascarado, sem serventia aqui.
      await api.post(caminho, { email: pessoa.email, nome: pessoa.nome, cpf: null })
      await recarregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao incluir o servidor.')
    } finally {
      setOcupado(false)
    }
  }

  /** Pelo id do item: dado pessoal não vai na URL. */
  async function remover(servidorId: number) {
    setOcupado(true)
    setErro(null)
    try {
      await api.remover(`${caminho}/${servidorId}`)
      await recarregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao remover o servidor.')
    } finally {
      setOcupado(false)
    }
  }

  const visiveis = lista?.servidores.filter((s) => mostrarInativos || s.ativo) ?? []
  const inativos = lista?.servidores.filter((s) => !s.ativo).length ?? 0

  return (
    <Modal
      largo
      titulo={lista?.unidadeNome ?? 'Servidores habilitados'}
      descricao={
        lista
          ? `Edição ${lista.edicaoAno}. Somente quem está nesta lista consegue emitir o certificado de servidor desta unidade.`
          : undefined
      }
      aoFechar={aoFechar}
      rodape={
        <button type="button" className="botao botao-neutro" onClick={aoFechar}>
          Fechar
        </button>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      {!lista ? (
        !erro && <Carregando />
      ) : (
        <>
          <div className="painel-resumo">
            <span className="painel-resumo-numero">
              {lista.servidores.filter((s) => s.ativo).length}
            </span>
            <div>
              <div className="principal">servidores habilitados a emitir</div>
              <div className="secundaria">
                {inativos > 0
                  ? `${inativos} removido(s) — a remoção é lógica, para manter a auditoria.`
                  : 'Nenhuma remoção nesta lista.'}
              </div>
            </div>
          </div>

          {lista.podeEditar ? (
            <BuscaDeServidor ocupado={ocupado} aoIncluir={incluir} />
          ) : (
            <Aviso tom="informacao">
              Você pode consultar esta lista, mas não editá-la. O magistrado edita apenas as suas
              unidades e apenas na edição vigente; fora disso, a alteração é do administrador.
            </Aviso>
          )}

          {visiveis.length === 0 ? (
            <EstadoVazio
              titulo="Lista vazia"
              descricao="Semeie a partir do EGESP ou inclua servidores manualmente."
            />
          ) : (
            <div className="tabela-rolagem" style={{ maxHeight: 420, overflowY: 'auto' }}>
              <table className="tabela">
                <thead>
                  <tr>
                    <th>Servidor</th>
                    <th>E-mail</th>
                    <th>Origem</th>
                    <th>Incluído em</th>
                    {lista.podeEditar && <th className="direita">Ação</th>}
                  </tr>
                </thead>
                <tbody>
                  {visiveis.map((servidor) => (
                    <tr key={servidor.id} style={{ opacity: servidor.ativo ? 1 : 0.5 }}>
                      <td>
                        <div className="principal">{servidor.nome}</div>
                        {servidor.cpfMascarado && (
                          <div className="secundaria mono">{servidor.cpfMascarado}</div>
                        )}
                      </td>
                      <td className="secundaria">{servidor.email ?? servidor.emailMascarado}</td>
                      <td>
                        <span className="etiqueta">
                          {servidor.origem === 'EGESP' ? 'EGESP' : 'Manual'}
                        </span>
                      </td>
                      <td className="secundaria">{formatarData(servidor.criadoEm)}</td>
                      {lista.podeEditar && (
                        <td className="direita">
                          {servidor.ativo ? (
                            <button
                              type="button"
                              className="botao botao-perigo botao-pequeno"
                              disabled={ocupado}
                              onClick={() => void remover(servidor.id)}
                            >
                              Remover
                            </button>
                          ) : (
                            <span className="secundaria">removido</span>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {inativos > 0 && (
            <button
              type="button"
              className="botao botao-texto botao-pequeno"
              onClick={() => setMostrarInativos((atual) => !atual)}
            >
              {mostrarInativos ? 'Ocultar removidos' : `Mostrar ${inativos} removido(s)`}
            </button>
          )}
        </>
      )}
    </Modal>
  )
}

/** O backend ignora termo menor que isto; avisar antes evita a lista vazia que
 *  parece "ninguém encontrado". */
const MINIMO_DO_TERMO = 3
const ESPERA_DA_BUSCA = 350

/**
 * Quem incluir na lista, escolhido do cadastro em vez de digitado.
 *
 * <p>Antes eram três campos livres — e-mail, nome e CPF —, e errar uma letra do
 * e-mail criava um habilitado que nunca conseguiria emitir: o e-mail é a chave
 * da pessoa no login (DI-24), e um endereço que não existe não casa com
 * ninguém. Agora se procura pelo nome, no sistema e no RH, e se escolhe.
 *
 * <p>Quem ainda não tem cadastro aparece marcado como tal. Incluí-lo na lista
 * funcionaria — a lista guarda e-mail e nome, não um id de usuário —, mas ele
 * não entraria no sistema para emitir. Por isso o caminho oferecido é
 * cadastrá-lo primeiro, com o e-mail já preenchido.
 */
function BuscaDeServidor({
  ocupado,
  aoIncluir,
}: {
  ocupado: boolean
  aoIncluir: (pessoa: PessoaDoRh) => Promise<void>
}) {
  const [termo, setTermo] = useState('')
  const [resultados, setResultados] = useState<PessoaDoRh[] | null>(null)
  const [rhRespondeu, setRhRespondeu] = useState(true)
  const [buscando, setBuscando] = useState(false)
  const { tem } = useSessao()

  useEffect(() => {
    const alvo = termo.trim()
    if (alvo.length < MINIMO_DO_TERMO) {
      setResultados(null)
      setBuscando(false)
      return
    }

    let ativo = true
    setBuscando(true)
    const relogio = setTimeout(() => {
      api
        .get<ResultadoDaBusca>(`/api/pessoas?termo=${encodeURIComponent(alvo)}`)
        .then((resultado) => {
          if (!ativo) return
          setResultados(resultado.pessoas)
          setRhRespondeu(resultado.rhRespondeu)
          setBuscando(false)
        })
        .catch(() => ativo && setBuscando(false))
    }, ESPERA_DA_BUSCA)

    return () => {
      ativo = false
      clearTimeout(relogio)
    }
  }, [termo])

  const semCadastro = (resultados ?? []).filter((p) => p.origem !== 'SISTEMA')

  return (
    <div className="campo">
      <label htmlFor="busca-servidor">Incluir servidor</label>
      <input
        id="busca-servidor"
        value={termo}
        autoComplete="off"
        placeholder="Parte do nome ou do e-mail"
        onChange={(evento) => setTermo(evento.target.value)}
      />
      <span className="campo-dica">
        Procura nos usuários do sistema e no RH. A partir de {MINIMO_DO_TERMO} letras.
      </span>

      {buscando && <Carregando texto="Procurando…" />}

      {!buscando && resultados?.length === 0 && (
        <NaoCadastrado
          termo={termo.trim()}
          podeCadastrar={tem('SUPERADMIN')}
          rhRespondeu={rhRespondeu}
        />
      )}

      {resultados && resultados.length > 0 && (
        <div className="lista-usuarios">
          {resultados.map((pessoa) => (
            <button
              key={`${pessoa.origem}-${pessoa.email ?? pessoa.matricula ?? pessoa.nome}`}
              type="button"
              className="usuario-opcao"
              // Sem e-mail ninguém emite: incluir seria criar uma linha morta.
              disabled={ocupado || !pessoa.temEmail}
              onClick={() => void aoIncluir(pessoa)}
            >
              <span>
                <span className="principal">{pessoa.nome}</span>{' '}
                <span
                  className={
                    pessoa.origem === 'SISTEMA'
                      ? 'etiqueta etiqueta-sincronizado'
                      : 'etiqueta etiqueta-so-na-api'
                  }
                >
                  {pessoa.origem === 'SISTEMA' ? 'no sistema' : 'só no RH'}
                </span>
                <br />
                <span className="secundaria mono">
                  {pessoa.email ?? `matrícula ${pessoa.matricula ?? '—'} · sem e-mail`}
                </span>
              </span>
              <Icone nome="mais" tamanho={16} />
            </button>
          ))}
        </div>
      )}

      {/* Quem veio do RH ainda não entra no sistema: a lista o habilita a
          emitir, mas o login não o reconheceria. */}
      {semCadastro.length > 0 && (
        <div style={{ marginTop: 'var(--e3)' }}>
          <Aviso tom="atencao" titulo="Alguns resultados ainda não têm cadastro">
            <p>
              Quem está marcado como “só no RH” pode entrar na lista, mas só conseguirá emitir
              depois de ser cadastrado como usuário.{' '}
              {tem('SUPERADMIN') ? (
                <Link to={`/usuarios?novo=${encodeURIComponent(semCadastro[0].email ?? '')}`}>
                  Cadastrar agora
                </Link>
              ) : (
                'Peça o cadastro ao superadministrador.'
              )}
            </p>
          </Aviso>
        </div>
      )}
    </div>
  )
}

/** Ninguém encontrado: o caminho é cadastrar, e não digitar um e-mail à mão. */
function NaoCadastrado({
  termo,
  podeCadastrar,
  rhRespondeu,
}: {
  termo: string
  podeCadastrar: boolean
  rhRespondeu: boolean
}) {
  return (
    <div style={{ marginTop: 'var(--e3)' }}>
      <Aviso tom="atencao" titulo={`Ninguém encontrado para “${termo}”`}>
        <p>
          {rhRespondeu
            ? 'Não há ninguém com esse nome nos usuários do sistema nem no RH.'
            : 'O RH não respondeu agora, então a busca olhou só os usuários do sistema.'}{' '}
          {podeCadastrar ? (
            <Link to="/usuarios?novo=">Cadastrar o usuário</Link>
          ) : (
            'Peça o cadastro ao superadministrador e volte aqui.'
          )}
        </p>
      </Aviso>
    </div>
  )
}
