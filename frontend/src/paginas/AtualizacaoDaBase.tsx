import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ErroApi } from '../api/cliente'
import type { EscopoDaAtualizacao, SituacaoDaAtualizacao } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Modal } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'

const BASE = '/api/usuarios/atualizacao-rh'

/** De quanto em quanto a tela pergunta como está. Curto o bastante para a barra
 *  andar, longo o bastante para não somar requisições à varredura. */
const INTERVALO_MS = 3000

const OPCOES: Array<{
  escopo: EscopoDaAtualizacao
  titulo: string
  descricao: string
  tempo: string
}> = [
  {
    escopo: 'TJGO',
    titulo: 'TJGO',
    descricao:
      'A estrutura sob a raiz do tribunal (código 600000009): as unidades administrativas. As varas não entram — elas ficam sob as comarcas.',
    tempo: 'alguns minutos',
  },
  {
    escopo: 'COMPLETA',
    titulo: 'Base completa do RH',
    descricao:
      'Todas as unidades do organograma, varas incluídas — cerca de 2.200. Traz todo o pessoal do tribunal.',
    tempo: 'pode passar de meia hora',
  },
]

/**
 * Atualização da base de usuários pelo RH.
 *
 * A varredura roda no servidor, em segundo plano: uma chamada por unidade e
 * outra por pessoa, a seis por segundo, não cabem numa requisição. A tela só
 * dispara e acompanha — e retoma o acompanhamento se a pessoa sair e voltar,
 * porque o estado vem sempre do servidor.
 *
 * O estado mora num hook para que o botão fique no cabeçalho e o progresso,
 * abaixo dele, em largura cheia — sem duas cópias perguntando ao servidor.
 */
export function useAtualizacaoDaBase(aoConcluir: () => Promise<void>) {
  const [situacao, setSituacao] = useState<SituacaoDaAtualizacao | null>(null)
  const [escolhendo, setEscolhendo] = useState(false)
  const [iniciando, setIniciando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const avisos = useAvisos()
  /** O estado anterior, para avisar uma vez só quando a varredura termina — e
   *  não a cada vez que a página abre com uma atualização antiga concluída. */
  const anterior = useRef<SituacaoDaAtualizacao['estado'] | null>(null)

  const consultar = useCallback(async () => {
    try {
      const atual = await api.get<SituacaoDaAtualizacao>(BASE)
      setSituacao(atual)

      if (anterior.current === 'EM_ANDAMENTO' && atual.estado === 'CONCLUIDA') {
        avisos.sucesso(
          'Base de usuários atualizada',
          `${atual.criados} criado(s) · ${atual.atualizados} atualizado(s)` +
            (atual.semEmail > 0 ? ` · ${atual.semEmail} sem e-mail` : ''),
        )
        await aoConcluir()
      }
      anterior.current = atual.estado
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao consultar a atualização.')
    }
  }, [avisos, aoConcluir])

  useEffect(() => {
    void consultar()
  }, [consultar])

  // Só pergunta enquanto há o que acompanhar.
  useEffect(() => {
    if (situacao?.estado !== 'EM_ANDAMENTO') return
    const relogio = setInterval(() => void consultar(), INTERVALO_MS)
    return () => clearInterval(relogio)
  }, [situacao?.estado, consultar])

  async function iniciar(escopo: EscopoDaAtualizacao) {
    setIniciando(true)
    setErro(null)
    try {
      const inicial = await api.post<SituacaoDaAtualizacao>(BASE, { escopo })
      anterior.current = inicial.estado
      setSituacao(inicial)
      setEscolhendo(false)
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao iniciar a atualização.')
    } finally {
      setIniciando(false)
    }
  }

  const emAndamento = situacao?.estado === 'EM_ANDAMENTO'
  const percentual =
    situacao && situacao.unidadesTotal > 0
      ? Math.round((situacao.unidadesProcessadas / situacao.unidadesTotal) * 100)
      : 0

  return {
    situacao,
    erro,
    escolhendo,
    iniciando,
    emAndamento,
    percentual,
    abrir: () => setEscolhendo(true),
    fechar: () => {
      if (!iniciando) setEscolhendo(false)
    },
    iniciar,
  }
}

type Atualizacao = ReturnType<typeof useAtualizacaoDaBase>

export function BotaoAtualizarBase({ atualizacao }: { atualizacao: Atualizacao }) {
  return (
    <button
      type="button"
      className="botao botao-neutro"
      disabled={atualizacao.emAndamento}
      onClick={atualizacao.abrir}
    >
      {atualizacao.emAndamento ? <span className="giro" /> : <Icone nome="trocar" tamanho={16} />}
      {atualizacao.emAndamento
        ? `Atualizando… ${atualizacao.percentual}%`
        : 'Atualizar base de usuários'}
    </button>
  )
}

export function PainelAtualizacaoDaBase({ atualizacao }: { atualizacao: Atualizacao }) {
  const { situacao, erro, escolhendo, emAndamento, percentual } = atualizacao

  if (erro && !escolhendo) {
    return (
      <div className="atualizacao-painel">
        <Aviso tom="erro">{erro}</Aviso>
      </div>
    )
  }
  if (!situacao || situacao.estado === 'NUNCA_EXECUTADA') {
    return null
  }

  return (
    <div className="atualizacao-painel">
      <Aviso
        tom={
          situacao.estado === 'FALHOU'
            ? 'erro'
            : situacao.estado === 'EM_ANDAMENTO'
              ? 'informacao'
              : situacao.unidadesComFalha > 0
                ? 'atencao'
                : 'sucesso'
        }
        titulo={tituloDa(situacao)}
      >
        {emAndamento && (
          <>
            <div
              className="atualizacao-barra"
              role="progressbar"
              aria-label="Progresso da atualização"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percentual}
            >
              <span style={{ width: `${percentual}%` }} />
            </div>
            <p className="secundaria">
              {situacao.unidadesTotal === 0
                ? 'Consultando o organograma no RH…'
                : `${situacao.unidadesProcessadas} de ${situacao.unidadesTotal} unidade(s)`}
              {situacao.unidadeAtual ? ` · agora em ${situacao.unidadeAtual}` : ''}
            </p>
          </>
        )}
        <p>
          {situacao.pessoas} pessoa(s) no RH · {situacao.criados} criada(s) ·{' '}
          {situacao.atualizados} atualizada(s)
          {situacao.semEmail > 0 && ` · ${situacao.semEmail} sem e-mail, fora do cadastro`}
          {situacao.unidadesComFalha > 0 &&
            ` · ${situacao.unidadesComFalha} unidade(s) sem resposta do RH`}
        </p>
        {situacao.estado === 'FALHOU' && situacao.mensagem && <p>{situacao.mensagem}</p>}
        {situacao.estado === 'CONCLUIDA' && situacao.unidadesComFalha > 0 && (
          <p>Rodar de novo completa as unidades que faltaram. Quem já entrou não é duplicado.</p>
        )}
        {emAndamento && (
          <p className="secundaria">
            Pode sair desta tela: a atualização continua no servidor, e o progresso aparece aqui
            quando você voltar.
          </p>
        )}
      </Aviso>
    </div>
  )
}

