# Plano técnico: Base de dados independente por edição

- **ID:** 011-base-por-edicao
- **Spec relacionada:** ./spec.md
- **Status:** aprovado

## 1. Abordagem

Um **schema do PostgreSQL por edição**, dentro do mesmo banco. O schema
compartilhado guarda apenas o catálogo de edições e o índice de verificação
pública; todo o resto das tabelas passa a existir, repetido e vazio, dentro de
`edicao_<ano>`.

O isolamento vem da estrutura, não da disciplina: a conexão de cada requisição
aponta para o schema da edição da sessão, e uma consulta que esqueça de filtrar
por ano simplesmente não enxerga outro ano. É o que atende o RNF-1 — a
alternativa (uma coluna `edicao_id` em cada tabela) deixaria dezenas de consultas
dependendo de ninguém esquecer o filtro.

Como o schema é escolhido por conexão, **o código de domínio não muda**. Os
repositórios, serviços e controladores continuam como estão, inclusive as colunas
`edicao_id` que já existem nas tabelas versionadas — dentro do schema da edição
elas sempre casam, e mantê-las evita reescrever consultas que funcionam.

## 2. Stack e dependências

Nada novo. Multi-tenancy por schema é recurso do Hibernate 6 (já no projeto, via
Spring Boot 3.5) e schema é recurso do PostgreSQL e do H2. Nenhuma variável de
ambiente nova, nenhum pedido à infraestrutura do tribunal (RNF-2).

## 3. Arquitetura

```
  requisição  --> FiltroJwt        lê o token, popula o SecurityContext
                     |
                     v
              FiltroDaEdicao       decide a edição da requisição e a guarda
                     |             num ThreadLocal; limpa no finally
                     v
              ResolvedorDeEdicao   (CurrentTenantIdentifierResolver)
                     |             devolve o schema da edição corrente
                     v
              ConexaoPorEdicao     (MultiTenantConnectionProvider)
                     |             SET search_path TO edicao_2026, public
                     v
              JPA / repositórios   sem mudança nenhuma
```

A edição da requisição vem, nesta ordem:

1. da claim `edicao` do JWT — é o caso normal e atende o RF-5: o navegador não
   escolhe a edição mandando um cabeçalho, ele carrega a sessão que o servidor
   emitiu;
2. da edição vigente, quando não há token (rotas públicas: verificação, login);
3. da edição que o próprio serviço fixar, em trabalho de sistema que atravessa
   edições (login, migração da subida, verificação pública).

### Schemas

```
public                        (compartilhado)
  edicao                      catálogo: ano, status, vigente, schema_dados
  certificado_indice          codigo_validacao -> edicao_id
  databasechangelog(lock)     controle do changelog compartilhado
  legado_*                    tabelas de antes da migração, preservadas

edicao_2026                   (uma por edição)
  usuario, usuario_papel, administrador,
  unidade_judiciaria, magistrado_reconhecido, reconhecimento,
  layout_certificado, arte_layout, servidor_habilitado,
  certificado_emitido
  databasechangelog(lock)     controle do changelog da edição
```

O nome do schema fica gravado em `edicao.schema_dados` em vez de ser derivado do
ano toda vez: a convenção é `edicao_<ano>`, mas quem lê o dado não deve depender
dela.

### Migrações

O changelog atual (`db.changelog-master.yaml`, migrações 001–011) **não é
tocado**: ele já rodou em homologação e o Liquibase guarda os checksums. Ele
passa a ser o changelog do schema compartilhado, e ganha a migração `012`, que
cria `certificado_indice` e a coluna `schema_dados`.

Nasce um segundo changelog, `db/edicao/changelog-edicao.yaml`, com o DDL
consolidado das tabelas de uma edição. Ele não é aplicado pelo Spring Boot na
subida, e sim pelo `BaseDaEdicao`, para cada schema, com `defaultSchemaName`
apontado para ele — o que dá a cada edição o seu próprio `databasechangelog` e
permite que uma edição criada daqui a três anos receba o esquema em vigor naquele
momento.

### Subida da aplicação

1. Spring Boot aplica o changelog compartilhado em `public`.
2. `BaseDaEdicao` (antes do `EntityManagerFactory`, via
   `EntityManagerFactoryDependsOnPostProcessor`):
   - cria a edição do ano corrente se não houver nenhuma (RF-13);
   - para cada edição, garante o schema e aplica o changelog da edição;
   - na primeira vez, **migra os dados legados** (abaixo).
3. `SuperadminInicial` roda por edição, para que o superadministrador das
   variáveis de ambiente exista em todas.
4. `DadosDemo` e `ImportadorDeArtesDoDisco` rodam na edição vigente.

O passo 2 precisa anteceder o `EntityManagerFactory` porque `ddl-auto: validate`
valida o mapeamento contra o schema resolvido — que já tem de existir.

### Migração dos dados que existem (RF-12)

Roda uma vez, quando `public.edicao.schema_dados` ainda está nulo. Para cada
edição já cadastrada:

- cria `edicao_<ano>` e aplica o changelog da edição;
- copia para lá as tabelas hoje compartilhadas — `usuario`, `usuario_papel`,
  `administrador`, `unidade_judiciaria` (com `responsavel_id`) e `arte_layout` —,
  preservando os ids, para que as chaves estrangeiras continuem casando;
- copia as linhas daquela edição em `magistrado_reconhecido`, `reconhecimento`,
  `layout_certificado`, `servidor_habilitado` e `certificado_emitido`;
- preenche `certificado_indice` com os códigos daquela edição.

