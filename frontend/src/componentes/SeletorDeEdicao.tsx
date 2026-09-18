import { useState } from 'react'
import { ErroApi } from '../api/cliente'
import type { EdicaoDaSessao } from '../api/tipos'
import { useSessao } from '../sessao/SessaoContexto'
import { useAvisos } from './Avisos'
import { Icone } from './Icone'

function rotulo(edicao: EdicaoDaSessao): string {
  return `Edição ${edicao.ano}${edicao.vigente ? ' · vigente' : ''}`
}

/**
 * Sobre qual edição o sistema inteiro está agindo (feature 011).
 *
 * Fica no topo, sempre visível, porque cada edição tem a sua base: usuários,
 * unidades, reconhecidos e listas são daquela edição e de nenhuma outra. Quem
 * sincroniza 190 unidades precisa saber, sem procurar, em que ano está fazendo
 * isso.
 *
 * Com uma edição só, o seletor vira um rótulo — não há o que escolher, mas a
 * informação continua ali.
 */
export function SeletorDeEdicao() {
  const { identidade, trocarEdicao } = useSessao()
  const avisos = useAvisos()
  const [trocando, setTrocando] = useState(false)

  const atual = identidade?.edicao
  const disponiveis = identidade?.edicoesDisponiveis ?? []
  if (!atual) {
    return null
  }

  if (disponiveis.length < 2) {
    return (
      <span className="troca-edicao troca-edicao-fixa" title="Edição em que você está trabalhando">
        <Icone nome="edicoes" tamanho={14} />
        {rotulo(atual)}
      </span>
    )
  }

  async function trocar(edicaoId: number) {
    if (edicaoId === atual?.id) return
    setTrocando(true)
    try {
      await trocarEdicao(edicaoId)
    } catch (e) {
      setTrocando(false)
      avisos.erro(
        'Não foi possível trocar de edição',
        e instanceof ErroApi ? e.message : undefined,
      )
    }
  }

  return (
    <label className="troca-edicao" title="Trocar a edição em que você está trabalhando">
      {trocando ? <span className="giro" /> : <Icone nome="trocar" tamanho={14} />}
      <select
        aria-label="Edição em que você está trabalhando"
        value={atual.id}
        disabled={trocando}
        onChange={(evento) => void trocar(Number(evento.target.value))}
      >
        {disponiveis.map((edicao) => (
          <option key={edicao.id} value={edicao.id}>
            {rotulo(edicao)}
          </option>
        ))}
      </select>
    </label>
  )
}
