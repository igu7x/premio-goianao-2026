import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi } from '../api/cliente'
import type { Papel, Usuario } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio, Modal, formatarData } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'

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
  const [promovendo, setPromovendo] = useState(false)
  const avisos = useAvisos()

  const carregar = useCallback(async () => {
    try {
      setUsuarios(await api.get<Usuario[]>('/api/usuarios'))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar os usuários.')
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

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
            Quem entra, com que papel e lotado onde. O CPF é obrigatório porque é por ele que o
            sistema liga a pessoa aos reconhecimentos, às listas de habilitados e aos certificados
            emitidos.
          </p>
        </div>
        <div className="acoes">
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

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {!usuarios ? (
        <Carregando />
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
                {usuarios.map((usuario) => (
                  <tr key={usuario.id} style={{ opacity: usuario.ativo ? 1 : 0.5 }}>
                    <td>
                      <div className="unidade-nome" style={{ fontSize: 15, margin: 0 }}>
                        {usuario.nome}
                      </div>
                      <div className="secundaria">{usuario.email}</div>
                      <div className="secundaria mono">{usuario.cpfMascarado}</div>
                    </td>
                    <td className="secundaria">
                      {usuario.unidadeLotacao ?? '—'}
                      {usuario.areaAtuacao && (
                        <div className="secundaria">área: {usuario.areaAtuacao}</div>
                      )}
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
                        <button
                          type="button"
                          className={
                            usuario.ativo
                              ? 'botao botao-perigo botao-pequeno'
                              : 'botao botao-neutro botao-pequeno'
                          }
                          onClick={() => void alternar(usuario)}
                        >
                          {usuario.ativo ? 'Desativar' : 'Reativar'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="bloco-rodape">
            Desativar tira o acesso na hora e preserva o histórico — nada do que a pessoa fez é
            apagado. Quando a integração com o EGESP entrar, este cadastro passa a ser alimentado
            por ela.
          </div>
        </div>
      )}

      {editando && (
        <ModalUsuario
          usuario={editando === 'novo' ? null : editando}
          aoFechar={() => setEditando(null)}
          aoSalvar={async () => {
            setEditando(null)
            await carregar()
          }}
        />
      )}

      {promovendo && (
        <ModalPromocao
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
  aoFechar,
  aoSalvar,
}: {
  usuario: Usuario | null
  aoFechar: () => void
  aoSalvar: () => Promise<void>
}) {
  const [cpf, setCpf] = useState('')
  const [nome, setNome] = useState(usuario?.nome ?? '')
  const [email, setEmail] = useState(usuario?.email ?? '')
  const [unidade, setUnidade] = useState(usuario?.unidadeLotacao ?? '')
  const [area, setArea] = useState(usuario?.areaAtuacao ?? '')
  const [papeis, setPapeis] = useState<Papel[]>(
    usuario?.papeis.filter((p) => p !== 'SUPERADMIN') ?? ['SERVIDOR'],
  )
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  const ehMagistrado = papeis.includes('MAGISTRADO')

  function alternarPapel(papel: Papel) {
    setPapeis((atual) =>
      atual.includes(papel) ? atual.filter((p) => p !== papel) : [...atual, papel],
    )
  }

  async function salvar() {
    setSalvando(true)
    setErro(null)
    // Na edição o CPF não vai: ele é a chave que liga o usuário a tudo que já
    // fez, e o contrato de atualização nem tem o campo.
    const corpo = {
      ...(usuario ? {} : { cpf }),
      nome,
      email,
      unidadeLotacao: unidade || null,
      areaAtuacao: ehMagistrado ? area || null : null,
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
          ? 'O CPF não muda: é a chave que liga o usuário a tudo que ele já fez no sistema.'
          : 'O CPF é obrigatório — é por ele que o sistema encontra os reconhecimentos e certificados da pessoa.'
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

      {!usuario && (
        <div className="campo">
          <label htmlFor="cpf">CPF</label>
          <input
            id="cpf"
            className="mono"
            placeholder="000.000.000-00"
            value={cpf}
            onChange={(evento) => setCpf(evento.target.value)}
          />
        </div>
      )}

      <div className="campo">
        <label htmlFor="nome">Nome</label>
        <input id="nome" value={nome} onChange={(evento) => setNome(evento.target.value)} />
      </div>

      <div className="campo">
        <label htmlFor="email-usuario">E-mail</label>
        <input
          id="email-usuario"
          type="email"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
        />
        <span className="campo-dica">É com ele que a pessoa entra no sistema.</span>
      </div>

      <div className="campo">
        <label htmlFor="unidade">Unidade de lotação</label>
        <input
          id="unidade"
          value={unidade}
          onChange={(evento) => setUnidade(evento.target.value)}
        />
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

      {/* Só aparece para magistrado, porque só ali significa alguma coisa. */}
      {ehMagistrado && (
        <div className="campo">
          <label htmlFor="area">Área de atuação</label>
          <input
            id="area"
            placeholder="Cível, Criminal, Família…"
            value={area}
            onChange={(evento) => setArea(evento.target.value)}
          />
        </div>
      )}

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

function ModalPromocao({
  aoFechar,
  aoPromover,
}: {
  aoFechar: () => void
  aoPromover: (nome: string) => Promise<void>
}) {
  const [email, setEmail] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

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
      descricao="Informe o e-mail de quem já está cadastrado. Superadministradores podem cadastrar usuários e promover outros."
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
        <label htmlFor="email-promocao">E-mail do usuário</label>
        <input
          id="email-promocao"
          type="email"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
        />
        <span className="campo-dica">
          Quem ainda não tem cadastro precisa ser criado primeiro — promover concede um papel a
          alguém que já existe.
        </span>
      </div>
    </Modal>
  )
}
