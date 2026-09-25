import { useEffect, useState } from 'react'
import { api } from '../api/cliente'
import type { SituacaoDoAmbiente } from '../api/tipos'

/**
 * O que este ambiente tem — e, por tabela, se ele é de teste.
 *
 * Produção é o ambiente oficial: nada que exista para testar pode aparecer
 * nela. Em vez de espalhar `import.meta.env` pela interface, quem responde é o
 * servidor: ele sabe quais portas de entrada abriu e se o RH ligado é o
 * corporativo ou o mockado. A mesma resposta serve ao rodapé, ao campo de senha
 * do cadastro e ao modelo de planilha de exemplo.
 *
 * A resposta é a mesma para todo mundo e não muda em uso, então fica guardada
 * no módulo: a primeira tela que perguntar paga a chamada, as demais leem o que
 * já veio.
 */
let conhecido: SituacaoDoAmbiente | null = null

export function useAmbiente(): SituacaoDoAmbiente | null {
  const [ambiente, setAmbiente] = useState<SituacaoDoAmbiente | null>(conhecido)

  useEffect(() => {
    if (conhecido) {
      return
    }
    let ativo = true
    api
      .get<SituacaoDoAmbiente>('/api/auth/situacao')
      .then((situacao) => {
        conhecido = situacao
        if (ativo) setAmbiente(situacao)
      })
      .catch(() => {
        /* sem resposta, a tela assume produção e não mostra nada de teste */
      })
    return () => {
      ativo = false
    }
  }, [])

  return ambiente
}

/**
 * Ambiente de teste é o que abre alguma porta além do SSO, ou trabalha com
 * dados de RH mockados. Produção não faz nem uma coisa nem outra.
 */
export function ehDeTeste(ambiente: SituacaoDoAmbiente | null): boolean {
  if (!ambiente) {
    return false
  }
  return ambiente.mock || ambiente.senha || !ambiente.rhReal
}
