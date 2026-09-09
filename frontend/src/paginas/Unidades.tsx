import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi } from '../api/cliente'
import type { Unidade, Usuario } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio, Modal } from '../componentes/Basicos'

/**
 * Cadastro de unidades — exclusivo do superadministrador.
 *
 * O que se faz aqui é designar o <b>superior responsável</b> por cada unidade.
 * Essa designação é o que passa a dar ao magistrado o direito de gerenciar a
 * lista de servidores habilitados dali — antes, o único caminho era ter sido
 * reconhecido no prêmio pela unidade, o que amarra duas coisas diferentes:
 * responder pela vara e ter vencido com ela.
 */
export function Unidades() {
  const [unidades, setUnidades] = useState<Unidade[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [filtro, setFiltro] = useState('')
  const [designando, setDesignando] = useState<Unidade | null>(null)
  const avisos = useAvisos()

  const carregar = useCallback(async () => {
    try {
      setUnidades(await api.get<Unidade[]>('/api/unidades'))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar as unidades.')
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function retirar(unidade: Unidade) {
    setErro(null)
    try {
      await api.remover(`/api/unidades/${unidade.id}/responsavel`)
      avisos.sucesso(
        `${unidade.responsavel?.nome ?? 'Responsável'} deixou de responder por ${unidade.nome}`,
        'Ele perde o acesso à lista de servidores desta unidade.',
      )
      await carregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao retirar a designação.')
    }
  }

  const visiveis = (unidades ?? []).filter((u) =>
    u.nome.toLowerCase().includes(filtro.trim().toLowerCase()),
  )
  const semResponsavel = (unidades ?? []).filter((u) => !u.responsavel).length

  return (
    <div className="pagina">
      <header className="cabecalho-pagina">
        <div>
          <span className="rotulo">Superadministração</span>
          <h1 className="titulo-pagina" style={{ marginTop: 4 }}>
            Unidades
          </h1>
          <p>
            Todas as unidades cadastradas e quem responde por cada uma. Designar o superior
            responsável é o que libera, para ele, a aba “Servidores da unidade” — mesmo que a
            unidade não o tenha reconhecido no prêmio.
          </p>
        </div>
      </header>

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {!unidades ? (
        <Carregando />
      ) : unidades.length === 0 ? (
        <div className="bloco">
          <EstadoVazio
            titulo="Nenhuma unidade cadastrada"
            descricao="As unidades entram pelo cadastro de reconhecidos ou, em breve, pela importação da planilha. Enquanto não houver nenhuma, não há o que designar."
          />
        </div>
      ) : (
        <div className="bloco">
          <div className="bloco-cabecalho">
            <div>
              <h2 className="titulo-secao">
                {unidades.length} unidade{unidades.length === 1 ? '' : 's'}
              </h2>
              <p className="apoio">
                {semResponsavel === 0
                  ? 'Todas têm responsável designado.'
                  : `${semResponsavel} ainda sem responsável.`}
              </p>
            </div>
            <div className="campo" style={{ margin: 0, minWidth: 260 }}>
              <label htmlFor="filtro-unidade" className="rotulo">
                Buscar
              </label>
              <input
                id="filtro-unidade"
                placeholder="Nome da unidade…"
                value={filtro}
                onChange={(evento) => setFiltro(evento.target.value)}
              />
            </div>
          </div>

          <div className="tabela-rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Unidade</th>
                  <th>Superior responsável</th>
                  <th className="direita">Ações</th>
                </tr>
              </thead>
              <tbody>
                {visiveis.map((unidade) => (
                  <tr key={unidade.id}>
                    <td>
                      <div className="unidade-nome" style={{ fontSize: 15, margin: 0 }}>
                        {unidade.nome}
                      </div>
                    </td>
                    <td>
                      {unidade.responsavel ? (
                        <>
                          <div className="principal">{unidade.responsavel.nome}</div>
                          <div className="secundaria">{unidade.responsavel.email}</div>
                        </>
                      ) : (
                        <span className="secundaria">— sem responsável —</span>
                      )}
                    </td>
                    <td>
                      <div className="acoes acoes-direita">
                        <button
                          type="button"
                          className="botao botao-texto botao-pequeno"
                          onClick={() => setDesignando(unidade)}
                        >
                          {unidade.responsavel ? 'Trocar' : 'Designar'}
                        </button>
                        {unidade.responsavel && (
                          <button
                            type="button"
                            className="botao botao-perigo botao-pequeno"
                            onClick={() => void retirar(unidade)}
                          >
                            Retirar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="bloco-rodape">
            O responsável só vê a unidade na tela dele quando ela foi reconhecida na edição
            vigente — fora disso não existe lista de habilitados para gerenciar. A importação da
            planilha de unidades ainda não está implementada.
          </div>
        </div>
      )}

      {designando && (
        <ModalDesignar
          unidade={designando}
          aoFechar={() => setDesignando(null)}
          aoDesignar={async (nome) => {
            setDesignando(null)
            avisos.sucesso(
              `${nome} responde por ${designando.nome}`,
              'Ele já pode gerenciar a lista de servidores desta unidade.',
            )
            await carregar()
          }}
        />
      )}
    </div>
  )
}

function ModalDesignar({
  unidade,
  aoFechar,
  aoDesignar,
}: {
  unidade: Unidade
  aoFechar: () => void
  aoDesignar: (nome: string) => Promise<void>
}) {
  const [magistrados, setMagistrados] = useState<Usuario[] | null>(null)
  const [escolhido, setEscolhido] = useState<number | ''>('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  // Só magistrados ativos: é a tela do magistrado que a designação libera, e
  // designar quem não tem o papel criaria um responsável sem onde exercer.
  useEffect(() => {
    api
      .get<Usuario[]>('/api/usuarios')
      .then((todos) =>
        setMagistrados(todos.filter((u) => u.ativo && u.papeis.includes('MAGISTRADO'))),
      )
      .catch((e: ErroApi) => setErro(e.message))
  }, [])

  async function designar() {
    if (escolhido === '') return
    setSalvando(true)
    setErro(null)
    try {
      const atualizada = await api.put<Unidade>(`/api/unidades/${unidade.id}/responsavel`, {
        usuarioId: escolhido,
      })
      await aoDesignar(atualizada.responsavel?.nome ?? 'O magistrado')
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao designar.')
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo={`Responsável por ${unidade.nome}`}
      descricao="Quem responde pela unidade passa a gerenciar a lista de servidores habilitados dela na edição vigente."
      aoFechar={aoFechar}
      rodape={
        <>
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            Cancelar
          </button>
          <button
            type="button"
            className="botao"
            disabled={salvando || escolhido === ''}
            onClick={() => void designar()}
          >
            {salvando && <span className="giro" />}
            {salvando ? 'Designando…' : 'Designar'}
          </button>
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      {!magistrados ? (
        <Carregando texto="Carregando magistrados…" />
      ) : magistrados.length === 0 ? (
        <Aviso tom="atencao" titulo="Nenhum magistrado cadastrado">
          <p>
            Só quem tem o papel de magistrado pode responder por uma unidade. Cadastre-o em
            Usuários do sistema e volte aqui.
          </p>
        </Aviso>
      ) : (
        <div className="campo">
          <label htmlFor="responsavel">Magistrado</label>
          <select
            id="responsavel"
            value={escolhido}
            onChange={(evento) => setEscolhido(Number(evento.target.value))}
          >
            <option value="">Escolha…</option>
            {magistrados.map((m) => (
              <option key={m.id} value={m.id}>
                {m.nome} — {m.email}
              </option>
            ))}
          </select>
          {unidade.responsavel && (
            <span className="campo-dica">
              Hoje responde {unidade.responsavel.nome}. Designar outro substitui.
            </span>
          )}
        </div>
      )}
    </Modal>
  )
}
