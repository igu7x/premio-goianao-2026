import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi } from '../api/cliente'
import type { ListaHabilitados, Semeadura } from '../api/tipos'
import { useAvisos } from '../componentes/Avisos'
import { Aviso, Carregando, EstadoVazio } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import { PainelDaLista } from './abas/PainelDaLista'
import { resumoDaSemeadura } from './abas/resumoDaSemeadura'

/**
 * Visão do magistrado sobre as listas das suas unidades (008/RF-4).
 *
 * Ele pode ajustar quem emite na sua unidade — mas só na edição vigente. É o
 * caminho rápido para corrigir uma omissão da semeadura sem depender do
 * administrador.
 *
 * Cada unidade é um cartão com os dois números que importam: quantos estão
 * habilitados e quantos foram removidos. Antes era um bloco genérico com uma
 * frase; o magistrado precisa bater o olho e saber se a lista está de pé.
 */
export function MinhasUnidades() {
  const [listas, setListas] = useState<ListaHabilitados[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  /**
   * Um 404 aqui só tem um motivo: nenhuma edição foi marcada como vigente, e
   * esta tela é sempre a da vigente. Não é falha — é estado do prêmio, e pede
   * explicação, não tarja vermelha.
   */
  const [semEdicaoVigente, setSemEdicaoVigente] = useState(false)
  const [aberta, setAberta] = useState<ListaHabilitados | null>(null)
  const [semeando, setSemeando] = useState<number | null>(null)
  const avisos = useAvisos()

  const carregar = useCallback(async () => {
    setErro(null)
    setSemEdicaoVigente(false)
    try {
      setListas(await api.get<ListaHabilitados[]>('/api/magistrado/servidores'))
    } catch (e) {
      if (e instanceof ErroApi && e.status === 404) {
        setSemEdicaoVigente(true)
        setListas([])
        return
      }
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar suas unidades.')
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  /**
   * O responsável pela unidade traz a lotação do RH sem esperar o
   * administrador. A mescla é a mesma do administrador: não desfaz inclusões
   * manuais nem traz de volta quem ele removeu.
   */
  async function semear(lista: ListaHabilitados) {
    setSemeando(lista.unidadeId)
    setErro(null)
    try {
      const dados = await api.post<Semeadura>(
        `/api/edicoes/${lista.edicaoId}/unidades/${lista.unidadeId}/servidores/semear`,
      )
      avisos.sucesso(`Lista de ${lista.unidadeNome} atualizada`, resumoDaSemeadura(dados))
      await carregar()
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao semear a lista.')
    } finally {
      setSemeando(null)
    }
  }

  return (
    <div className="pagina">
      <header className="cabecalho-pagina">
        <div>
          <span className="rotulo">Minhas unidades</span>
          <h1 className="titulo-pagina" style={{ marginTop: 4 }}>
            Servidores habilitados
          </h1>
          <p>
            Confira e ajuste quem pode emitir o certificado de servidor nas unidades pelas quais
            você responde na edição vigente. Semear do EGESP traz quem está lotado na unidade e
            mescla com a lista atual: suas inclusões ficam e quem você removeu não volta.
          </p>
        </div>
      </header>

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {semEdicaoVigente ? (
        <div className="bloco">
          <EstadoVazio
            titulo="Nenhuma edição vigente definida"
            descricao="Esta tela mostra as listas da edição vigente, e ainda não há uma marcada como tal. Assim que a administração do prêmio definir a edição do ano, suas unidades aparecem aqui."
          />
        </div>
      ) : !listas ? (
        // Sem esta guarda o carregando gira para sempre depois de uma falha: o
        // erro aparece em cima e a tela parece estar tentando de novo.
        !erro && <Carregando />
      ) : listas.length === 0 ? (
        <div className="bloco">
          <EstadoVazio
            titulo="Nenhuma unidade na edição vigente"
            descricao="Você não tem reconhecimento na edição vigente. Listas de edições anteriores são mantidas pelo administrador."
          />
        </div>
      ) : (
        <div className="grade grade-2">
          {listas.map((lista, indice) => {
            const ativos = lista.servidores.filter((s) => s.ativo).length
            const removidos = lista.servidores.length - ativos
            return (
              <article
                className="unidade-cartao"
                key={lista.unidadeId}
                style={{ animationDelay: `${indice * 55}ms` }}
              >
                <div className="unidade-cartao-corpo">
                  <span className="rotulo">Edição {lista.edicaoAno}</span>
                  <h2 className="unidade-nome">{lista.unidadeNome}</h2>

                  <div className="unidade-numeros">
                    <div className="unidade-numero">
                      <strong>{ativos}</strong>
                      <span>habilitados</span>
                    </div>
                    <div className="unidade-numero">
                      <strong>{removidos}</strong>
                      <span>removidos</span>
                    </div>
                  </div>
                </div>

                <div className="bloco-rodape">
                  <div className="acoes acoes-direita">
                    {lista.podeSemear && (
                      <button
                        type="button"
                        className="botao botao-neutro botao-pequeno"
                        disabled={semeando === lista.unidadeId}
                        onClick={() => void semear(lista)}
                      >
                        {semeando === lista.unidadeId ? (
                          <span className="giro" />
                        ) : (
                          <Icone nome="semear" tamanho={15} />
                        )}
                        {semeando === lista.unidadeId ? 'Semeando…' : 'Semear do EGESP'}
                      </button>
                    )}
                    <button
                      type="button"
                      className={lista.podeEditar ? 'botao botao-pequeno' : 'botao botao-neutro botao-pequeno'}
                      onClick={() => setAberta(lista)}
                    >
                      {lista.podeEditar ? 'Gerenciar lista' : 'Ver lista'}
                      <Icone nome="seta" tamanho={14} />
                    </button>
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      )}

      {aberta && (
        <PainelDaLista
          caminho={`/api/edicoes/${aberta.edicaoId}/unidades/${aberta.unidadeId}/servidores`}
          carregarLista={() =>
            api.get<ListaHabilitados>(
              `/api/edicoes/${aberta.edicaoId}/unidades/${aberta.unidadeId}/servidores`,
            )
          }
          aoFechar={() => {
            setAberta(null)
            void carregar()
          }}
        />
      )}
    </div>
  )
}