export function ModalAtualizacaoDaBase({ atualizacao }: { atualizacao: Atualizacao }) {
  const { escolhendo, iniciando, erro } = atualizacao
  if (!escolhendo) return null

  return (
    <Modal
      titulo="Atualizar base de usuários"
      descricao="Cria no sistema quem ainda não existe e atualiza quem já existe, com os dados e a lotação que o RH tem hoje."
      aoFechar={atualizacao.fechar}
      rodape={
        <button
          type="button"
          className="botao botao-neutro"
          disabled={iniciando}
          onClick={atualizacao.fechar}
        >
          Cancelar
        </button>
      }
    >
      {erro && <Aviso tom="erro">{erro}</Aviso>}

      <div className="atualizacao-opcoes">
        {OPCOES.map((opcao) => (
          <button
            key={opcao.escopo}
            type="button"
            className="atualizacao-opcao"
            disabled={iniciando}
            onClick={() => void atualizacao.iniciar(opcao.escopo)}
          >
            <strong>{opcao.titulo}</strong>
            <span className="secundaria">{opcao.descricao}</span>
            <span className="rotulo">leva {opcao.tempo}</span>
          </button>
        ))}
      </div>

      <p className="apoio">
        Quem é novo entra com papel de servidor. Quem já existe recebe nome, CPF, matrícula e
        lotação do RH, e <strong>não perde nenhum papel</strong> — administrador continua
        administrador. Ninguém é habilitado a emitir por aqui: isso é da lista de cada edição.
      </p>
      <p className="apoio">
        A consulta ao RH vai a seis chamadas por segundo e ocupa essa cota enquanto roda: a
        sincronização de unidades fica mais lenta até terminar.
      </p>
    </Modal>
  )
}

function tituloDa(situacao: SituacaoDaAtualizacao): string {
  const escopo = situacao.escopo === 'TJGO' ? 'TJGO' : 'base completa do RH'
  switch (situacao.estado) {
    case 'EM_ANDAMENTO':
      return `Atualizando a base de usuários (${escopo})`
    case 'CONCLUIDA':
      return `Última atualização (${escopo}) concluída`
    case 'FALHOU':
      return `A atualização (${escopo}) foi interrompida`
    default:
      return 'Atualização da base de usuários'
  }
}
