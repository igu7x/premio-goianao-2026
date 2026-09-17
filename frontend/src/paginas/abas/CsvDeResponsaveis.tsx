import { useEffect, useRef, useState } from 'react'
import { ErroApi, api, lerToken, urlDaApi } from '../../api/cliente'
import type { Edicao, ImportacaoResponsaveis, UnidadeCadastrada } from '../../api/tipos'
import { useAvisos } from '../../componentes/Avisos'
import { Aviso, Modal } from '../../componentes/Basicos'
import { Icone } from '../../componentes/Icone'

/**
 * Planilha de magistrados responsáveis pelas unidades.
 *
 * Uma linha por unidade: `nome;e-mail;código da unidade;selo`. Cada linha cria o
 * magistrado que ainda não existe, grava o selo como reconhecimento da edição,
 * designa quem responde pela unidade e semeia a lista de servidores habilitados
 * dela com os lotados do RH (DI-27).
 *
 * <p>Vive ao lado do cadastro de reconhecidos porque é disso que ela trata:
 * quem foi reconhecido e por qual unidade. Teve uma aba própria, com as 190
 * unidades listadas, e não precisava: as unidades entram pela sincronização, e
 * as que estão em uso são exatamente as que aparecem aqui depois do envio.
 *
 * <p>Diferente do "Importar planilha" dos reconhecidos, está disponível em
 * qualquer edição — designar responsável e semear a lista não altera
 * certificado já emitido.
 */
export function CsvDeResponsaveis({
  edicao,
  aoImportar,
}: {
  edicao: Edicao
  aoImportar: () => Promise<void>
}) {
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  /** Relatório do último envio; fica na tela até a pessoa fechar, porque é onde
   *  aparecem as linhas que o arquivo não conseguiu gravar. */
  const [relatorio, setRelatorio] = useState<ImportacaoResponsaveis | null>(null)
  const [unidades, setUnidades] = useState<UnidadeCadastrada[]>([])
  const arquivo = useRef<HTMLInputElement>(null)
  const avisos = useAvisos()

  // Só para gerar o modelo com códigos de verdade; a tela não lista unidades.
  useEffect(() => {
    let ativo = true
    api
      .get<UnidadeCadastrada[]>('/api/unidades/cadastradas')
      .then((lista) => ativo && setUnidades(lista))
      .catch(() => ativo && setUnidades([]))
    return () => {
      ativo = false
    }
  }, [])

  /**
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

      const resposta = await fetch(
        urlDaApi(`/api/unidades/responsaveis/importar?edicaoId=${edicao.id}`),
        {
          method: 'POST',
          headers: { Authorization: `Bearer ${lerToken() ?? ''}` },
          body: corpo,
        },
      )
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
          lido.listasSemeadas > 0
            ? `${lido.listasSemeadas} lista(s) semeada(s) com ${lido.habilitados} servidor(es)`
            : null,
          lido.erros.length > 0 ? `${lido.erros.length} linha(s) com erro` : null,
        ]
          .filter(Boolean)
          .join(' · ') || 'Tudo na planilha já estava gravado.',
      )
      await aoImportar()
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
   * Gera um CSV de teste com as unidades cadastradas.
   *
   * Os códigos são os do banco — é isso que faz o arquivo valer para testar: um
   * exemplo com código inventado falharia em todas as linhas e não provaria
   * nada. Os nomes e e-mails são fictícios, no domínio reservado `.example`,
   * para que ninguém confunda o teste com a lista de verdade.
   */
  function baixarModelo() {
    const comCodigo = unidades.filter((u) => u.codigoSiedos !== null)
    if (comCodigo.length === 0) {
      setErro(
        'Nenhuma unidade tem código do SIEDOS ainda: compare-as em Sincronização de Unidades '
          + 'antes de gerar o modelo.',
      )
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

  return (
    <>
      {erro && (
        <div style={{ marginBottom: 'var(--e4)' }}>
          <Aviso tom="erro">{erro}</Aviso>
        </div>
      )}

      <div className="csv-responsaveis">
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
          className="botao botao-neutro"
          disabled={enviando}
          onClick={() => arquivo.current?.click()}
        >
          {enviando ? <span className="giro" /> : <Icone nome="enviar" tamanho={16} />}
          {enviando ? 'Importando…' : 'Subir CSV de magistrados'}
        </button>
        <button type="button" className="botao botao-texto botao-pequeno" onClick={baixarModelo}>
          Baixar modelo de teste
        </button>
        <span className="secundaria mono">nome;e-mail;código da unidade;selo</span>
      </div>

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
              <li>
                {relatorio.listasSemeadas} lista(s) de servidores semeada(s) do RH, com{' '}
                {relatorio.habilitados} servidor(es) incluído(s).
              </li>
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
    </>
  )
}
