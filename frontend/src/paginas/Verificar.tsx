import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ErroApi, urlDaApi } from '../api/cliente'
import type { Verificacao } from '../api/tipos'
import { Aviso, Carregando, formatarDataHora } from '../componentes/Basicos'
import { Icone } from '../componentes/Icone'
import { Disco, rotuloDoSelo } from '../componentes/Selo'

/**
 * Conferência pública de autenticidade (feature 007).
 *
 * É o destino do QR impresso no certificado e roda sem login: quem confere é o
 * RH, uma banca ou qualquer cidadão. O resultado é apresentado como o próprio
 * atestado — moldura de filete duplo, selo cunhado, dados em ficha — porque
 * quem está do outro lado quer ver um documento, não um retorno de API.
 *
 * A resposta traz apenas nome, unidade, edição, selo, tipo e data. <b>Nunca o
 * CPF</b> (007/RNF-2).
 *
 * A mesma tela serve aos dois contextos. Quem chega pelo QR não tem sessão e vê
 * a página inteira, com o cabeçalho do tribunal. Quem já está no sistema e
 * clica em "Conferir certificado" no menu recebe a versão <code>embutida</code>:
 * sem cabeçalho próprio, dentro da casca — tirar o menu de quem está navegando
 * seria despejá-lo do sistema para responder a uma pergunta rápida.
 */
export function Verificar({ embutido = false }: { embutido?: boolean } = {}) {
  const { codigo: codigoDaUrl } = useParams()
  const navegar = useNavigate()

  const [codigo, setCodigo] = useState(codigoDaUrl ?? '')
  const [resultado, setResultado] = useState<Verificacao | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [consultando, setConsultando] = useState(false)

  const consultar = useCallback(async (valor: string) => {
    const limpo = valor.trim()
    if (!limpo) return

    setConsultando(true)
    setErro(null)
    setResultado(null)
    try {
      // Chamada direta: a verificação pública não usa o cliente autenticado,
      // para deixar claro que não depende de sessão alguma.
      const resposta = await fetch(urlDaApi(`/api/public/certificados/${encodeURIComponent(limpo)}`))
      if (resposta.status === 429) {
        throw new ErroApi(
          429,
          'Muitas consultas em pouco tempo. Aguarde um minuto e tente novamente.',
        )
      }
      setResultado((await resposta.json()) as Verificacao)
    } catch (e) {
      setErro(
        e instanceof ErroApi ? e.message : 'Não foi possível consultar agora. Tente novamente.',
      )
    } finally {
      setConsultando(false)
    }
  }, [])

  useEffect(() => {
    if (codigoDaUrl) {
      setCodigo(codigoDaUrl)
      void consultar(codigoDaUrl)
    }
  }, [codigoDaUrl, consultar])

  function enviar(evento: React.FormEvent) {
    evento.preventDefault()
    const limpo = codigo.trim().toUpperCase()
    if (limpo) {
      navegar(`/verificar/${encodeURIComponent(limpo)}`)
    }
  }

  const conteudo = (
    <>
      <span className="rotulo">Conferência pública</span>
      <h1 className={embutido ? 'titulo-pagina' : 'super-titulo'} style={{ marginTop: 8 }}>
        Autenticidade do certificado
      </h1>
      <p className="apoio" style={{ marginTop: 14, maxWidth: '54ch' }}>
        Informe o código impresso no certificado — ou leia o QR, que já traz o código. A
        conferência não exige login e não exibe CPF nem e-mail do reconhecido.
      </p>

      <form className="conferencia-busca" onSubmit={enviar}>
        <input
          value={codigo}
          onChange={(evento) => setCodigo(evento.target.value)}
          placeholder="ABCD-1234-EFGH"
          aria-label="Código de validação"
          autoComplete="off"
          spellCheck={false}
        />
        <button type="submit" className="botao" disabled={consultando || !codigo.trim()}>
          {consultando ? <span className="giro" /> : <Icone nome="busca" tamanho={16} />}
          Conferir
        </button>
      </form>

      {consultando && <Carregando texto="Consultando o registro de emissão…" />}

      {erro && (
        <div style={{ marginTop: 'var(--e5)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      {resultado && !consultando && (
        <div
          className={`resultado${resultado.valido ? '' : ' resultado-invalido-caixa'}`}
        >
          {resultado.valido ? (
            <>
              <div className="resultado-selo">
                {resultado.selo ? (
                  <Disco selo={resultado.selo} tamanho="g" />
                ) : (
                  <span className="resultado-emblema">
                    <Icone nome="confirmado" tamanho={26} />
                  </span>
                )}
                <div className="resultado-veredito">Certificado autêntico</div>
                <div className="apoio" style={{ fontSize: 13 }}>
                  Registro localizado na base de emissões do Prêmio Goianão.
                </div>
              </div>

              <dl className="resultado-dados">
                <Linha rotulo="Reconhecido">
                  <span className="destaque-nome">{resultado.nome}</span>
                </Linha>
                <Linha rotulo="Unidade">{resultado.unidade}</Linha>
                <Linha rotulo="Edição">
                  <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {resultado.edicaoAno}
                  </span>
                </Linha>
                <Linha rotulo="Selo">
                  {resultado.selo && (
                    <span className={`selo selo-${resultado.selo.toLowerCase()}`}>
                      <Disco selo={resultado.selo} tamanho="m" />
                      {rotuloDoSelo(resultado.selo)}
                    </span>
                  )}
                </Linha>
                <Linha rotulo="Tipo">
                  {resultado.tipo === 'MAGISTRADO' ? 'Magistrado' : 'Servidor'}
                </Linha>
                <Linha rotulo="Emitido em">{formatarDataHora(resultado.emitidoEm)}</Linha>
                <Linha rotulo="Código">
                  <span className="mono">{resultado.codigo}</span>
                </Linha>
              </dl>
            </>
          ) : (
            <>
              <div className="resultado-selo">
                <span className="resultado-emblema resultado-emblema-erro">
                  <Icone nome="atencao" tamanho={26} />
                </span>
                <div className="resultado-veredito resultado-veredito-erro">
                  Nenhum certificado encontrado
                </div>
              </div>
              <div className="resultado-mensagem">
                Não há certificado emitido com o código{' '}
                <span className="mono">{resultado.codigo}</span>. Confira a digitação — o código
                tem doze caracteres em três grupos e não usa as letras I, L, O nem U.
              </div>
            </>
          )}
        </div>
      )}

    </>
  )

  if (embutido) {
    return <div className="pagina conferencia-embutida">{conteudo}</div>
  }

  return (
    <div className="conferencia">
      <header className="conferencia-topo">
        <span className="marca-orgao">Tribunal de Justiça do Estado de Goiás</span>
        <div className="marca-nome" style={{ fontSize: 26 }}>
          Prêmio Goianão
        </div>
      </header>

      <main className="conferencia-corpo">
        {conteudo}

        <div className="regua">
          <span />
        </div>

        <p className="apoio" style={{ textAlign: 'center' }}>
          <Link to="/entrar">Entrar no sistema</Link> para emitir seus certificados.
        </p>
      </main>
    </div>
  )
}

function Linha({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div className="resultado-linha">
      <dt>{rotulo}</dt>
      <dd>{children}</dd>
    </div>
  )
}
