# Plano técnico: Cadastro de magistrados reconhecidos

- **ID:** 004-cadastro-magistrados-reconhecidos
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**. Inclui as unidades judiciárias selecionadas a partir do EGESP.

## 1. Abordagem

Três entidades: `UnidadeJudiciaria` (espelho local das unidades do EGESP),
`MagistradoReconhecido` (por edição) e `Reconhecimento` (magistrado × unidade ×
selo). As unidades **vêm do EGESP**: o admin escolhe da lista retornada pela
porta `EgespClient`, e o **nome cru do EGESP** é persistido para **identificar a
unidade** ao semear a lista de servidores (feature 008). O conjunto de
reconhecimentos de uma edição é a fonte de verdade consumida por 005, 006 e 008.
Uma consulta agregada expõe, por edição, as unidades reconhecidas (por nome) com
seus selos.

## 2. Stack e dependências

- **Backend:** Spring Web + Spring Data JPA; Bean Validation (CPF, obrigatórios).
  Porta `EgespClient` (introduzida aqui), com `MockEgespClient` nesta fase —
  método `listarUnidades()`. A feature **008** acrescenta
  `listarServidoresPorUnidade(...)`. A emissão (006) **não** usa o EGESP.
- **Frontend:** React — seleção de unidade a partir da lista do EGESP
  (autocomplete/busca) e formulário de magistrados/reconhecimentos (múltiplas
  linhas unidade+selo).
- **Banco:** PostgreSQL.

## 3. Arquitetura

```
[React admin] --listar unidades--> UnidadeController --> EgespClient(mock).listarUnidades()
              --selecionar/salvar-->                  --> UnidadeService (upsert por nome)
              --> MagistradoController    --> MagistradoService   --> MagistradoRepository
                                                  '--> ReconhecimentoRepository
              --> (consulta) UnidadesReconhecidasQuery (agrega selos por unidade/edição)
```

- `EgespClient.listarUnidades() -> List<UnidadeEgesp{ nome }>` (mock nesta fase).
- Ao selecionar, faz-se **upsert por nome** em `unidade_judiciaria`, preservando
  o nome exatamente como veio do EGESP (sem reescrever/normalizar o valor salvo).

## 4. Modelo de dados

- `unidade_judiciaria`
  - `id` (PK), `nome` (texto, **identificador da unidade no EGESP**), `ativo`
  - **unique** sobre uma forma **canônica** do nome
    (`nome_canonico` = trim + lowercase + sem acentos), mantendo `nome` cru para
    exibição/impressão. A comparação com a lotação do servidor (006) usa
    `nome_canonico`.
- `magistrado_reconhecido`
  - `id` (PK), `edicao_id` (FK), `cpf`, `nome`
  - **unique** (`edicao_id`, `cpf`)
- `reconhecimento`
  - `id` (PK), `magistrado_id` (FK), `unidade_id` (FK), `selo` (enum)
  - **unique** (`magistrado_id`, `unidade_id`)  ← RF-5
- `Selo` enum com **valor ordinal**: BRONZE(1) < PRATA(2) < OURO(3) < DIAMANTE(4)
  (base da regra do maior selo na feature 006).

Consulta agregada (RF-9): por `edicao_id`, agrupar `reconhecimento` por
`unidade_id`, coletando os selos distintos e o **máximo**.

## 5. Contratos / APIs (perfil Administrador)

Unidades (origem EGESP):
- `GET /api/unidades/egesp` → lista de unidades vinda do `EgespClient` (para o
  admin selecionar). Pode aceitar `?q=` para busca.
- `POST /api/unidades` `{ nome }` → upsert por nome canônico; persiste o `nome`
  cru do EGESP. Retorna a unidade local. (Normalmente chamado implicitamente ao
  salvar um reconhecimento.)
- `GET /api/unidades` → unidades já selecionadas/salvas.

Magistrados/reconhecimentos (no contexto de uma edição):
- `POST /api/edicoes/{edicaoId}/magistrados`
  `{ cpf, nome, reconhecimentos: [{ unidadeId, selo }] }` → `201`.
- `GET /api/edicoes/{edicaoId}/magistrados` → lista com reconhecimentos.
- `PUT /api/edicoes/{edicaoId}/magistrados/{id}` → edita dados/reconhecimentos.
- `DELETE /api/edicoes/{edicaoId}/magistrados/{id}`.
- `GET /api/edicoes/{edicaoId}/unidades-reconhecidas`
  → `[{ unidadeId, nome, selos: [...], maiorSelo }]` (RF-9, consumido por 006).
- `POST /api/edicoes/{edicaoId}/magistrados/importar` (multipart: CSV)
  → relatório `{ criados: n, magistrados: [...], erros: [{ linha, motivo }] }`.
  Colunas: `cpf, nome, unidade, selo`. Linhas do mesmo CPF são agrupadas; o
  nome da unidade é casado por `nome_canonico` com as unidades do EGESP (cria a
  unidade local se necessário). **Transação por magistrado** (RF-11/RNF-3):
  magistrado com qualquer linha inválida é **rejeitado inteiro** e reportado; os
  válidos são persistidos. Só em Rascunho.
- Erros: `400` CPF inválido; `409` reconhecimento duplicado; bloqueio se a edição
  **não** está em Rascunho (cadastro travado após publicar); `403` não-admin.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Unidade vinda do EGESP (nome como identificador) | Catálogo próprio / texto livre | Nome salvo idêntico ao do EGESP permite localizar a unidade ao semear a lista de servidores (feature 008). |
| Guardar `nome` cru + `nome_canonico` | Só nome cru | Comparação robusta (acentos/caixa/espaços) sem perder o texto oficial para impressão. |
| Selo como enum ordinal | String solta | Habilita comparação "maior selo" de forma confiável. |
| `Reconhecimento` separado | Lista embutida | Modela N unidades por magistrado e a unicidade por unidade. |
| Importação transacional **por magistrado** | Tudo-ou-nada global / linha-a-linha | Não perde o lote por um erro isolado, mas evita magistrado parcialmente importado (RNF-3). |

## 7. Riscos e mitigação

- **Risco:** nome do EGESP variar entre a listagem (004) e a consulta de lotação
  (006) — acento, caixa, espaços → **Mitigação:** comparar por `nome_canonico`;
  logar lotações sem correspondência para curadoria.
- **Risco:** edição de reconhecimentos após emissões → **Mitigação:** avaliar
  bloqueio/aviso quando já houver certificados emitidos.

## 8. Estratégia de testes

- Integração: múltiplos reconhecimentos por magistrado (CA-1); duplicidade de
  unidade rejeitada (CA-2); agregação de selos por unidade (CA-3); CPF inválido
  (CA-4); 403 não-admin (CA-5); importação CSV agrupa por CPF e reporta linhas
  inválidas sem perder as válidas (CA-6).

## 9. Pontos em aberto

- **RESOLVIDO:** nome salvo cru do EGESP, comparado por `nome_canonico`; usado
  para **identificar a unidade ao semear** a lista de servidores (008), não na
  emissão.
- [NEEDS CLARIFICATION: o EGESP oferece endpoints de `listarUnidades()` e
  `listarServidoresPorUnidade()` (mock nesta fase)? Adiado por dependência externa.]
- **RESOLVIDO:** importação CSV **incluída** nesta fase (transação por magistrado,
  relatório de erros por linha).
