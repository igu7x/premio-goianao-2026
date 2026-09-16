# Tarefas: Cadastro de magistrados reconhecidos

- **ID:** 004-cadastro-magistrados-reconhecidos
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Introduz a porta `EgespClient` (listagem de unidades) e fecha o lookup de
> Magistrado da feature 001.

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.

## Tarefas

- [x] **T-001** — Porta `EgespClient` + `MockEgespClient.listarUnidades()`
  (unidades de teste) _(satisfaz: RF-1)_
- [x] **T-002** — Migração `unidade_judiciaria` (`nome` cru, `nome_canonico`
  **unique**, ativo) _(satisfaz: RF-1)_
- [x] **T-003** — Migração `magistrado_reconhecido` (unique edicao+cpf) e
  `reconhecimento` (unique magistrado+unidade) _(satisfaz: RF-2, RF-3, RF-5, RF-10)_
- [x] **T-004** — `GET /api/unidades/egesp` (lista do EGESP) e **upsert** local por
  `nome_canonico` preservando o nome cru _(satisfaz: RF-1)_ _(depende de: T-001, T-002)_
- [x] **T-005** — `POST/GET/PUT/DELETE` magistrados+reconhecimentos: valida **CPF**,
  rejeita reconhecimento **duplicado** (`409`), bloqueia se edição não está em
  **Rascunho** _(satisfaz: RF-2..RF-8)_ _(depende de: T-003, T-004)_
- [x] **T-006** [P] — `GET /api/edicoes/{id}/unidades-reconhecidas` (agrega selos
  por unidade + `maiorSelo`) _(satisfaz: RF-9)_ _(depende de: T-003)_
- [x] **T-007** — `POST .../magistrados/importar` (CSV): agrupa por CPF, casa a
  unidade por `nome_canonico`, **transação por magistrado**, **relatório de erros**
  por linha _(satisfaz: RF-11, RNF-3)_ _(depende de: T-004, T-005)_
- [x] **T-008** — Conectar o lookup de **Magistrado** no `PapeisResolver`
  (feature 001) ao cadastro real _(satisfaz: RF-2; 001/RF-5)_ _(depende de: T-003)_
- [x] **T-009** — RBAC: somente Administrador _(satisfaz: RNF-1)_ _(depende de: T-005, T-007)_
- [x] **T-010** [P] — Frontend: seleção de unidade via **autocomplete do EGESP**,
  formulário de magistrado com **múltiplas linhas (unidade+selo)** e **upload CSV**
  com exibição do relatório _(satisfaz: RF-1, RF-3, RF-11)_ _(depende de: T-004, T-005, T-007)_
- [x] **T-011** — Testes: múltiplos reconhecimentos (CA-1); duplicidade (CA-2);
  agregação de selos (CA-3); CPF inválido (CA-4); `403` não-admin (CA-5); CSV
  agrupa por CPF e reporta linha inválida sem perder válidas (CA-6)
  _(satisfaz: RF-2..RF-11)_ _(depende de: T-005, T-006, T-007, T-009)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-6 verificados.
- [x] Nome de unidade salvo idêntico ao do EGESP (cru) + `nome_canonico` para casar.
- [x] Perfil Magistrado da feature 001 passa a refletir o cadastro real.
- [x] Testes passando.

## Implementação (2026-09-01)

**Backend** — `br.jus.tjgo.goianao.magistrado`, `.unidade` e `.integracao.egesp`

| O que | Onde |
|---|---|
| Porta do EGESP e mock | `integracao/egesp/EgespClient.java`, `MockEgespClient.java` |
| Espelho local da unidade (nome cru + canônico) | `unidade/UnidadeJudiciaria.java`, `unidade/UnidadeService.java` |
| Entidades do cadastro | `magistrado/MagistradoReconhecido.java`, `magistrado/Reconhecimento.java` |
| Regras de cadastro e elegibilidade | `magistrado/MagistradoService.java` |
| Leitura da planilha | `magistrado/ImportadorCsv.java` |
| Importação com transação por magistrado | `magistrado/ImportacaoMagistradosService.java` |
| Unidades reconhecidas com selos e maior selo | `magistrado/UnidadesReconhecidasController.java` |
| Lookup de MAGISTRADO da feature 001 | `magistrado/MagistradoLookupJpa.java` |

