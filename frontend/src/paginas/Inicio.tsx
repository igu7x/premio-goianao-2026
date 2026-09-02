import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErroApi } from '../api/cliente'
import type { Edicao, ResumoEdicao } from '../api/tipos'
import { Anel } from '../componentes/Anel'
import { Aviso, Carregando, EstadoVazio, SituacaoEdicao } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'

/**
 * Visão geral do administrador.
 *
 * A tela responde a uma pergunta só: **o prêmio está pronto para emitir?** Por
 * isso a edição vigente abre a página inteira, com o anel de layouts ao lado —
 * a única métrica que efetivamente bloqueia a emissão. Os demais números vêm
 * depois, como apoio.
 */
export function Inicio() {
  const [edicoes, setEdicoes] = useState<Edicao[] | null>(null)
  const [resumo, setResumo] = useState<ResumoEdicao | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true
    api
      .get<Edicao[]>('/api/edicoes')
      .then(async (lista) => {
        if (!ativo) return
        setEdicoes(lista)
        const foco = lista.find((e) => e.vigente) ?? lista[0]
        if (foco) {
          const dados = await api.get<ResumoEdicao>(`/api/painel/edicoes/${foco.id}`)
          if (ativo) setResumo(dados)
        }
      })
      .catch((e: ErroApi) => ativo && setErro(e.message))
    return () => {
      ativo = false
    }
  }, [])

  if (erro) {
    return (
      <div className="pagina">
        <Aviso tom="erro">{erro}</Aviso>
      </div>
    )
  }

  if (!edicoes) {
    return <Carregando />
  }

  if (edicoes.length === 0) {
    return (
      <div className="pagina">
        <div className="bloco">
          <EstadoVazio
            titulo="Nenhuma edição cadastrada"
            descricao="Comece criando a edição do ano. É a partir dela que se configuram os layouts, os magistrados reconhecidos e as listas de servidores."
            acao={
              <Link className="botao" to="/edicoes">
                <Icone nome="mais" tamanho={16} />
                Criar a primeira edição
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  const vigente = edicoes.find((e) => e.vigente)
  const emFoco = vigente ?? edicoes[0]
  const pendencias = resumo?.layoutsPendentes ?? []
  const prontaParaEmitir = emFoco.status === 'PUBLICADA' && pendencias.length === 0

  return (
    <div className="pagina">
      <section className="destaque">
        <div className="destaque-conteudo">
          <span className="rotulo">
            {vigente ? 'Edição vigente' : 'Edição mais recente'}
          </span>
          <h1>Edição {emFoco.ano}</h1>
          <p>
            {prontaParaEmitir
              ? 'Tudo configurado. Magistrados e servidores reconhecidos já conseguem emitir os certificados desta edição.'
              : emFoco.status === 'RASCUNHO'
                ? 'Em rascunho: nada é emitido até a publicação, que exige as oito combinações de layout configuradas.'
                : 'Publicada, mas ainda não é a edição padrão da emissão.'}
          </p>

          <div className="destaque-acoes">
            <Link className="botao" to={`/edicoes/${emFoco.id}`}>
              Abrir edição
              <Icone nome="seta" tamanho={15} />
            </Link>
            <Link className="botao botao-neutro" to="/edicoes">
              Todas as edições
            </Link>
            <SituacaoEdicao status={emFoco.status} vigente={emFoco.vigente} />
          </div>
        </div>

        <div className="destaque-lateral">
          <Anel
            valor={resumo?.layoutsConfigurados ?? 0}
            total={8}
            tamanho={112}
            rotulo="Layouts"
          />
        </div>
      </section>

      {pendencias.length > 0 && (
        <div style={{ marginBottom: 'var(--e5)' }}>
          <Aviso
            tom="atencao"
            titulo={`Faltam ${pendencias.length} de 8 combinações de layout`}
            detalhes={pendencias}
          >
            <p>Sem elas a edição não pode ser publicada.</p>
          </Aviso>
        </div>
      )}

      <div className="grade grade-3 escalonar" style={{ marginBottom: 'var(--e6)' }}>
        <Indicador
          rotulo="Magistrados reconhecidos"
          valor={resumo?.magistradosReconhecidos ?? '—'}
          nota="Fonte da verdade sobre quem venceu"
        />
        <Indicador
          rotulo="Unidades reconhecidas"
          valor={resumo?.unidadesReconhecidas ?? '—'}
          nota={`${resumo?.servidoresHabilitados ?? 0} servidores habilitados a emitir`}
        />
        <Indicador
          rotulo="Certificados emitidos"
          valor={resumo?.certificadosEmitidos ?? '—'}
          nota="Contagem por certificado, não por download"
        />
      </div>

      <div className="bloco">
        <div className="bloco-cabecalho">
          <div>
            <h2 className="titulo-secao">Todas as edições</h2>
            <p className="apoio">
              Edições publicadas continuam emitíveis, com os dados e o layout da época.
            </p>
          </div>
          <Link className="botao botao-neutro botao-pequeno" to="/edicoes">
            Gerenciar
          </Link>
        </div>
        <div className="tabela-rolagem">
          <table className="tabela">
            <thead>
              <tr>
                <th>Ano</th>
                <th>Situação</th>
                <th>Descrição</th>
                <th className="direita">Ação</th>
              </tr>
            </thead>
            <tbody>
              {edicoes.map((edicao) => (
                <tr key={edicao.id}>
                  <td className="principal" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {edicao.ano}
                  </td>
                  <td>
                    <SituacaoEdicao status={edicao.status} vigente={edicao.vigente} />
                  </td>
                  <td className="secundaria">{edicao.descricao ?? '—'}</td>
                  <td className="direita">
                    <Link className="botao botao-texto botao-pequeno" to={`/edicoes/${edicao.id}`}>
                      Abrir
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function Indicador({
  rotulo,
  valor,
  nota,
}: {
  rotulo: string
  valor: string | number
  nota?: string
}) {
  return (
    <div className="indicador">
      <span className="rotulo">{rotulo}</span>
      <div className="numero">{valor}</div>
      {nota && <div className="indicador-nota">{nota}</div>}
    </div>
  )
}
