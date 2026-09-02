# Plano técnico: Gestão de edições do prêmio

- **ID:** 002-gestao-edicoes
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**.

## 1. Abordagem

CRUD de `Edicao` restrito a administrador, com unicidade de ano. O ciclo de vida
é simples: `status ∈ {RASCUNHO, PUBLICADA}` mais uma flag booleana `vigente`.
Rascunho não emite; Publicada emite (inclusive reemissão de edições antigas). A
flag `vigente` marca a edição **padrão** e é **única** (no máximo uma true); ela
não bloqueia as demais. Tornar vigente é operação transacional que desmarca a
anterior (que continua Publicada).

**Pré-condição de publicação (RF-3b):** `publicar` só procede se a edição tiver
os **8 layouts** (selo×tipo). Para não acoplar o pacote `edicao` ao `layout`,
define-se uma **porta** `PreRequisitosPublicacao` (no pacote `edicao`),
implementada no pacote `layout` (consulta `LayoutRepository.countByEdicaoId == 8`
ou as pendências da feature 003). `EdicaoService.publicar` consulta a porta e, se
faltar, lança `ConflitoException` listando as combinações pendentes.

## 2. Stack e dependências

- **Backend:** Spring Web + Spring Data JPA; validação Bean Validation.
- **Frontend:** React — tela de listagem/edição de edições (área admin).
- **Banco:** PostgreSQL.

## 3. Arquitetura

```
[React admin] --> EdicaoController --> EdicaoService --> EdicaoRepository --> PostgreSQL
                                          '--> regra: vigente única (transacional)
```

`EdicaoService.tornarVigente(id)`: em uma transação, `UPDATE edicao SET vigente=false WHERE vigente=true`, depois `SET vigente=true` na nova (que deve estar `PUBLICADA`). Índice único parcial garante no máximo uma vigente.

## 4. Modelo de dados

- `edicao`
  - `id` (PK)
  - `ano` (int, **unique**)
  - `descricao` (text, opcional)
  - `status` (enum: `RASCUNHO`, `PUBLICADA`)
  - `vigente` (boolean) — edição padrão
  - `criado_em`, `atualizado_em`
- Índice único parcial: no máximo uma vigente
  (`CREATE UNIQUE INDEX ... ON edicao ((vigente)) WHERE vigente = true`).
- Emissão (005/006) é permitida quando `status = PUBLICADA`, independentemente de
  `vigente`.

## 5. Contratos / APIs (escrita exige Administrador; leituras consumíveis por outras features)

- `POST /api/edicoes` `{ ano, descricao? }` → `201 Edicao` (RASCUNHO). `409` se ano duplicado.
- `GET /api/edicoes` → lista com `status` e `vigente`.
- `GET /api/edicoes/{id}` → detalhe.
- `PUT /api/edicoes/{id}` `{ descricao }` → atualiza dados descritivos.
- `POST /api/edicoes/{id}/publicar` → RASCUNHO → PUBLICADA. `409` se faltarem
  layouts (corpo lista as combinações selo×tipo pendentes — RF-3b).
- `POST /api/edicoes/{id}/vigente` → torna vigente (desmarca anterior; exige PUBLICADA).
- `GET /api/edicoes/vigente` → edição vigente (padrão).
- `GET /api/edicoes/publicadas` → edições emitíveis (para seleção em 005/006).

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| "Vigente única" por índice parcial | Só validação na app | Índice no banco garante invariante mesmo sob concorrência. |
| `status` (Rascunho/Publicada) + flag `vigente` | Status único incluindo "Encerrada" | Vigente é só a padrão; edições antigas seguem emitíveis para reemissão (decisão do produto). |

## 7. Riscos e mitigação

- **Risco:** corrida ao definir vigente → duas vigentes → **Mitigação:** índice
  único parcial + transação.
- **Risco:** mudança de vigente afetar reemissão de edições antigas →
  **Mitigação:** emissão valida apenas `PUBLICADA`; layout/dados são lidos da
  edição-alvo (features 005/006).

## 8. Estratégia de testes

- Unit/integração: ano duplicado rejeitado (CA-2); tornar vigente desmarca a
  anterior mantendo-a Publicada (CA-3); edição antiga publicada segue emitível
  (CA-4); Rascunho bloqueia emissão (CA-5); não-admin recebe 403 (CA-6);
  **publicar sem os 8 layouts é bloqueado** e lista pendências (CA-7).

## 9. Pontos em aberto

- **RESOLVIDO:** status inicial = **RASCUNHO**.
- **RESOLVIDO:** sem encerramento bloqueante; `status ∈ {RASCUNHO, PUBLICADA}` +
  flag `vigente` (padrão). Edições publicadas seguem emitíveis (reemissão).
