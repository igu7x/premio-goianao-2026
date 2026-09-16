import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErroApi, lerToken, urlDaApi } from '../api/cliente'
import type { ImportacaoResponsaveis, Unidade, Usuario } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio, Modal } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'

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
  const [enviando, setEnviando] = useState(false)
  /** Relatório do último envio; fica na tela até a pessoa fechar, porque é onde
   *  aparecem as linhas que o arquivo não conseguiu gravar. */
  const [relatorio, setRelatorio] = useState<ImportacaoResponsaveis | null>(null)
  const arquivo = useRef<HTMLInputElement>(null)
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

  /**
   * Sobe a planilha de magistrados responsáveis.
   *
   * O multipart não passa pelo cliente de API: ele põe `Content-Type: json` em
   * tudo, e com isso o navegador não escreve o `boundary` — o servidor receberia
   * um corpo que não sabe separar. Aqui o cabeçalho é omitido de propósito.
   */
  async function enviarPlanilha(csv: File) {
    setEnviando(true)
    setErro(null)
    try {
      const corpo = new FormData()
      corpo.append('arquivo', csv)

      const resposta = await fetch(urlDaApi('/api/unidades/responsaveis/importar'), {
        method: 'POST',
        headers: { Authorization: `Bearer ${lerToken() ?? ''}` },
        body: corpo,
      })
      const dados = await resposta.json()
      if (!resposta.ok) {
        throw new ErroApi(resposta.status, dados?.mensagem ?? 'Falha ao importar a planilha.')
      }

      const lido = dados as ImportacaoResponsaveis
      setRelatorio(lido)
      avisos.sucesso(
        `${lido.designados} unidade(s) com responsável designado`,
        [
          lido.usuariosCriados > 0 ? `${lido.usuariosCriados} magistrado(s) criado(s)` : null,
          lido.reconhecimentos > 0
            ? `${lido.reconhecimentos} selo(s) gravado(s) na edição ${lido.edicaoAno}`
            : null,
          lido.erros.length > 0 ? `${lido.erros.length} linha(s) com erro` : null,
        ]
          .filter(Boolean)
          .join(' · ') || 'Tudo na planilha já estava gravado.',
      )
      await carregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao importar a planilha.')
    } finally {
      setEnviando(false)
      if (arquivo.current) {
        // Sem isto, reenviar o mesmo arquivo corrigido não dispara o onChange.
        arquivo.current.value = ''
      }
    }
  }

  /**
   * Gera um CSV de teste com as unidades desta tela.
   *
   * Os códigos são os do banco — é isso que faz o arquivo valer para testar: um
   * exemplo com código inventado falharia em todas as linhas e não provaria
   * nada. Os nomes e e-mails são fictícios, no domínio reservado `.example`,
   * para que ninguém confunda o teste com a lista de verdade.
   */
  function baixarModelo() {
    const comCodigo = (unidades ?? []).filter((u) => u.codigoSiedos !== null)
    if (comCodigo.length === 0) {
      setErro('Nenhuma unidade tem código do SIEDOS ainda: compare-as em Sincronização de '
        + 'Unidades antes de gerar o modelo.')
      return
    }

    const selos = ['bronze', 'prata', 'ouro', 'diamante']
    const linhas = comCodigo.map((unidade, indice) => {
      const numero = String(indice + 1).padStart(3, '0')
      return [
        `Magistrado de Teste ${numero}`,
        `magistrado.teste.${numero}@tjgo.example`,
        String(unidade.codigoSiedos),
        selos[indice % selos.length],
      ].join(';')
    })

    const csv = ['nome;email;unidade;selo', ...linhas].join('\n')
    // BOM: sem ele o Excel abre os acentos errados, e a planilha volta corrompida.
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'magistrados-responsaveis-exemplo.csv'
    link.click()
    URL.revokeObjectURL(url)

    avisos.sucesso(
      `Modelo com ${comCodigo.length} unidade(s)`,
      'Códigos reais desta base; nomes e e-mails fictícios, no domínio .example.',
    )
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
        {/* A lista de quem responde por cada unidade vem pronta, em planilha:
            designar uma a uma não é caminho com centenas de unidades. */}
        <div>
          <input
            ref={arquivo}
            id="planilha-responsaveis"
            type="file"
            accept=".csv,text/csv"
            style={{ display: 'none' }}
            onChange={(evento) => {
              const escolhido = evento.target.files?.[0]
              if (escolhido) void enviarPlanilha(escolhido)
            }}
          />
          <button
            type="button"
            className="botao"
            disabled={enviando}
            onClick={() => arquivo.current?.click()}
          >
            {enviando ? <span className="giro" /> : <Icone nome="enviar" tamanho={16} />}
            {enviando ? 'Importando…' : 'Subir CSV de magistrados'}
          </button>
          <div className="acoes acoes-direita" style={{ marginTop: 6 }}>
            <button
              type="button"
              className="botao botao-texto botao-pequeno"
              onClick={baixarModelo}
            >
              Baixar modelo de teste
            </button>
          </div>
          <div className="secundaria mono" style={{ marginTop: 4, textAlign: 'right' }}>
            nome;e-mail;código da unidade;selo
          </div>
        </div>
      </header>

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {!unidades ? (
        !erro && <Carregando />
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
                      {/* A unidade leva à página dela, onde estão os lotados do
                          RH e o cadastro deles em lote. */}
                      <Link to={`/unidades/${unidade.id}`} className="link-unidade">
                        <span className="unidade-nome" style={{ fontSize: 15, margin: 0 }}>
                          {unidade.nome}
                        </span>
                      </Link>
                      {unidade.codigoSiedos !== null && (
                        <div className="secundaria mono">código {unidade.codigoSiedos}</div>
                      )}
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
            Clique no nome da unidade para ver quem o RH aponta como lotado nela e cadastrar todos
            de uma vez. O responsável só vê a unidade na tela dele quando ela foi reconhecida na
            edição vigente — fora disso não existe lista de habilitados para gerenciar.
          </div>
        </div>
      )}

      {/* O relatório é a única coisa que sobra do envio: é onde estão as linhas
          que não entraram, com o texto original, para corrigir a planilha. */}
      {relatorio && (
        <Modal
          largo
          titulo="Planilha importada"
          descricao={`${relatorio.linhasLidas} linha(s) lida(s) · selos gravados na edição ${relatorio.edicaoAno}.`}
          aoFechar={() => setRelatorio(null)}
          rodape={
            <button type="button" className="botao" onClick={() => setRelatorio(null)}>
              Fechar
            </button>
          }
        >
          <Aviso tom={relatorio.erros.length > 0 ? 'atencao' : 'sucesso'}>
            <ul>
              <li>{relatorio.designados} unidade(s) com responsável designado.</li>
              <li>{relatorio.usuariosCriados} magistrado(s) criado(s) no cadastro de usuários.</li>
              {relatorio.papelConcedido > 0 && (
                <li>
                  {relatorio.papelConcedido} já existia(m) e ganhou(aram) o papel de magistrado,
                  que a designação exige.
                </li>
              )}
              {relatorio.substituidos > 0 && (
                <li>{relatorio.substituidos} responsável(is) anterior(es) substituído(s).</li>
              )}
              {relatorio.jaEram > 0 && (
                <li>{relatorio.jaEram} já respondia(m) pela unidade — nada mudou.</li>
              )}
              <li>{relatorio.reconhecimentos} selo(s) gravado(s) como reconhecimento.</li>
            </ul>
          </Aviso>

          {relatorio.erros.length > 0 && (
            <>
              <p className="apoio">
                Estas linhas não entraram. As demais foram gravadas: corrija só estas e suba o
                arquivo de novo — reenviar o que já entrou não duplica nada.
              </p>
              <div className="tabela-rolagem" style={{ maxHeight: 320, overflowY: 'auto' }}>
                <table className="tabela">
                  <thead>
                    <tr>
                      <th>Linha</th>
                      <th>Conteúdo</th>
                      <th>Motivo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {relatorio.erros.map((linha) => (
                      <tr key={`${linha.linha}-${linha.motivo}`}>
                        <td className="mono">{linha.linha}</td>
                        <td className="secundaria mono">{linha.conteudo}</td>
                        <td>{linha.motivo}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </Modal>
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
