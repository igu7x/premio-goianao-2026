import { useCallback, useEffect, useState } from 'react'
import { api, ErroApi } from '../api/cliente'
import type { ListaHabilitados } from '../api/tipos'
import { Aviso, Carregando, EstadoVazio } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import { PainelDaLista } from './abas/PainelDaLista'

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
  const [aberta, setAberta] = useState<ListaHabilitados | null>(null)

  const carregar = useCallback(async () => {
    try {
      setListas(await api.get<ListaHabilitados[]>('/api/magistrado/servidores'))
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Falha ao carregar suas unidades.')
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

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
            você foi reconhecido na edição vigente. A lista foi semeada a partir do EGESP.
          </p>
        </div>
      </header>

      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {!listas ? (
        <Carregando />
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
