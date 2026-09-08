# Tarefas: Inclusão de reconhecimentos na edição vigente

- **ID:** 009-inclusao-reconhecimentos-vigente
- **Plano relacionado:** ./plan.md
- **Status:** concluído

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.

## Tarefas

### Pré-condição de publicação (feature 002)
- [x] **T-001** — Porta `PreRequisitosPublicacao` (pacote `edicao`) e impl
  `LayoutPreRequisitos` (pacote `layout`) usando as pendências da 003
  _(satisfaz: 002/RF-3b)_
- [x] **T-002** — `EdicaoService.publicar` consulta a porta; `409` listando as
  combinações pendentes quando faltam layouts _(satisfaz: 002/RF-3b, CA-7/CA-8)_

### Inclusão aditiva (009)
- [x] **T-003** — `MagistradoService.podeIncluir(edicaoId)` = Rascunho ou
  Publicada+vigente; `criar`/`importar` passam a usá-la _(satisfaz: RF-1, RF-7)_
- [x] **T-004** — `MagistradoService.adicionarReconhecimento(...)`: elegibilidade,
  magistrado da edição, unidade não duplicada (409), upsert unidade EGESP
  _(satisfaz: RF-2, RF-5, RF-6)_
- [x] **T-005** — Endpoint `POST /api/edicoes/{edId}/magistrados/{id}/reconhecimentos`
  (admin) _(satisfaz: RF-2, RF-8)_
- [x] **T-006** — `PUT`/`DELETE` mantêm `exigirRascunho` (regressão) _(satisfaz: RF-3)_

### Frontend
- [x] **T-007** — Tela Magistrados: modo **somente-inclusão** na vigente (formulário
  de novo magistrado + "adicionar unidade" por magistrado; sem remover/editar)
  _(satisfaz: RF-1, RF-2, RF-3)_
- [x] **T-008** — Edições: exibir o erro de publicação (layouts pendentes)
  _(satisfaz: 002/RF-3b)_

### Testes
- [x] **T-009** — Backend: publicar sem 8 layouts bloqueia (CA-8); inclusão na
  vigente (CA-1/CA-2); duplicada 409 (CA-3); publicada não-vigente bloqueia (CA-4);
  unidade nova gerenciável na 008 (CA-5); 403 não-admin (CA-6); Rascunho ok (CA-7);
  regressão PUT/DELETE bloqueados _(satisfaz: RF-1..RF-10)_
- [x] **T-010** — Frontend: teste do modo inclusão na vigente _(satisfaz: RF-2)_

## Definição de pronto

- [x] CA-1 a CA-8 verificados; suíte existente verde (ajustada à pré-condição).

## Implementação (2026-09-01)

**Backend** — a regra virou `Edicao.aceitaInclusoes()` (rascunho **ou** publicada
e vigente), consumida por `MagistradoService.exigirElegivelParaInclusao`.
`criar` e a importação passaram a usá-la; `atualizar` e `remover` continuam com
`exigirRascunho`. Endpoint aditivo:
`POST /api/edicoes/{id}/magistrados/{magistradoId}/reconhecimentos`.

A pré-condição de publicação (§10 do plano) foi implementada como a porta
`edicao/PreRequisitosPublicacao` com a implementação `layout/LayoutPreRequisitos`.

**Frontend** — `paginas/abas/AbaMagistrados.tsx` entra em **modo somente-inclusão**
quando a edição está publicada: some a ação de remover, o formulário de novo
magistrado e o "adicionar unidade" continuam, e um aviso explica por quê.

**Testes (frontend)** — `AbaMagistrados.test.tsx` (Vitest + Testing Library)
verifica os três estados da tela: rascunho (inclui, importa e remove), vigente
(somente inclusão, sem remover nem importar) e publicada não vigente (tudo
desabilitado). Um dos casos confere que a inclusão de unidade usa o endpoint
aditivo `POST .../reconhecimentos`, e não um `PUT`.

**Testes (backend)** — `InclusaoNaVigenteIT` cobre CA-1 a CA-7, a regressão de
`PUT`/`DELETE` bloqueados (RF-3) e o recálculo do maior selo quando uma inclusão
eleva o selo da unidade. CA-8 (publicar sem os 8 layouts) está em `EdicaoIT`.

### Decisões tomadas na implementação

- A regra ficou **na entidade** (`Edicao.aceitaInclusoes()`) e não no serviço: é
  uma propriedade do estado da edição, e o DTO a expõe (`aceitaInclusoes`) para
  o frontend decidir o que mostrar sem repetir a lógica.
- A mensagem de bloqueio para edição publicada não vigente diz explicitamente
  "cadastro congelado", para distinguir do caso de rascunho.

### Escopo da T-003, esclarecido em 2026-09-01

A T-003 menciona `criar`/`importar`, mas apenas **`criar`** passou a aceitar a
edição vigente. A importação em lote continua restrita ao rascunho, conforme a
**004/RF-11**, que é requisito numerado e não foi emendada por esta feature — e
conforme o §3 do plano acima, que troca a regra só em `criar`. A decisão está
registrada no `tasks.md` da 004 e em
`specs/memory/decisoes-de-implementacao.md`.