No fim, as tabelas originais em `public` são **renomeadas** para `legado_*`, não
apagadas: elas são o backup imediato da migração, e o nome novo garante que o
`search_path` não caia nelas por engano se um schema de edição vier incompleto.

## 4. Modelo de dados

Duas mudanças de esquema, ambas no schema compartilhado:

| Tabela | Campo | Observação |
|---|---|---|
| `edicao` | `schema_dados VARCHAR(63)` | nome do schema da edição; nulo só antes da migração |
| `certificado_indice` | `codigo_validacao VARCHAR(32)` PK | código impresso no certificado |
| | `edicao_id BIGINT` FK `edicao` | onde encontrar o certificado |

Dentro do schema de cada edição, o esquema é exatamente o de hoje. As colunas
`edicao_id` permanecem: são redundantes dentro do schema, mas mantêm o código e
as consultas atuais válidos sem reescrita.

## 5. Contratos / APIs

| Método | Rota | O que muda |
|---|---|---|
| POST | `/api/auth/login`, `/api/auth/sso/*` | a resposta passa a trazer a edição da sessão e a lista de edições em que a pessoa existe |
| POST | `/api/auth/edicao/{edicaoId}` | **nova**: troca a edição da sessão e devolve um token novo, com os papéis daquela edição. Recusa (403) edição em que a pessoa não existe |
| GET | `/api/auth/sessao` | passa a incluir `edicao` e `edicoesDisponiveis` |
| POST | `/api/edicoes` | além de criar a linha, cria o schema, aplica o changelog e semeia os superadministradores (RF-3) |
| GET | verificação pública por código | resolve a edição pelo índice antes de ler o certificado |

As demais rotas ficam iguais. As que trazem `{edicaoId}` no caminho passam a
**exigir** que ele seja o da sessão: divergência é 403, não uma consulta em outra
base.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Isolamento por schema | coluna `edicao_id` em tudo; banco por edição | Schema isola por construção, sem pedir nada à infra. A coluna deixaria o isolamento na mão de dezenas de consultas; banco separado exigiria provisionamento a cada ano. |
| Edição na sessão (JWT) | cabeçalho `X-Edicao`; parâmetro na rota | O cabeçalho seria escolha do navegador — qualquer um trocaria de base trocando um header. Na sessão, a troca passa por um endpoint que valida o direito. |
| Manter as colunas `edicao_id` | removê-las do esquema por edição | Removê-las obrigaria a reescrever repositórios, serviços e testes de cinco features que hoje funcionam. A redundância é barata. |
| `edicao` só no schema compartilhado | uma cópia em cada schema | O catálogo é o que o login percorre; duplicá-lo criaria dois lugares para a mesma verdade. As FKs `edicao_id` dentro do schema perdem a referência declarada — o schema já é a garantia. |
| Índice de verificação no compartilhado | varrer todos os schemas ao verificar | A rota pública é anônima e limitada por taxa; varrer N schemas a cada tentativa é caro e cresce todo ano. |
| Tabelas antigas renomeadas, não apagadas | `DROP TABLE` | Migração dos dados de homologação em uma tacada: manter o original renomeado é o que permite conferir e voltar atrás. |
| Superadministrador semeado na edição nova | edição totalmente vazia | Uma edição sem superadministrador não pode ser administrada por ninguém — nasceria morta. |

## 7. Riscos e mitigação

- **Risco:** a migração dos dados de homologação erra e mistura edições. →
  **Mitigação:** as tabelas originais ficam como `legado_*`, e a migração é
  idempotente (só roda com `schema_dados` nulo). Conferência por contagem de
  linhas antes e depois, registrada no log.
- **Risco:** um schema de edição fica sem uma tabela e o `search_path` cai no
  compartilhado, misturando dados. → **Mitigação:** o compartilhado não tem mais
  nenhuma dessas tabelas depois da renomeação; a consulta falha alto em vez de
  responder errado.
- **Risco:** `ddl-auto: validate` falha na subida porque o schema da edição ainda
  não existe. → **Mitigação:** `BaseDaEdicao` é dependência declarada do
  `EntityManagerFactory`.
- **Risco:** criar uma edição passa a ser DDL, que não volta atrás num rollback de
  transação. → **Mitigação:** a criação do schema é idempotente
  (`CREATE SCHEMA IF NOT EXISTS`); um schema órfão é inerte.
- **Risco:** o login passa a percorrer todas as edições e fica lento com o tempo.
  → **Mitigação:** é uma consulta por e-mail em tabela indexada, uma por ano. Se
  incomodar, entra um índice de acesso no compartilhado.
- **Risco:** os testes de integração rodam em transação com rollback, e criar
  edição cria schema. → **Mitigação:** em H2 na memória, o schema órfão morre com
  a JVM; a criação é idempotente.

## 8. Estratégia de testes

- `BasePorEdicaoIT` — o teste central do RF-11: monta duas edições com dados
  diferentes e verifica que cada uma só enxerga os seus (CA-1, CA-2).
- `AcessoPorEdicaoIT` — login de quem existe em uma, nas duas e em nenhuma
  edição; troca de edição pelo endpoint; papéis diferentes por edição (CA-3, CA-4,
  CA-5, CA-7).
- `VerificacaoPublicaIT` — acrescenta o caso do código de edição não vigente
  (CA-6).
- `MigracaoDeBaseIT` — monta o esquema antigo em `public`, roda a migração e
  confere que cada edição recebeu o que era dela e que `legado_*` preservou o
  original (CA-8).
- A suíte atual é a verificação do RNF-4: ela precisa passar sem mudança de
  asserção — só de fixture, onde o teste montar duas edições.

## 9. Pontos em aberto

Nenhum.
