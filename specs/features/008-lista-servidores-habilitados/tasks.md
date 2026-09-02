# Tarefas: Gestão da lista de servidores habilitados por unidade

- **ID:** 008-lista-servidores-habilitados
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Depende de 004 (unidades reconhecidas) e estende a porta `EgespClient`.
> É a base da elegibilidade do servidor na emissão (feature 006).

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.

## Tarefas

- [x] **T-001** — Estender `EgespClient` com
  `listarServidoresPorUnidade(unidade)` + mock (servidores de teste por unidade)
  _(satisfaz: RF-1)_
- [x] **T-002** — Migração `servidor_habilitado` (edicao_id, unidade_id, cpf, nome,
  `origem` {EGESP, MANUAL}, `ativo`, auditoria) + **unique** (edicao, unidade, cpf)
  _(satisfaz: RF-2, RF-5, RF-8)_
- [x] **T-003** — Guarda `PodeEditarLista`: **admin** (qualquer edição publicada);
  **magistrado** apenas se `edicao.vigente` E unidade ∈ seus reconhecimentos
  _(satisfaz: RF-3, RF-4, RNF-1)_ _(depende de: T-002)_
- [x] **T-004** — `POST .../unidades/{unId}/servidores/semear` (admin): busca no
  EGESP e **mescla** (não remove MANUAL, não reativa removidos)
  _(satisfaz: RF-1, RF-2)_ _(depende de: T-001, T-002)_
- [x] **T-005** [P] — `GET .../unidades/{unId}/servidores` (lista atual)
  _(satisfaz: RF-6)_ _(depende de: T-002)_
- [x] **T-006** — `POST` incluir (origem MANUAL; `409` se CPF já existe) e
  `DELETE {cpf}` (remoção lógica) com a guarda de escopo
  _(satisfaz: RF-3, RF-4, RF-5, RF-8)_ _(depende de: T-003)_
- [x] **T-007** [P] — `GET /api/magistrado/servidores?edicaoId=` (unidades do
  magistrado na vigente, com suas listas) _(satisfaz: RF-4)_ _(depende de: T-002)_
- [x] **T-008** — Frontend admin: **passo posterior** ao cadastro (semear/editar
  lista por unidade) _(satisfaz: RF-1, RF-3)_ _(depende de: T-004, T-005, T-006)_
- [x] **T-009** [P] — Frontend magistrado: editar a lista das suas unidades na
  edição vigente _(satisfaz: RF-4)_ _(depende de: T-006, T-007)_
- [x] **T-010** — Testes: semeadura popula do EGESP (CA-1); inclusão manual (CA-2);
  magistrado edita vigente/sua unidade (CA-3); bloqueio em não vigente (CA-4) e em
  unidade alheia (CA-5); duplicidade (CA-6); ressemear preserva ajustes
  _(satisfaz: RF-1..RF-8)_ _(depende de: T-004, T-006, T-007)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-6 verificados.
- [x] Ressemear **mescla** (não desfaz ajustes manuais).
- [x] Lista exposta como fonte de elegibilidade para a feature 006.
- [x] Testes passando.

## Implementação (2026-09-01)

**Backend** — `br.jus.tjgo.goianao.servidor`: `ServidorHabilitado` (migração
`V4`), `ServidorHabilitadoService` (semeadura com mescla, inclusão, remoção
lógica e a guarda de escopo), `ServidorHabilitadoController` e
`MagistradoServidoresController`.

**Frontend** — `paginas/abas/AbaServidores.tsx` (visão do administrador por
unidade), `paginas/MinhasUnidades.tsx` (visão do magistrado) e o painel
compartilhado `paginas/abas/PainelDaLista.tsx`.

**Testes** — `ServidorHabilitadoIT` cobre CA-1 a CA-6 e verifica explicitamente
que ressemear **preserva os ajustes manuais**: não remove inclusões MANUAL e não
reativa quem foi removido de propósito.

### Decisões tomadas na implementação

- **O administrador também gerencia a lista em edição de rascunho.** RF-3 fala em
  "qualquer edição publicada", mas a semeadura acontece naturalmente logo após o
  cadastro dos reconhecimentos, quando a edição ainda está em rascunho. A
  permissão é aditiva e inofensiva (nada foi emitido ainda). O magistrado segue
  restrito à edição vigente e às suas unidades, como manda o RF-4.
- **Reativar é ação explícita.** Incluir um CPF que consta como removido reativa
  o registro (com origem MANUAL); a semeadura, não. Assim a inclusão manual
  continua funcionando sem que o RH desfaça uma remoção deliberada.
- **A lista só existe para unidade reconhecida na edição**; tentar gerenciar
  outra dá 422 com mensagem explícita.
- O DTO devolve CPF completo e mascarado; a tela usa o mascarado (008/RNF-2).

### Ajuste de 2026-09-01 — CPF só para quem edita (RNF-2)

A varredura mostrou que o DTO devolvia o CPF completo para qualquer consulta,
inclusive a de um magistrado olhando unidade que não é dele. Como o CPF completo
só é necessário para **remover** alguém da lista, ele passou a acompanhar apenas
a resposta de quem pode editar; os demais recebem somente o mascarado. Coberto
por `ServidorHabilitadoIT.cpfSoParaQuemEdita`.