**Frontend** — `paginas/abas/AbaMagistrados.tsx`: seleção de unidade a partir do
EGESP, formulário com várias linhas unidade+selo e upload do CSV com o relatório
de erros linha a linha.

**Testes** — `MagistradoIT` (CA-1 a CA-6 e o bloqueio pós-publicação),
`ImportadorCsvTest` (separador, aspas, BOM, arquivo sem dados) e `TextoTest`
(canonicalização).

### Decisões tomadas na implementação

- **O nome da unidade é conferido contra o EGESP no servidor.** RF-1 diz que o
  administrador não digita unidades livremente; para que isso valha mesmo com a
  requisição manipulada, `UnidadeService.garantirDoEgesp` rejeita nome que não
  exista no catálogo. A API aceita `unidadeId` (quando já espelhada) ou
  `unidadeNome`.
- **Leitor de CSV próprio**, sem biblioteca: o formato é simples e conhecido, e
  os pontos que realmente quebram planilhas exportadas do Excel — separador
  `;` ou `,`, BOM, aspas, acentuação — ficam sob controle e testados.
- **Transação por magistrado** obtida sem `REQUIRES_NEW`: o orquestrador da
  importação não é transacional e cada `MagistradoService.criar` abre a sua.

### Conflito com a 009, resolvido em 2026-09-01

A **009/T-003** diz que `criar` e `importar` passam a aceitar também a edição
vigente. Prevaleceu a **RF-11 desta spec** — "Disponível só em Rascunho" — porque
é requisito numerado e explícito, e porque o §3 do plano da 009 troca a regra
apenas em `criar`. Assim, `POST .../magistrados` aceita a vigente (009), mas
`POST .../magistrados/importar` continua restrito ao rascunho.

O motivo de fundo: a inclusão na vigente foi desenhada para o caso pontual — um
reconhecido que faltou —, enquanto subir uma planilha inteira numa edição já em
uso é operação de outra escala. Para reverter, basta trocar
`exigirRascunhoParaImportacao` por `exigirElegivelParaInclusao` em
`ImportacaoMagistradosService` e emendar a RF-11. Coberto por
`MagistradoIT.importacaoSoEmRascunho`.

### Ajuste de 2026-09-16 — unidade escolhida digitando, e linha alinhada

Com as ~190 unidades do TJGO carregadas, o `<select>` do reconhecimento virou uma
lista que estourava a largura do modal e obrigava a rolar até achar a unidade.
Além disso, a dica embaixo do campo empurrava o rótulo e o controle do **Selo** e
o botão de remover para fora da linha.

- **Busca no lugar do select** (`SeletorDeUnidade`, em
  `paginas/abas/AbaMagistrados.tsx`): o administrador digita parte do nome ou o
  código SIEDOS e escolhe numa lista que abre logo abaixo do campo. Cada palavra
  precisa aparecer no nome ou no código, sem diferenciar acento nem maiúsculas.
  Setas, Enter e Esc funcionam; o Esc fecha só a lista, não o modal. A RF-1 continua
  valendo: o texto digitado não vira unidade enquanto não for escolhido da lista.
- **Linha do reconhecimento** (`.linha-reconhecimento`, em `paginas.css`): a
  dica saiu da linha e fica uma vez só, abaixo de todas as unidades; campo,
  selo e remover têm a mesma altura (42px) e se alinham pela base.
- **Testes:** `AbaMagistrados.test.tsx` passou a digitar e escolher a opção em
  vez de `selectOptions`, e verifica o filtro pelo código.
