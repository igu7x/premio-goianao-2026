# Tarefas: Gestão de edições do prêmio

- **ID:** 002-gestao-edicoes
- **Plano relacionado:** ./plan.md
- **Status:** concluído

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.
- Cada tarefa referencia o requisito da spec (ex.: `RF-1`).

## Tarefas

- [x] **T-001** — Migração `edicao` (ano único, descricao, status
  {RASCUNHO, PUBLICADA}, `vigente` boolean) + **índice único parcial** em
  `vigente = true` _(satisfaz: RF-2, RF-5)_
- [x] **T-002** — Entidade/repositório `Edicao` e enum de status
  _(satisfaz: RF-1)_ _(depende de: T-001)_
- [x] **T-003** — `POST /api/edicoes` (cria em **RASCUNHO**; `409` se ano
  duplicado) _(satisfaz: RF-1, RF-2)_ _(depende de: T-002)_
- [x] **T-004** — `POST /api/edicoes/{id}/publicar` (RASCUNHO → PUBLICADA)
  _(satisfaz: RF-3)_ _(depende de: T-002)_
- [x] **T-005** — `POST /api/edicoes/{id}/vigente` **transacional** (desmarca a
  anterior; exige PUBLICADA) _(satisfaz: RF-4, RF-5)_ _(depende de: T-004)_
- [x] **T-006** [P] — `GET /api/edicoes` (com status e vigente), `GET {id}` e
  `PUT {id}` (descrição) _(satisfaz: RF-7, RF-8)_ _(depende de: T-002)_
- [x] **T-007** [P] — `GET /api/edicoes/vigente` e `GET /api/edicoes/publicadas`
  (consumíveis por 003/004/005/006) _(satisfaz: RF-6, RF-9)_ _(depende de: T-002)_
- [x] **T-008** — Restringir escrita ao **Administrador** (RBAC) _(satisfaz: RNF-1)_
  _(depende de: T-003..T-007)_
- [x] **T-009** [P] — Frontend admin: lista/criação/edição de edições, ações
  **Publicar** e **Tornar vigente** _(satisfaz: RF-1, RF-3, RF-4, RF-7)_
  _(depende de: T-003..T-007)_
- [x] **T-010** — Testes: ano duplicado (CA-2); tornar vigente desmarca a anterior
  mantendo-a Publicada (CA-3); edição publicada anterior segue emitível (CA-4);
  Rascunho bloqueia emissão (CA-5); `403` não-admin (CA-6)
  _(satisfaz: RF-2, RF-4, RF-5, RF-6, RNF-1)_ _(depende de: T-008)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-6 verificados.
- [x] Invariante "no máximo uma vigente" garantida por índice no banco.
- [x] Testes passando.

## Implementação (2026-09-01)

**Backend** — `br.jus.tjgo.goianao.edicao`: `Edicao`, `EdicaoRepository`,
`EdicaoService`, `EdicaoController` e a porta `PreRequisitosPublicacao`
(implementada em `layout/LayoutPreRequisitos.java`).

**Frontend** — `paginas/Edicoes.tsx` (lista, criação, publicar, tornar vigente) e
`paginas/EdicaoDetalhe.tsx`. O erro de publicação exibe as combinações pendentes
item a item.

**Testes** — `EdicaoIT` cobre CA-1 a CA-7 mais a invariante de vigente única.

### Decisões tomadas na implementação

- **Invariante "no máximo uma vigente" sem índice parcial.** O plano previa
  `CREATE UNIQUE INDEX ... WHERE vigente = true`, que é específico do PostgreSQL.
  Como o desenvolvimento roda em H2 (ver `specs/memory/decisoes-de-implementacao.md`),
  usou-se uma coluna espelho `vigente_unico` que vale `'S'` quando vigente e
  `NULL` caso contrário, sob restrição UNIQUE. Como NULLs são distintos num
  índice único nos dois bancos, a garantia é a mesma — e portável. A coluna é
  mantida pela própria entidade e não aparece na API.
- **Troca de vigente pela entidade, não por `UPDATE` em massa.** A primeira versão
  usava `@Modifying`, mas um update em massa não atualiza o contexto de
  persistência: quem já tinha a edição anterior carregada continuava enxergando
  `vigente = true`. Passou a carregar e alterar a entidade, com `flush()`
  intermediário para não esbarrar na restrição durante a troca.
