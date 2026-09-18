# Tarefas: Base de dados independente por edição

- **ID:** 011-base-por-edicao
- **Plano relacionado:** ./plan.md
- **Status:** em execução

> Quebra do plano em passos pequenos, ordenados e verificáveis.
> Cada tarefa deve poder ser concluída e revisada de forma independente.

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída
- Marque `[P]` tarefas que podem rodar em **paralelo** (sem dependência mútua).
- Cada tarefa referencia o requisito da spec que satisfaz (ex.: `RF-1`).

## Tarefas

### Esquema

- [ ] **T-001** — Migração `012` no changelog compartilhado: coluna
  `edicao.schema_dados` e tabela `certificado_indice`. _(satisfaz: RF-1, RF-10)_
- [ ] **T-002** [P] — Changelog da edição (`db/edicao/`): DDL consolidado das dez
  tabelas que passam a viver dentro do schema de cada edição. _(satisfaz: RF-1)_

### Isolamento

- [ ] **T-003** — Multi-tenancy por schema: `EdicaoCorrente` (contexto por
  thread), `ResolvedorDeEdicao` e `ConexaoPorEdicao` ajustando o `search_path`.
  _(satisfaz: RF-1, RNF-1; depende de: T-002)_
- [ ] **T-004** — `BaseDaEdicao`: na subida, cria a edição do ano corrente se não
  houver nenhuma, e garante schema + changelog de cada edição, antes do
  `EntityManagerFactory`. _(satisfaz: RF-13; depende de: T-003)_
- [ ] **T-005** — Migração dos dados que existem para o schema de cada edição e
  renomeação das tabelas antigas para `legado_*`. _(satisfaz: RF-12; depende de:
  T-004)_
- [ ] **T-006** — `FiltroDaEdicao`: resolve a edição da requisição (claim do token
  → vigente) e limpa o contexto no fim. _(satisfaz: RF-4, RF-5; depende de:
  T-003)_

### Acesso

- [ ] **T-007** — Claim `edicao` no JWT; resposta de login e de sessão passam a
  trazer a edição corrente e as edições disponíveis. _(satisfaz: RF-5, RF-6;
  depende de: T-006)_
- [ ] **T-008** — `AcessoPorEdicao`: procura a pessoa em todas as edições, escolhe
  a de entrada (vigente, senão a mais recente) e resolve os papéis dela naquela
  edição. Recusa quem não existe em nenhuma. _(satisfaz: RF-7, RF-8, RF-9;
  depende de: T-007)_
- [ ] **T-009** — `POST /api/auth/edicao/{id}`: troca a edição da sessão, com
  token novo e papéis da edição de destino. _(satisfaz: RF-6; depende de: T-008)_
- [ ] **T-010** — Guarda nas rotas com `{edicaoId}` no caminho: divergir da edição
  da sessão é 403. _(satisfaz: RF-11; depende de: T-006)_

### Ciclo de vida da edição

- [ ] **T-011** — Criar edição passa a criar o schema, aplicar o changelog e
  semear os superadministradores da edição de origem. _(satisfaz: RF-2, RF-3;
  depende de: T-004)_
- [ ] **T-012** — `SuperadminInicial` por edição; `DadosDemo` e importador de
  artes na edição vigente. _(satisfaz: RF-3; depende de: T-004)_

### Verificação pública

- [ ] **T-013** — Gravar o código no `certificado_indice` ao emitir e resolver a
  edição por ele na verificação pública. _(satisfaz: RF-10; depende de: T-001)_

### Interface

- [ ] **T-014** — Seletor de edição no topo, sempre visível, com as edições
  disponíveis; trocar recarrega a sessão. _(satisfaz: RF-4, RF-6; depende de:
  T-009)_
- [ ] **T-015** — Tela de entrada e mensagens: quem não tem cadastro em edição
  nenhuma recebe a explicação do RF-9. _(satisfaz: RF-9; depende de: T-008)_

### Verificação

- [ ] **T-016** — `BasePorEdicaoIT` e `AcessoPorEdicaoIT`. _(satisfaz: CA-1 a
  CA-5, CA-7)_
- [ ] **T-017** — Caso de edição não vigente em `VerificacaoPublicaIT` e
  `MigracaoDeBaseIT`. _(satisfaz: CA-6, CA-8)_
- [ ] **T-018** — Suíte atual passando sem mudança de asserção. _(satisfaz:
  RNF-4)_

### Registro

- [ ] **T-019** — Decisão de implementação (DI-30) e diário da frente.

## Definição de pronto (Definition of Done)

- [ ] Todos os critérios de aceitação da spec verificados
- [ ] Testes correspondentes passando
- [ ] Suíte existente verde sem afrouxar asserção
- [ ] Constituição atualizada: o princípio 3b (snapshot por edição) passa a ser
      consequência do isolamento, não uma regra isolada
