# Tarefas: Base de dados independente por edição

- **ID:** 011-base-por-edicao
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Quebra do plano em passos pequenos, ordenados e verificáveis.
> Cada tarefa deve poder ser concluída e revisada de forma independente.

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída
- Marque `[P]` tarefas que podem rodar em **paralelo** (sem dependência mútua).
- Cada tarefa referencia o requisito da spec que satisfaz (ex.: `RF-1`).

## Tarefas

### Esquema

- [x] **T-001** — Migração `012` no changelog compartilhado: coluna
  `edicao.schema_dados` e tabela `certificado_indice`. _(satisfaz: RF-1, RF-10)_
- [x] **T-002** [P] — Changelog da edição (`db/edicao/`): DDL consolidado das dez
  tabelas que passam a viver dentro do schema de cada edição. _(satisfaz: RF-1)_

### Isolamento

- [x] **T-003** — Multi-tenancy por schema: `EdicaoCorrente` (contexto por
  thread), `ResolvedorDeEdicao` e `ConexaoPorEdicao` ajustando o `search_path`.
  _(satisfaz: RF-1, RNF-1; depende de: T-002)_
- [x] **T-004** — `BaseDaEdicao`: na subida, cria a edição do ano corrente se não
  houver nenhuma, e garante schema + changelog de cada edição, antes do
  `EntityManagerFactory`. _(satisfaz: RF-13; depende de: T-003)_
- [x] **T-005** — Migração dos dados que existem para o schema de cada edição e
  renomeação das tabelas antigas para `legado_*`. _(satisfaz: RF-12; depende de:
  T-004)_
- [x] **T-006** — `FiltroDaEdicao`: resolve a edição da requisição (claim do token
  → vigente) e limpa o contexto no fim. _(satisfaz: RF-4, RF-5; depende de:
  T-003)_

### Acesso

- [x] **T-007** — Claim `edicao` no JWT; resposta de login e de sessão passam a
  trazer a edição corrente e as edições disponíveis. _(satisfaz: RF-5, RF-6;
  depende de: T-006)_
- [x] **T-008** — `AcessoPorEdicao`: procura a pessoa em todas as edições, escolhe
  a de entrada (vigente, senão a mais recente) e resolve os papéis dela naquela
  edição. Recusa quem não existe em nenhuma. _(satisfaz: RF-7, RF-8, RF-9;
  depende de: T-007)_
- [x] **T-009** — `POST /api/auth/edicao/{id}`: troca a edição da sessão, com
  token novo e papéis da edição de destino. _(satisfaz: RF-6; depende de: T-008)_
- [x] **T-010** — Guarda nas rotas com `{edicaoId}` no caminho: divergir da edição
  da sessão é 403. _(satisfaz: RF-11; depende de: T-006)_

### Ciclo de vida da edição

- [x] **T-011** — Criar edição passa a criar o schema, aplicar o changelog e
  semear os superadministradores da edição de origem. _(satisfaz: RF-2, RF-3;
  depende de: T-004)_
- [x] **T-012** — `SuperadminInicial` por edição; `DadosDemo` e importador de
  artes na edição vigente. _(satisfaz: RF-3; depende de: T-004)_

### Verificação pública

- [x] **T-013** — Gravar o código no `certificado_indice` ao emitir e resolver a
  edição por ele na verificação pública. _(satisfaz: RF-10; depende de: T-001)_

### Interface

- [x] **T-014** — Seletor de edição no topo, sempre visível, com as edições
  disponíveis; trocar recarrega a sessão. _(satisfaz: RF-4, RF-6; depende de:
  T-009)_
- [x] **T-015** — Tela de entrada e mensagens: quem não tem cadastro em edição
  nenhuma recebe a explicação do RF-9. _(satisfaz: RF-9; depende de: T-008)_

### Verificação

- [x] **T-016** — `BasePorEdicaoIT` e `AcessoPorEdicaoIT`. _(satisfaz: CA-1 a
  CA-5, CA-7)_
- [x] **T-017** — Caso de edição não vigente em `VerificacaoPublicaIT` e
  `MigracaoDeBaseIT`. _(satisfaz: CA-6, CA-8)_
- [x] **T-018** — Suíte atual passando sem mudança de asserção. _(satisfaz:
  RNF-4)_

### Registro

- [x] **T-019** — Decisão de implementação (DI-30) e diário da frente.

## Definição de pronto (Definition of Done)

- [x] Todos os critérios de aceitação da spec verificados
- [x] Testes correspondentes passando
- [x] Suíte existente verde sem afrouxar asserção
- [x] Constituição atualizada: o princípio 3b (snapshot por edição) passa a ser
      consequência do isolamento, não uma regra isolada

## Implementação

**Onde vive.** `api/.../edicao/base/`: `EdicaoCorrente` (contexto por thread),
`ConexaoPorEdicao` e `ResolvedorDeEdicao` (multi-tenancy do Hibernate),
`ApontadorDeSchema` e `DataSourceDaEdicao` (a conexão decide o schema),
`BaseDaEdicao` (schemas e changelog na subida, antes do JPA),
`MigracaoDosDadosAnteriores`, `CatalogoDeEdicoes` (catálogo por JDBC),
`FiltroDaEdicao` e `GuardaDaEdicaoDaSessao`. Acesso em `auth/AcessoPorEdicao` e
`auth/MontadorDeSessao`. Esquema da edição em `db/edicao/`; compartilhado ganhou
a `012`. Frontend: `componentes/SeletorDeEdicao.tsx` e a sessão em
`sessao/SessaoContexto.tsx`.

**Decidido no caminho** (detalhes na DI-30):

- A tabela `edicao` ficou fora da validação de esquema do Hibernate, que só
  olha o schema corrente.
- Publicar confere os layouts na base da edição publicada, não na da sessão.
- A guarda de rota (T-010) deixa o catálogo (`/api/edicoes/{id}`) alcançar
  todas as edições: a tela de edições precisa publicar e tornar vigente qualquer
  uma.
- `DadosDemo` deixou de checar "existe edição" — o sistema garante uma na
  subida — e passou a checar se alguma base tem unidade.
- Os testes de integração perderam a transação com rollback; `BasesDeTeste`
  reconstrói a base antes de cada um.
- **As tabelas antigas não são mais renomeadas** para `legado_*`, contra o que
  o plano previa (T-005). Ficam intactas em `public` para que reimplantar a
  versão anterior baste como reversão — em homologação não se roda comando no
  banco. A cópia só roda para edição que chega à subida sem base.

**Verificação.** 267 testes do backend e 49 do frontend passando. Conferido no
navegador contra o banco H2 de desenvolvimento, que tinha dados anteriores: a
migração levou as duas edições para os seus schemas, o seletor troca a base, e o
detalhe de uma edição fora da sessão oferece entrar nela. Em PostgreSQL 16, o
ciclo completo: versão anterior → nova → anterior de novo → nova de novo, sem
tocar no banco e sem duplicar dados (detalhes na DI-30).
