import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi } from '../../api/cliente'
import type { ListaHabilitados } from '../../api/tipos'
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
  const [email, setEmail] = useState('')
  const [nome, setNome] = useState('')
  const [cpf, setCpf] = useState('')
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

  async function incluir() {
    setOcupado(true)
    setErro(null)
    try {
      await api.post(caminho, { email, nome, cpf: cpf || null })
      setEmail('')
      setNome('')
      setCpf('')
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
        <Carregando />
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
            <div className="inclusao">
              <div className="campo">
                <label htmlFor="email-servidor">E-mail corporativo</label>
                <input
                  id="email-servidor"
                  type="email"
                  placeholder="nome@tjgo.jus.br"
                  value={email}
                  onChange={(evento) => setEmail(evento.target.value)}
                />
              </div>
              <div className="campo">
                <label htmlFor="nome-servidor">Nome</label>
                <input
                  id="nome-servidor"
                  value={nome}
                  onChange={(evento) => setNome(evento.target.value)}
                />
              </div>
              <div className="campo">
                <label htmlFor="cpf-servidor">CPF (opcional)</label>
                <input
                  id="cpf-servidor"
                  className="mono"
                  placeholder="000.000.000-00"
                  value={cpf}
                  onChange={(evento) => setCpf(evento.target.value)}
                />
              </div>
              <button
                type="button"
                className="botao"
                disabled={ocupado || !email || !nome}
                onClick={() => void incluir()}
              >
                <Icone nome="mais" tamanho={16} />
                Incluir
              </button>
            </div>
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
