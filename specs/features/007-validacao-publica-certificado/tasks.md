# Tarefas: Validação pública de certificado

- **ID:** 007-validacao-publica-certificado
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Endpoint/página **públicos** que conferem o `codigo_validacao` (005/006).

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.

## Tarefas

- [x] **T-001** — `VerificacaoDTO` (nome, unidade, edicaoAno, selo, tipo,
  emitidoEm) — **sem CPF** _(satisfaz: RF-2, RNF-2)_
- [x] **T-002** — `GET /api/public/certificados/{codigo}`: `200` com dados se
  válido, `404` uniforme se inexistente _(satisfaz: RF-1, RF-2, RF-3, RF-5)_
  _(depende de: T-001)_
- [x] **T-003** — Liberar a rota no Spring Security (`permitAll`) e aplicar
  **rate limiting** por IP _(satisfaz: RNF-1, RNF-3)_ _(depende de: T-002)_
- [x] **T-004** [P] — Confirmar que o **QR** gerado na emissão (005/006) aponta
  para `${BASE_VERIFICACAO}/verificar/{codigo}` _(satisfaz: RF-4)_
- [x] **T-005** [P] — Frontend: página pública `/verificar/{codigo}` (resolve pelo
  endpoint; estados válido/não encontrado) _(satisfaz: RF-1, RF-3)_
  _(depende de: T-002)_
- [x] **T-006** — Testes: código válido retorna dados **sem CPF** (CA-1, CA-5);
  inexistente → `404` uniforme (CA-2); QR resolve o código (CA-3); reemitido
  segue válido pelo mesmo código (CA-4) _(satisfaz: RF-1..RF-5)_
  _(depende de: T-002, T-004)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-5 verificados.
- [x] Nenhum CPF exposto na verificação pública.
- [x] Rate limiting ativo; respostas uniformes para códigos inexistentes.
- [x] Testes passando.

## Implementação (2026-09-01)

**Backend** — `br.jus.tjgo.goianao.publico`: `VerificacaoController`,
`VerificacaoResposta` (DTO sem CPF) e `LimitadorDeTaxa`. A rota
`/api/public/**` é liberada em `SecurityConfig`.

**Frontend** — `paginas/Verificar.tsx`, fora do fluxo autenticado, nas rotas
`/verificar` e `/verificar/:codigo`.

**Testes (frontend)** — `Verificar.test.tsx` cobre a resolução automática do
código vindo da URL (o caminho do QR), o estado de não encontrado e a ausência
de CPF no painel de resultado.

**Testes (backend)** — `VerificacaoPublicaIT` cobre CA-1 a CA-5, inclusive a verificação de
que o CPF **não aparece em lugar nenhum** do corpo da resposta.

### Decisões tomadas na implementação

- **O código digitado é normalizado** antes da consulta: aceita-se sem hifens e
  em minúsculas. Quem digita o que está impresso não deve ser punido por
  formatação — e o código é opaco de qualquer forma.
- **Limitador em memória, por IP e por minuto.** Basta para uma instância; havendo
  várias, o limite deve migrar para o proxy reverso ou um Redis compartilhado.
  O comentário no código registra isso.
- **`404` com o mesmo corpo do caso válido** (`{ valido: false }`), sem detalhe
  adicional: a resposta é uniforme e não distingue "não existe" de qualquer
  outra falha.
- Pendente da spec: o **domínio público** continua sendo configuração de
  ambiente (`GOIANAO_BASE_VERIFICACAO`), como previsto.
