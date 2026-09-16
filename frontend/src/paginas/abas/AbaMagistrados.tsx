import { useCallback, useEffect, useId, useState } from 'react'
import { api, ErroApi } from '../../api/cliente'
import type {
  Edicao,
  Magistrado,
  PessoaDoRh,
  ResultadoDaBusca,
  RelatorioImportacao,
  Selo,
  SituacaoDoRh,
  UnidadeCadastrada,
} from '../../api/tipos'
import { Aviso, Carregando, EstadoVazio, Modal } from '../../componentes/Basicos'
import { Icone } from '../../componentes/Icone'
import { Disco } from '../../componentes/Selo'

const SELOS: Selo[] = ['BRONZE', 'PRATA', 'OURO', 'DIAMANTE']

interface LinhaReconhecimento {
  unidadeNome: string
  selo: Selo
}

/**
 * Cadastro dos reconhecidos (feature 004) com o modo somente-inclusao da
 * feature 009.
 *
 * A diferenca entre rascunho e vigente e visivel na tela: publicada a edicao,
 * some a acao de remover e o formulario passa a apenas acrescentar — porque
 * incluir nao altera certificado ja emitido, mas editar ou remover alteraria.
 */
export function AbaMagistrados({ edicao }: { edicao: Edicao }) {
  const [magistrados, setMagistrados] = useState<Magistrado[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [novo, setNovo] = useState(false)
  const [adicionandoEm, setAdicionandoEm] = useState<Magistrado | null>(null)
  const [importando, setImportando] = useState(false)

  const somenteInclusao = edicao.status === 'PUBLICADA'
  const congelada = !edicao.aceitaInclusoes

  const carregar = useCallback(async () => {
    try {
      setMagistrados(await api.get<Magistrado[]>(`/api/edicoes/${edicao.id}/magistrados`))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar os magistrados.')
    }
  }, [edicao.id])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function remover(magistrado: Magistrado) {
    if (!window.confirm(`Remover ${magistrado.nome} e seus reconhecimentos?`)) {
      return
    }
    setErro(null)
    try {
      await api.remover(`/api/edicoes/${edicao.id}/magistrados/${magistrado.id}`)
      await carregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao remover.')
    }
  }

  if (!magistrados) {
    return erro ? <Aviso tom="erro">{erro}</Aviso> : <Carregando />
  }

  return (
    <>
      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {congelada && (
        <div style={{ marginBottom: 'var(--e5)' }}>
          <Aviso tom="informacao">
            Esta edição está publicada e não é a vigente: o cadastro está congelado. Inclusões só
            acontecem em rascunho ou na edição vigente.
          </Aviso>
        </div>
      )}

      {somenteInclusao && !congelada && (
        <div style={{ marginBottom: 'var(--e5)' }}>
          <Aviso tom="atencao" titulo="Edição vigente: apenas inclusões">
            <p>
              Dá para acrescentar um magistrado que faltou ou uma nova unidade a quem já está
              cadastrado. Editar ou remover o que existe fica bloqueado, para não alterar
              certificados já emitidos.
            </p>
          </Aviso>
        </div>
      )}

      <div className="bloco">
        <div className="bloco-cabecalho">
          <div>
            <h2 className="titulo-secao">Magistrados reconhecidos</h2>
            <p className="apoio">
              {magistrados.length} cadastrado(s) na edição {edicao.ano}
            </p>
          </div>
          <div className="acoes">
            {!somenteInclusao && (
              <button
                type="button"
                className="botao botao-neutro"
                onClick={() => setImportando(true)}
              >
                <Icone nome="planilha" tamanho={16} />
                Importar planilha
              </button>
            )}
            <button
              type="button"
              className="botao"
              disabled={congelada}
              onClick={() => setNovo(true)}
            >
              <Icone nome="mais" tamanho={16} />
              Novo magistrado
            </button>
          </div>
        </div>

        {magistrados.length === 0 ? (
          <EstadoVazio
            titulo="Nenhum magistrado reconhecido"
            descricao="O sistema não calcula vencedores: quem venceu, por qual unidade e com qual selo é informação cadastrada aqui."
          />
        ) : (
          <div className="tabela-rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Magistrado</th>
                  <th>E-mail</th>
                  <th>Reconhecimentos</th>
                  <th className="direita">Ações</th>
                </tr>
              </thead>
              <tbody>
                {magistrados.map((magistrado) => (
                  <tr key={magistrado.id}>
                    <td>
                      <div className="unidade-nome" style={{ fontSize: 16, margin: 0 }}>
                        {magistrado.nome}
                      </div>
                      <div className="secundaria">
                        {magistrado.reconhecimentos.length} reconhecimento(s)
                      </div>
                    </td>
                    <td className="secundaria">
                      {magistrado.email}
                      {magistrado.cpfFormatado && (
                        <div className="secundaria mono">{magistrado.cpfFormatado}</div>
                      )}
                    </td>
                    <td>
                      <div style={{ display: 'grid', gap: 6 }}>
                        {magistrado.reconhecimentos.map((r) => (
                          <div
                            key={r.id}
                            style={{ display: 'flex', gap: 'var(--e3)', alignItems: 'center' }}
                          >
                            <Disco selo={r.selo} tamanho="p" />
                            <span>{r.unidadeNome}</span>
                          </div>
                        ))}
                      </div>
                    </td>
                    <td>
                      <div className="acoes acoes-direita">
                        <button
                          type="button"
                          className="botao botao-texto botao-pequeno"
                          disabled={congelada}
                          onClick={() => setAdicionandoEm(magistrado)}
                        >
                          Adicionar unidade
                        </button>
                        {!somenteInclusao && (
                          <button
                            type="button"
                            className="botao botao-perigo botao-pequeno"
                            onClick={() => void remover(magistrado)}
                          >
                            <Icone nome="remover" tamanho={15} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {novo && (
        <ModalMagistrado
          edicaoId={edicao.id}
          aoFechar={() => setNovo(false)}
          aoSalvar={async () => {
            setNovo(false)
            await carregar()
          }}
        />
      )}

      {adicionandoEm && (
        <ModalNovaUnidade
          edicaoId={edicao.id}
          magistrado={adicionandoEm}
          aoFechar={() => setAdicionandoEm(null)}
          aoSalvar={async () => {
            setAdicionandoEm(null)
            await carregar()
          }}
        />
      )}

      {importando && (
        <ModalImportacao
          edicaoId={edicao.id}
          aoFechar={() => setImportando(false)}
          aoImportar={carregar}
        />
      )}
    </>
  )
}

/* ------------------------------------------------------------------ */
/* Selecao de unidade entre as cadastradas                             */
/* ------------------------------------------------------------------ */

/**
 * A unidade nunca e digitada livremente (004/RF-1): o administrador escolhe
 * entre as unidades cadastradas, e o nome vai para o banco exatamente como esta
 * no cadastro.
 *
 * Antes a lista vinha do RH ao vivo. Quando a API corporativa falhava, o campo
 * aparecia vazio e sem aviso — e o administrador nao tinha como saber que o
 * problema nao era dele. As unidades entram no sistema pela sincronizacao, com
 * codigo; e dali que a escolha deve sair.
 */
function SeletorDeUnidade({
  valor,
  aoEscolher,
}: {
  valor: string
  aoEscolher: (nome: string) => void
}) {
  const [unidades, setUnidades] = useState<UnidadeCadastrada[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const id = useId()

  useEffect(() => {
    let ativo = true
    api
      .get<UnidadeCadastrada[]>('/api/unidades/cadastradas')
      .then((lista) => ativo && setUnidades(lista))
      .catch((e: unknown) => {
        if (!ativo) return
        setUnidades([])
        setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar as unidades.')
      })
    return () => {
      ativo = false
    }
  }, [])

  return (
    <div className="campo">
      <label htmlFor={id}>Unidade judiciária</label>
      <select
        id={id}
        value={valor}
        disabled={unidades === null}
        onChange={(evento) => aoEscolher(evento.target.value)}
      >
        <option value="">
          {unidades === null
            ? 'Carregando as unidades…'
            : unidades.length === 0
              ? 'Nenhuma unidade cadastrada'
              : 'Selecione a unidade…'}
        </option>
        {unidades?.map((unidade) => (
          <option key={unidade.id} value={unidade.nome}>
            {unidade.nome}
            {unidade.codigoSiedos !== null ? ` — ${unidade.codigoSiedos}` : ''}
          </option>
        ))}
      </select>
      <span className="campo-dica">
        {erro
          ? erro
          : unidades !== null && unidades.length === 0
            ? 'Cadastre as unidades em Sincronização de Unidades antes de reconhecer alguém.'
            : 'Unidades cadastradas no sistema; o nome é salvo exatamente como está no cadastro.'}
      </span>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Escolha da pessoa no RH                                             */
/* ------------------------------------------------------------------ */

/** O backend ignora termo menor que isto; avisar antes evita a lista vazia que
 *  parece "ninguém encontrado". */
const MINIMO_DO_TERMO = 3

/** Tempo entre a última tecla e a consulta. Curto o bastante para parecer
 *  imediato, longo o bastante para não pedir uma busca por letra digitada. */
const ESPERA_DA_BUSCA = 350

/**
 * Busca da pessoa pelo nome, no lugar do e-mail digitado.
 *
 * Digitar o e-mail é o erro mais caro do cadastro: uma letra trocada só aparece
 * meses depois, quando o magistrado tenta emitir e não acha nada seu — e a
 * edição já está publicada, onde editar é proibido (009/RF-3).
 */
function BuscaDePessoa({
  ocupado,
  aoEscolher,
  aoFalhar,
}: {
  ocupado: boolean
  aoEscolher: (pessoa: PessoaDoRh) => void
  aoFalhar: (mensagem: string) => void
}) {
  const [termo, setTermo] = useState('')
  const [resultados, setResultados] = useState<PessoaDoRh[] | null>(null)
  const [rhRespondeu, setRhRespondeu] = useState(true)
  const [buscando, setBuscando] = useState(false)

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
      // No sistema e no RH: quem entrou por planilha pode não estar no RH do
      // jeito que foi escrito, e a busca dizia "ninguém" sobre quem já existe.
      api
        .get<ResultadoDaBusca>(`/api/pessoas?termo=${encodeURIComponent(alvo)}`)
        .then((resultado) => {
          if (!ativo) return
          setResultados(resultado.pessoas)
          setRhRespondeu(resultado.rhRespondeu)
          setBuscando(false)
        })
        .catch((e) => {
          if (!ativo) return
          aoFalhar(e instanceof ErroApi ? e.message : 'Falha ao consultar o RH.')
          setBuscando(false)
        })
    }, ESPERA_DA_BUSCA)

    return () => {
      ativo = false
      clearTimeout(relogio)
    }
  }, [termo, aoFalhar])

  return (
    <div className="campo">
      <label htmlFor="busca-rh">Magistrado</label>
      <input
        id="busca-rh"
        value={termo}
        autoComplete="off"
        placeholder="Parte do nome ou do e-mail"
        onChange={(evento) => setTermo(evento.target.value)}
      />
      <span className="campo-dica">
        Procura nos usuários já cadastrados e no RH. Escolher da lista garante o e-mail certo — é
        ele que identifica o magistrado no login e na emissão. A partir de {MINIMO_DO_TERMO} letras.
      </span>

      {buscando && <Carregando texto="Procurando no sistema e no RH…" />}

      {!buscando && !rhRespondeu && (
        <p className="apoio">
          O RH não respondeu agora: a lista mostra só quem já está cadastrado no sistema.
        </p>
      )}

      {!buscando && resultados?.length === 0 && (
        <p className="apoio">
          Ninguém com esse nome no sistema {rhRespondeu ? 'nem no RH' : ''}. Tente outro trecho —
          sobrenome costuma encontrar mais.
        </p>
      )}

      {resultados && resultados.length > 0 && (
        <div className="lista-usuarios">
          {resultados.map((pessoa) => (
            <button
              key={`${pessoa.origem}-${pessoa.email ?? pessoa.matricula ?? pessoa.nome}`}
              type="button"
              className="usuario-opcao"
              disabled={ocupado}
              onClick={() => aoEscolher(pessoa)}
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
                  {pessoa.origem === 'SISTEMA' ? 'no sistema' : 'RH'}
                </span>
                <br />
                <span className="secundaria mono">
                  {pessoa.origem === 'SISTEMA'
                    ? pessoa.email
                    : `matrícula ${pessoa.matricula ?? '—'}`}
                  {pessoa.cpfMascarado ? ` · ${pessoa.cpfMascarado}` : ''}
                </span>
              </span>
              <Icone nome="seta" tamanho={16} />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

/** Quem foi escolhido, com o e-mail que veio do RH (ou do AD) à vista: é o
 *  único momento em que dá para conferir antes de gravar. */
function PessoaConfirmada({
  pessoa,
  aoTrocar,
}: {
  pessoa: PessoaDoRh
  aoTrocar: () => void
}) {
  return (
    <div className="campo">
      <span className="rotulo">Magistrado escolhido</span>
      <div className="pessoa-escolhida">
        <div>
          <div className="principal">{pessoa.nome}</div>
          <div className="secundaria">{pessoa.email ?? '— sem e-mail corporativo —'}</div>
          <div className="secundaria mono">
            matrícula {pessoa.matricula ?? '—'}
            {pessoa.cpfMascarado ? ` · ${pessoa.cpfMascarado}` : ''}
          </div>
        </div>
        <button type="button" className="botao botao-neutro botao-pequeno" onClick={aoTrocar}>
          Trocar
        </button>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Novo magistrado                                                     */
/* ------------------------------------------------------------------ */

function ModalMagistrado({
  edicaoId,
  aoFechar,
  aoSalvar,
}: {
  edicaoId: number
  aoFechar: () => void
  aoSalvar: () => Promise<void>
}) {
  const [email, setEmail] = useState('')
  const [nome, setNome] = useState('')
  const [cpf, setCpf] = useState('')
  const [linhas, setLinhas] = useState<LinhaReconhecimento[]>([
    { unidadeNome: '', selo: 'OURO' },
  ])
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)
  /** Nulo enquanto a consulta não voltou: não dá para escolher entre busca e
   *  campo digitado antes de saber se o RH responde neste ambiente. */
  const [rhDisponivel, setRhDisponivel] = useState<boolean | null>(null)
  const [escolhida, setEscolhida] = useState<PessoaDoRh | null>(null)
  const [consultandoPessoa, setConsultandoPessoa] = useState(false)

  useEffect(() => {
    let ativo = true
    api
      .get<SituacaoDoRh>('/api/rh/pessoas/situacao')
      .then((situacao) => ativo && setRhDisponivel(situacao.disponivel))
      // Falha na consulta não pode travar o cadastro: cai no e-mail digitado,
      // que é o que sempre funcionou.
      .catch(() => ativo && setRhDisponivel(false))
    return () => {
      ativo = false
    }
  }, [])

  /**
   * A busca devolve o e-mail que o RH tem; quando ele não tem, o endereço vem
   * do AD — e isso é uma consulta por pessoa, cara demais para rodar a cada
   * tecla. Por isso a escolha dispara uma segunda chamada: é dela que sai o
   * e-mail que vai para o banco.
   */
  async function escolher(pessoa: PessoaDoRh) {
    // Quem já está no sistema tem o e-mail que o login reconhece: não há o que
    // completar no RH, e consultar por matrícula falharia para quem não tem.
    if (pessoa.origem === 'SISTEMA') {
      setEscolhida(pessoa)
      setEmail(pessoa.email ?? '')
      setNome(pessoa.nome)
      return
    }
    if (pessoa.matricula === null) {
      return
    }
    setConsultandoPessoa(true)
    setErro(null)
    try {
      const completa = await api.get<PessoaDoRh>(`/api/rh/pessoas/${pessoa.matricula}`)
      setEscolhida(completa)
      setEmail(completa.email ?? '')
      setNome(completa.nome)
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao consultar a pessoa no RH.')
    } finally {
      setConsultandoPessoa(false)
    }
  }

  function trocarPessoa() {
    setEscolhida(null)
    setEmail('')
    setNome('')
  }

  // Sem e-mail a pessoa não seria reconhecida no login: cadastrá-la seria criar
  // um reconhecimento que ninguém consegue emitir (DI-24).
  const semEmail = escolhida !== null && !escolhida.temEmail
  const faltaEscolher = rhDisponivel === true && escolhida === null

  async function salvar() {
    setSalvando(true)
    setErro(null)
    try {
      await api.post(`/api/edicoes/${edicaoId}/magistrados`, {
        email,
        nome,
        cpf: cpf || null,
        reconhecimentos: linhas
          .filter((linha) => linha.unidadeNome)
          .map((linha) => ({ unidadeNome: linha.unidadeNome, selo: linha.selo })),
      })
      await aoSalvar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao cadastrar.')
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo="Novo magistrado reconhecido"
      descricao="O nome informado aqui é o que será impresso no certificado dele nesta edição."
      aoFechar={aoFechar}
      rodape={
        <>
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            Cancelar
          </button>
          <button
            type="button"
            className="botao"
            disabled={salvando || semEmail || faltaEscolher}
            onClick={() => void salvar()}
          >
            {salvando ? 'Salvando…' : 'Cadastrar'}
          </button>
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      {rhDisponivel === null ? (
        <Carregando texto="Verificando a consulta ao RH…" />
      ) : rhDisponivel ? (
        <>
          {escolhida ? (
            <PessoaConfirmada
              pessoa={escolhida}
              aoTrocar={trocarPessoa}
            />
          ) : (
            <BuscaDePessoa
              ocupado={consultandoPessoa}
              aoEscolher={(pessoa) => void escolher(pessoa)}
              aoFalhar={setErro}
            />
          )}

          {semEmail && (
            <Aviso tom="erro" titulo="Sem e-mail corporativo">
              <p>
                Nem o RH nem o Active Directory têm endereço para {escolhida?.nome}. Sem e-mail ela
                não seria reconhecida no login e não conseguiria emitir o certificado — o cadastro
                ficaria parado aqui até alguém descobrir, meses depois. Peça a criação da conta
                antes de cadastrá-la.
              </p>
            </Aviso>
          )}

          <div className="campo">
            <label htmlFor="nome">Nome completo</label>
            <input id="nome" value={nome} onChange={(evento) => setNome(evento.target.value)} />
            <span className="campo-dica">
              É este nome que vai impresso no certificado. O RH costuma devolver tudo em
              maiúsculas: corrija a grafia aqui se for o caso.
            </span>
          </div>
        </>
      ) : (
        <div className="linha-campos">
          <div className="campo">
            <label htmlFor="email">E-mail corporativo</label>
            <input
              id="email"
              type="email"
              value={email}
              placeholder="nome@tjgo.jus.br"
              onChange={(evento) => setEmail(evento.target.value)}
            />
            <span className="campo-dica">
              A busca no RH não está disponível neste ambiente; confira o endereço antes de salvar.
            </span>
          </div>
          <div className="campo">
            <label htmlFor="nome">Nome completo</label>
            <input id="nome" value={nome} onChange={(evento) => setNome(evento.target.value)} />
          </div>
        </div>
      )}

      <div className="campo">
        <label htmlFor="cpf">CPF (opcional)</label>
        <input
          id="cpf"
          className="mono"
          value={cpf}
          placeholder="000.000.000-00"
          onChange={(evento) => setCpf(evento.target.value)}
        />
        <span className="campo-dica">
          Quem identifica o magistrado no login é o e-mail; o CPF é só informativo.
        </span>
      </div>

      <fieldset>
        <span className="rotulo">Reconhecimentos</span>
        <div style={{ display: 'grid', gap: 'var(--e3)', marginTop: 'var(--e2)' }}>
          {linhas.map((linha, indice) => (
            <div
              key={indice}
              style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(0, 1fr) 140px auto',
                gap: 'var(--e2)',
                alignItems: 'end',
              }}
            >
              <SeletorDeUnidade
                valor={linha.unidadeNome}
                aoEscolher={(nomeUnidade) =>
                  setLinhas((atual) =>
                    atual.map((item, i) =>
                      i === indice ? { ...item, unidadeNome: nomeUnidade } : item,
                    ),
                  )
                }
              />
              <div className="campo">
                <label htmlFor={`selo-${indice}`}>Selo</label>
                <select
                  id={`selo-${indice}`}
                  value={linha.selo}
                  onChange={(evento) =>
                    setLinhas((atual) =>
                      atual.map((item, i) =>
                        i === indice ? { ...item, selo: evento.target.value as Selo } : item,
                      ),
                    )
                  }
                >
                  {SELOS.map((selo) => (
                    <option key={selo} value={selo}>
                      {selo.charAt(0) + selo.slice(1).toLowerCase()}
                    </option>
                  ))}
                </select>
              </div>
              <button
                type="button"
                className="botao botao-perigo botao-pequeno"
                disabled={linhas.length === 1}
                onClick={() => setLinhas((atual) => atual.filter((_, i) => i !== indice))}
              >
                <Icone nome="remover" tamanho={15} />
              </button>
            </div>
          ))}
        </div>
        <button
          type="button"
          className="botao botao-texto botao-pequeno"
          style={{ marginTop: 'var(--e2)' }}
          onClick={() => setLinhas((atual) => [...atual, { unidadeNome: '', selo: 'OURO' }])}
        >
          <Icone nome="mais" tamanho={15} />
          Outra unidade
        </button>
      </fieldset>
    </Modal>
  )
}

/* ------------------------------------------------------------------ */
/* Inclusao aditiva de unidade (feature 009)                           */
/* ------------------------------------------------------------------ */

function ModalNovaUnidade({
  edicaoId,
  magistrado,
  aoFechar,
  aoSalvar,
}: {
  edicaoId: number
  magistrado: Magistrado
  aoFechar: () => void
  aoSalvar: () => Promise<void>
}) {
  const [unidadeNome, setUnidadeNome] = useState('')
  const [selo, setSelo] = useState<Selo>('OURO')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  async function salvar() {
    setSalvando(true)
    setErro(null)
    try {
      await api.post(
        `/api/edicoes/${edicaoId}/magistrados/${magistrado.id}/reconhecimentos`,
        { unidadeNome, selo },
      )
      await aoSalvar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao incluir o reconhecimento.')
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo="Adicionar unidade"
      descricao={`Novo reconhecimento para ${magistrado.nome}.`}
      aoFechar={aoFechar}
      rodape={
        <>
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            Cancelar
          </button>
          <button type="button" className="botao" disabled={salvando} onClick={() => void salvar()}>
            {salvando ? 'Incluindo…' : 'Incluir'}
          </button>
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      <SeletorDeUnidade valor={unidadeNome} aoEscolher={setUnidadeNome} />

      <div className="campo">
        <label htmlFor="selo-novo">Selo</label>
        <select
          id="selo-novo"
          value={selo}
          onChange={(evento) => setSelo(evento.target.value as Selo)}
        >
          {SELOS.map((valor) => (
            <option key={valor} value={valor}>
              {valor.charAt(0) + valor.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
        <span className="campo-dica">
          Se a unidade já tiver outro selo por outro magistrado, o certificado do servidor usa o
          maior dos dois.
        </span>
      </div>
    </Modal>
  )
}

/* ------------------------------------------------------------------ */
/* Importacao em lote                                                  */
/* ------------------------------------------------------------------ */

function ModalImportacao({
  edicaoId,
  aoFechar,
  aoImportar,
}: {
  edicaoId: number
  aoFechar: () => void
  aoImportar: () => Promise<void>
}) {
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [relatorio, setRelatorio] = useState<RelatorioImportacao | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar() {
    if (!arquivo) return
    setEnviando(true)
    setErro(null)
    try {
      const dados = new FormData()
      dados.append('arquivo', arquivo)
      setRelatorio(
        await api.enviarArquivo<RelatorioImportacao>(
          `/api/edicoes/${edicaoId}/magistrados/importar`,
          dados,
        ),
      )
      await aoImportar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao importar.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      titulo="Importar reconhecidos"
      descricao="Planilha CSV com as colunas email, nome, unidade e selo — e, se quiser, cpf por último."
      aoFechar={aoFechar}
      rodape={
        <>
          <button type="button" className="botao botao-neutro" onClick={aoFechar}>
            Fechar
          </button>
          <button
            type="button"
            className="botao"
            disabled={!arquivo || enviando}
            onClick={() => void enviar()}
          >
            {enviando ? 'Importando…' : 'Importar'}
          </button>
        </>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      <div className="campo">
        <label htmlFor="csv">Arquivo CSV</label>
        <input
          id="csv"
          type="file"
          accept=".csv,text/csv"
          onChange={(evento) => setArquivo(evento.target.files?.[0] ?? null)}
        />
        <span className="campo-dica">
          Várias linhas com o mesmo e-mail viram um magistrado com várias unidades. Um magistrado
          com qualquer linha inválida é recusado inteiro e aparece no relatório; os demais entram.
        </span>
      </div>

      {relatorio && (
        <>
          <Aviso
            tom={relatorio.erros.length === 0 ? 'sucesso' : 'atencao'}
            titulo={`${relatorio.magistradosCriados} magistrado(s) e ${relatorio.reconhecimentosCriados} reconhecimento(s) importados`}
          >
            <p>
              {relatorio.linhasLidas} linha(s) lida(s), {relatorio.erros.length} com problema.
            </p>
          </Aviso>

          {relatorio.erros.length > 0 && (
            <div className="tabela-rolagem" style={{ maxHeight: 260, overflowY: 'auto' }}>
              <table className="tabela">
                <thead>
                  <tr>
                    <th>Linha</th>
                    <th>Motivo</th>
                    <th>Conteúdo</th>
                  </tr>
                </thead>
                <tbody>
                  {relatorio.erros.map((linha, indice) => (
                    <tr key={`${linha.linha}-${indice}`}>
                      <td className="mono">{linha.linha}</td>
                      <td>{linha.motivo}</td>
                      <td className="secundaria mono">{linha.conteudo}</td>
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
