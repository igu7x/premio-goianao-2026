import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, ErroApi } from '../api/cliente'
import type { Papel, Unidade, Usuario } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio, Modal, formatarData } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import {
  BotaoAtualizarBase,
  ModalAtualizacaoDaBase,
  PainelAtualizacaoDaBase,
  useAtualizacaoDaBase,
} from './AtualizacaoDaBase'

const PAPEIS: Papel[] = ['ADMINISTRADOR', 'MAGISTRADO', 'SERVIDOR']

const ROTULO_PAPEL: Record<Papel, string> = {
  SUPERADMIN: 'Superadmin',
  ADMINISTRADOR: 'Administrador',
  MAGISTRADO: 'Magistrado',
  SERVIDOR: 'Servidor',
}

/**
 * Cadastro de usuários — exclusivo do superadministrador.
 *
 * É o único módulo com essa restrição, porque criar usuário é conceder acesso.
 * Duas operações convivem aqui: cadastrar alguém novo, e promover a
 * superadministrador quem já existe — esta última só pede o e-mail, porque
 * promover é dar um papel a alguém conhecido, não cadastrar gente nova.
 */
export function Usuarios() {
  const [usuarios, setUsuarios] = useState<Usuario[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [editando, setEditando] = useState<Usuario | 'novo' | null>(null)
  /** Confirmação da exclusão: apagar cadastro é ação sem volta. */
  const [excluindo, setExcluindo] = useState<Usuario | null>(null)
  const [promovendo, setPromovendo] = useState(false)
  const [filtro, setFiltro] = useState('')
  const avisos = useAvisos()
  const [parametros, definirParametros] = useSearchParams()

  /*
   * A lista de habilitados manda para cá quem ela não encontrou no cadastro,
   * por `?novo=<e-mail>`. Abrir o formulário já com o endereço poupa quem veio
   * de lá de digitá-lo de novo — e de digitá-lo diferente.
   */
  const [emailDoLink, setEmailDoLink] = useState('')
  const novoDoLink = parametros.get('novo')
  useEffect(() => {
    if (novoDoLink === null) return
    // Guardar antes de limpar a URL: o formulário só monta na renderização
    // seguinte, quando o parâmetro já não existe mais.
    setEmailDoLink(novoDoLink)
    setEditando('novo')
    definirParametros({}, { replace: true })
  }, [novoDoLink, definirParametros])

  /*
   * Com a base do tribunal inteira aqui dentro, rolar até alguém deixou de ser
   * caminho. A busca é local: a lista já veio inteira, e uma ida ao servidor a
   * cada letra digitada só atrasaria a resposta.
   */
  const termos = semAcento(filtro).split(/\s+/).filter(Boolean)
  const visiveis = (usuarios ?? []).filter((usuario) => {
    if (termos.length === 0) return true
    const alvo = semAcento(
      `${usuario.nome} ${usuario.email} ${usuario.unidadeLotacao ?? ''} ${usuario.areaAtuacao ?? ''}`,
    )
    return termos.every((termo) => alvo.includes(termo))
  })

  const carregar = useCallback(async () => {
    try {
      setUsuarios(await api.get<Usuario[]>('/api/usuarios'))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar os usuários.')
    }
  }, [])

  // Ao terminar, a lista é recarregada: é ali que a pessoa confere o resultado.
  const atualizacao = useAtualizacaoDaBase(carregar)

  useEffect(() => {
    void carregar()
  }, [carregar])

  /**
   * Exclusão de verdade, com a desativação como saída.
   *
   * O cadastro nasce de cargas em lote — planilha com e-mail errado, importação
   * de unidade inteira —, e desativar deixaria a lista cheia de fantasmas. Mas
   * quem já emitiu, foi reconhecido ou está numa lista não pode sumir: aí o
   * servidor recusa com o motivo, e a tela oferece desativar.
   */
  async function excluir(usuario: Usuario) {
    setErro(null)
    setExcluindo(null)
    try {
      await api.remover(`/api/usuarios/${usuario.id}`)
      avisos.sucesso(`${usuario.nome} foi excluído`, 'O cadastro dele deixou de existir.')
      await carregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao excluir o usuário.')
    }
  }

  async function alternar(usuario: Usuario) {
    setErro(null)
    try {
      await api.put(`/api/usuarios/${usuario.id}/ativacao`, { ativo: !usuario.ativo })
      avisos.sucesso(
        usuario.ativo ? `${usuario.nome} desativado` : `${usuario.nome} reativado`,
        usuario.ativo ? 'Ele deixa de conseguir entrar imediatamente.' : undefined,
      )
      await carregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao alterar o usuário.')
    }
  }

  return (
    <div className="pagina">
      <header className="cabecalho-pagina">
        <div>
          <span className="rotulo">Superadministração</span>
          <h1 className="titulo-pagina" style={{ marginTop: 4 }}>
            Usuários do sistema
          </h1>
          <p>
            Quem entra, com que papel e lotado onde. O e-mail corporativo é obrigatório porque é
            por ele que o login reconhece a pessoa e o sistema a liga aos reconhecimentos, às
            listas de habilitados e aos certificados emitidos.
          </p>
        </div>
        <div className="acoes">
          <BotaoAtualizarBase atualizacao={atualizacao} />
          <button
            type="button"
            className="botao botao-neutro"
            onClick={() => setPromovendo(true)}
          >
            <Icone nome="equipe" tamanho={16} />
            Promover superadmin
          </button>
          <button type="button" className="botao" onClick={() => setEditando('novo')}>
            <Icone nome="mais" tamanho={16} />
            Novo usuário
          </button>
        </div>
      </header>

      <PainelAtualizacaoDaBase atualizacao={atualizacao} />
      <ModalAtualizacaoDaBase atualizacao={atualizacao} />

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {!usuarios ? (
        !erro && <Carregando />
      ) : usuarios.length === 0 ? (
        <div className="bloco">
          <EstadoVazio
            titulo="Nenhum usuário cadastrado"
            descricao="Cadastre as pessoas que vão administrar o prêmio. Magistrados e servidores também podem ser cadastrados aqui até a integração com o EGESP."
            acao={
              <button type="button" className="botao" onClick={() => setEditando('novo')}>
                Cadastrar o primeiro
              </button>
            }
          />
        </div>
      ) : (
        <div className="bloco">
          <div className="bloco-cabecalho">
            <div>
              <h2 className="titulo-secao">
                {visiveis.length === usuarios.length
                  ? `${usuarios.length} usuário(s)`
                  : `${visiveis.length} de ${usuarios.length} usuário(s)`}
              </h2>
              <p className="apoio">
                A busca olha nome, e-mail e lotação, sem ligar para acento nem maiúsculas.
              </p>
            </div>
            <div className="campo" style={{ margin: 0, minWidth: 260 }}>
              <label htmlFor="filtro-usuario" className="rotulo">
                Buscar
              </label>
              <input
                id="filtro-usuario"
                placeholder="Nome, e-mail ou lotação…"
                value={filtro}
                onChange={(evento) => setFiltro(evento.target.value)}
              />
            </div>
          </div>

          {visiveis.length === 0 ? (
            <EstadoVazio
              titulo="Ninguém com esse termo"
              descricao="Tente outro trecho do nome, do e-mail ou da lotação — sobrenome costuma encontrar mais."
            />
          ) : (
          <div className="tabela-rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Usuário</th>
                  <th>Lotação</th>
                  <th>Papéis</th>
                  <th>Acesso</th>
                  <th className="direita">Ações</th>
                </tr>
              </thead>
              <tbody>
                {visiveis.map((usuario) => (
                  <tr key={usuario.id} style={{ opacity: usuario.ativo ? 1 : 0.5 }}>
                    <td>
                      <div className="unidade-nome" style={{ fontSize: 15, margin: 0 }}>
                        {usuario.nome}
                      </div>
                      <div className="secundaria">{usuario.email}</div>
                      {usuario.cpfMascarado && (
                        <div className="secundaria mono">{usuario.cpfMascarado}</div>
                      )}
                    </td>
                    <td className="secundaria">
                      {usuario.unidadeLotacao ?? '—'}
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        {usuario.papeis.map((papel) => (
                          <span
                            key={papel}
                            className={`papel-marca papel-${papel.toLowerCase()}`}
                          >
                            {ROTULO_PAPEL[papel]}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="secundaria">
                      {usuario.ativo ? 'Ativo' : 'Desativado'}
                      <div className="secundaria">
                        {usuario.temSenha ? 'entra com senha' : 'só pelo SSO'}
                      </div>
                      <div className="secundaria">desde {formatarData(usuario.criadoEm)}</div>
                    </td>
                    <td>
                      <div className="acoes acoes-direita">
                        <button
                          type="button"
                          className="botao botao-texto botao-pequeno"
                          onClick={() => setEditando(usuario)}
                        >
                          Editar
                        </button>
                        {!usuario.ativo && (
                          <button
                            type="button"
                            className="botao botao-neutro botao-pequeno"
                            onClick={() => void alternar(usuario)}
                          >
                            Reativar
                          </button>
                        )}
                        <button
                          type="button"
                          className="botao botao-perigo botao-pequeno"
                          onClick={() => setExcluindo(usuario)}
                        >
                          Excluir
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
            Excluir apaga o cadastro. Quem já emitiu certificado, foi reconhecido ou está em
            alguma lista de habilitados não pode ser apagado — nesse caso a tela oferece desativar,
            que tira o acesso na hora e preserva o histórico.
          </div>
        </div>
      )}

      {excluindo && (
        <Modal
          titulo={`Excluir ${excluindo.nome}?`}
          descricao={excluindo.email}
          aoFechar={() => setExcluindo(null)}
          rodape={
            <>
              <button
                type="button"
                className="botao botao-neutro"
                onClick={() => setExcluindo(null)}
              >
                Cancelar
              </button>
              {excluindo.ativo && (
                <button
                  type="button"
                  className="botao botao-neutro"
                  onClick={() => {
                    const alvo = excluindo
                    setExcluindo(null)
                    void alternar(alvo)
                  }}
                >
                  Só desativar
                </button>
              )}
              <button
                type="button"
                className="botao botao-perigo"
                onClick={() => void excluir(excluindo)}
              >
                Excluir
              </button>
            </>
          }
        >
          <Aviso tom="atencao" titulo="Apagar o cadastro não tem volta">
            <p>
              Se esta pessoa já emitiu certificado, foi reconhecida numa edição ou está em alguma
              lista de habilitados, a exclusão é recusada — e a mensagem diz qual é o vínculo.
              Nesses casos o caminho é desativar, que tira o acesso na hora e preserva o histórico.
            </p>
          </Aviso>
        </Modal>
      )}

      {editando && (
        <ModalUsuario
          usuario={editando === 'novo' ? null : editando}
          emailInicial={editando === 'novo' ? emailDoLink : ''}
          aoFechar={() => {
            setEditando(null)
            setEmailDoLink('')
          }}
          aoSalvar={async () => {
            setEditando(null)
            await carregar()
          }}
        />
      )}

      {promovendo && (
        <ModalPromocao
          usuarios={usuarios ?? []}
          aoFechar={() => setPromovendo(false)}
          aoPromover={async (nome) => {
            setPromovendo(false)
            avisos.sucesso(`${nome} agora é superadministrador`)
            await carregar()
          }}
        />
      )}
    </div>
  )
}

function ModalUsuario({
  usuario,
  emailInicial = '',
  aoFechar,
  aoSalvar,
}: {
  usuario: Usuario | null
  /** Vem de quem mandou cadastrar alguém que a busca não encontrou. */
  emailInicial?: string
  aoFechar: () => void
  aoSalvar: () => Promise<void>
}) {
  const [email, setEmail] = useState(emailInicial)
  const [nome, setNome] = useState(usuario?.nome ?? '')
  const [cpf, setCpf] = useState('')
  const [unidade, setUnidade] = useState(usuario?.unidadeLotacao ?? '')
  /** As unidades cadastradas, para escolher a lotação em vez de digitá-la. */
  const [unidades, setUnidades] = useState<Unidade[] | null>(null)
  const [papeis, setPapeis] = useState<Papel[]>(
    usuario?.papeis.filter((p) => p !== 'SUPERADMIN') ?? ['SERVIDOR'],
  )
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  useEffect(() => {
    let ativo = true
    api
      .get<Unidade[]>('/api/unidades')
      .then((lista) => ativo && setUnidades(lista))
      // A lista é conveniência: sem ela o campo continua aceitando o texto
      // digitado, e o cadastro não fica bloqueado por causa dela.
      .catch(() => ativo && setUnidades([]))
    return () => {
      ativo = false
    }
  }, [])

  function alternarPapel(papel: Papel) {
    setPapeis((atual) =>
      atual.includes(papel) ? atual.filter((p) => p !== papel) : [...atual, papel],
    )
  }

  async function salvar() {
    setSalvando(true)
    setErro(null)
    // Na edição o e-mail não vai: ele é a chave que liga o usuário a tudo que
    // já fez, e o contrato de atualização nem tem o campo. O CPF é opcional;
    // em branco na edição, o servidor mantém o atual.
    const corpo = {
      ...(usuario ? {} : { email }),
      nome,
      cpf: cpf || null,
      unidadeLotacao: unidade || null,
      papeis,
      senha: senha || null,
    }
    try {
      if (usuario) {
        await api.put(`/api/usuarios/${usuario.id}`, corpo)
      } else {
        await api.post('/api/usuarios', corpo)
      }
      await aoSalvar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao salvar o usuário.')
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo={usuario ? `Editar ${usuario.nome}` : 'Novo usuário'}
      descricao={
        usuario
          ? 'O e-mail não muda: é a chave que liga o usuário a tudo que ele já fez no sistema.'
          : 'O e-mail corporativo é obrigatório — é por ele que o login reconhece a pessoa e o sistema encontra os reconhecimentos e certificados dela.'
      }
      aoFechar={aoFechar}
      rodape={
        <>
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            Cancelar
          </button>
          <button type="button" className="botao" disabled={salvando} onClick={() => void salvar()}>
            {salvando && <span className="giro" />}
            {salvando ? 'Salvando…' : 'Salvar'}
          </button>
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      <div className="campo">
        <label htmlFor="email-usuario">E-mail corporativo</label>
        {usuario ? (
          <input id="email-usuario" type="email" value={usuario.email} readOnly disabled />
        ) : (
          <input
            id="email-usuario"
            type="email"
            placeholder="nome@tjgo.jus.br"
            value={email}
            onChange={(evento) => setEmail(evento.target.value)}
          />
        )}
        <span className="campo-dica">
          {usuario
            ? 'É a chave do usuário e não muda.'
            : 'O mesmo do login corporativo — é por ele que a pessoa é reconhecida ao entrar.'}
        </span>
      </div>

      <div className="campo">
        <label htmlFor="nome">Nome</label>
        <input id="nome" value={nome} onChange={(evento) => setNome(evento.target.value)} />
      </div>

      <div className="campo">
        <label htmlFor="cpf">CPF (opcional)</label>
        <input
          id="cpf"
          className="mono"
          placeholder={usuario?.cpfMascarado ?? '000.000.000-00'}
          value={cpf}
          onChange={(evento) => setCpf(evento.target.value)}
        />
        <span className="campo-dica">
          {usuario?.cpfMascarado
            ? 'Em branco mantém o atual. Só informativo.'
            : 'Só informativo — o sistema não identifica ninguém pelo CPF.'}
        </span>
      </div>

      {/* Escolhida na lista, e não digitada: a lotação casa com a unidade pelo
          nome, e um acento a mais deixaria a pessoa lotada em lugar nenhum. */}
      <div className="campo">
        <label htmlFor="unidade">Unidade de lotação</label>
        <input
          id="unidade"
          list="unidades-cadastradas"
          placeholder={
            unidades === null
              ? 'Carregando as unidades…'
              : unidades.length === 0
                ? 'Nenhuma unidade cadastrada ainda'
                : 'Comece a digitar o nome da unidade…'
          }
          value={unidade}
          onChange={(evento) => setUnidade(evento.target.value)}
        />
        <datalist id="unidades-cadastradas">
          {(unidades ?? []).map((u) => (
            <option key={u.id} value={u.nome} />
          ))}
        </datalist>
        <span className="campo-dica">
          {unidades !== null && unidades.length > 0
            ? `${unidades.length} unidade(s) cadastrada(s). Faltando alguma, cadastre-a em Sincronização de Unidades.`
            : 'As unidades vêm do cadastro; cadastre-as em Sincronização de Unidades.'}
        </span>
      </div>

      <div className="campo">
        <label>Permissão</label>
        <div style={{ display: 'flex', gap: 'var(--e4)', flexWrap: 'wrap', marginTop: 6 }}>
          {PAPEIS.map((papel) => (
            <label key={papel} className="replicar" style={{ margin: 0 }}>
              <input
                type="checkbox"
                checked={papeis.includes(papel)}
                onChange={() => alternarPapel(papel)}
              />
              {ROTULO_PAPEL[papel]}
            </label>
          ))}
        </div>
        <span className="campo-dica">
          Papéis acumulam: quem é administrador e magistrado vê os dois menus, sem trocar de
          contexto.
        </span>
      </div>

      <div className="campo">
        <label htmlFor="senha-usuario">{usuario ? 'Nova senha (opcional)' : 'Senha'}</label>
        <input
          id="senha-usuario"
          type="password"
          autoComplete="new-password"
          value={senha}
          onChange={(evento) => setSenha(evento.target.value)}
        />
        <span className="campo-dica">
          {usuario
            ? 'Deixe em branco para manter a senha atual.'
            : 'Ao menos 8 caracteres. Em branco, o usuário existe mas só entrará pelo SSO.'}
        </span>
      </div>
    </Modal>
  )
}

/** Sem acento e sem caixa: o RH grava em maiúsculas, e ninguém digita "JOÃO". */
function semAcento(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
}

/** Quantas sugestões mostrar de uma vez: a lista cresce com a base inteira. */
const SUGESTOES_MAXIMAS = 8

function ModalPromocao({
  usuarios,
  aoFechar,
  aoPromover,
}: {
  usuarios: Usuario[]
  aoFechar: () => void
  aoPromover: (nome: string) => Promise<void>
}) {
  const [email, setEmail] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  /*
   * Sugestões enquanto digita, pelo nome ou pelo e-mail. Saem da lista que a
   * tela já carregou — sem ir ao servidor a cada tecla. Só aparecem quem pode
   * ser promovido: ativo e ainda não superadministrador; oferecer os outros
   * seria sugerir algo que o servidor vai recusar.
   */
  const termo = semAcento(email.trim())
  const escolhido = usuarios.find((u) => u.email.toLowerCase() === email.trim().toLowerCase())
  const sugestoes =
    termo.length < 2 || escolhido
      ? []
      : usuarios
          .filter((u) => u.ativo && !u.papeis.includes('SUPERADMIN'))
          .filter((u) => semAcento(u.nome).includes(termo) || u.email.toLowerCase().includes(termo))
          .slice(0, SUGESTOES_MAXIMAS)

  async function promover() {
    setSalvando(true)
    setErro(null)
    try {
      const promovido = await api.post<Usuario>('/api/usuarios/superadmins', { email })
      await aoPromover(promovido.nome)
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao promover.')
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo="Promover a superadministrador"
      descricao="Escolha quem já está cadastrado. Superadministradores podem cadastrar usuários e promover outros."
      aoFechar={aoFechar}
      rodape={
        <>
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            Cancelar
          </button>
          <button
            type="button"
            className="botao"
            disabled={salvando || !email.trim()}
            onClick={() => void promover()}
          >
            {salvando && <span className="giro" />}
            {salvando ? 'Promovendo…' : 'Promover'}
          </button>
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      <div className="campo">
        <label htmlFor="email-promocao">Usuário</label>
        <input
          id="email-promocao"
          value={email}
          autoComplete="off"
          placeholder="Comece a digitar o nome ou o e-mail…"
          onChange={(evento) => setEmail(evento.target.value)}
        />
        <span className="campo-dica">
          {escolhido
            ? `${escolhido.nome}${escolhido.papeis.includes('SUPERADMIN') ? ' já é superadministrador.' : ''}`
            : 'Quem ainda não tem cadastro precisa ser criado primeiro — promover concede um papel a alguém que já existe.'}
        </span>

        {sugestoes.length > 0 && (
          <div className="lista-usuarios" role="listbox" aria-label="Sugestões">
            {sugestoes.map((usuario) => (
              <button
                key={usuario.id}
                type="button"
                role="option"
                aria-selected={false}
                className="usuario-opcao"
                onClick={() => setEmail(usuario.email)}
              >
                <span>
                  <span className="principal">{usuario.nome}</span>
                  <br />
                  <span className="secundaria">{usuario.email}</span>
                </span>
                <Icone nome="seta" tamanho={16} />
              </button>
            ))}
          </div>
        )}

        {termo.length >= 2 && !escolhido && sugestoes.length === 0 && (
          <p className="apoio">
            Ninguém que possa ser promovido com esse nome ou e-mail. Quem já é superadministrador
            ou está desativado não aparece.
          </p>
        )}
      </div>
    </Modal>
  )
}
