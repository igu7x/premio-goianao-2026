# Plano técnico: Inclusão de reconhecimentos na edição vigente

- **ID:** 009-inclusao-reconhecimentos-vigente
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**. Estende a feature 004 com uma regra de elegibilidade e um endpoint
> aditivo; reutiliza entidades, serviços e validações existentes.

## 1. Abordagem

Trocar a verificação rígida `exigirRascunho` (usada na criação de magistrados) por
uma regra **elegibilidade para inclusão**:

```
podeIncluir(edicao) = edicao.status == RASCUNHO
                   OR (edicao.status == PUBLICADA && edicao.vigente)
```

A **criação de magistrado** passa a usar `podeIncluir`. Acrescenta-se uma operação
**aditiva** para incluir um reconhecimento em magistrado existente. As operações
**edição** (`PUT`) e **remoção** (`DELETE`) continuam restritas a **Rascunho**
(`exigirRascunho`), pois são destrutivas para a fidelidade.

Nenhuma mudança de modelo de dados: as constraints atuais já garantem a
integridade. A unidade nova vira "reconhecida" automaticamente, então a feature
008 (lista de servidores) passa a gerenciá-la sem etapa extra (a 008 já valida
`reconhecimentoRepo.existeNaEdicao`).

## 2. Stack e dependências

- **Backend:** reutiliza `MagistradoService`, `MagistradoRepository`,
  `UnidadeService`, `EdicaoService`, `ReconhecimentoRepository` (feature 004).
- **Frontend:** ajuste na tela **Magistrados & Unidades** para habilitar o modo
  **somente-inclusão** quando a edição selecionada for a **vigente**.
- Sem novas dependências, sem migração de banco.

## 3. Arquitetura

```
[admin] --POST /magistrados--------------> MagistradoService.criar (usa podeIncluir)
[admin] --POST /magistrados/{id}/reconhecimentos--> MagistradoService.adicionarReconhecimento
                                                     (podeIncluir + unicidade + upsert unidade EGESP)
[008] consulta unidades reconhecidas -> nova unidade já aparece (sem mudança)
```

- `MagistradoService.podeIncluir(edicaoId)`: aplica a regra acima; lança
  `ConflitoException` (edição não elegível) caso contrário.
- `MagistradoService.criar(...)`: passa a chamar `podeIncluir` em vez de
  `exigirRascunho`.
- `MagistradoService.adicionarReconhecimento(edicaoId, magistradoId, unidadeNome, selo)`:
  valida elegibilidade; carrega o magistrado (e confere que pertence à edição);
  rejeita unidade já reconhecida para ele (409); faz `upsert` da unidade (EGESP);
  adiciona o `Reconhecimento`.
- `atualizar(...)` e `remover(...)`: mantêm `exigirRascunho`.

## 4. Modelo de dados

Sem alterações. Reutiliza `magistrado_reconhecido` (unique `edicao_id,cpf`),
`reconhecimento` (unique `magistrado_id,unidade_id`) e `unidade_judiciaria`.

## 5. Contratos / APIs (perfil Administrador)

- `POST /api/edicoes/{edicaoId}/magistrados` **(existente, regra ampliada)** —
  passa a aceitar também a **edição vigente** (além de Rascunho). `409` para
  CPF duplicado na edição; `409` se a edição **não for elegível** (publicada não
  vigente).
- `POST /api/edicoes/{edicaoId}/magistrados/{id}/reconhecimentos`
  `{ unidadeNome, selo }` → `201`. `409` se a unidade já é reconhecimento do
  magistrado; `409` se a edição não for elegível; `404` se o magistrado não
  pertence à edição.
- `PUT`/`DELETE` de magistrados — **inalterados** (apenas Rascunho).

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Inclusão aditiva na vigente | Travar tudo após publicar | Adicionar não quebra certificados emitidos; editar/remover sim → estes ficam só em Rascunho. |
| Apenas a **vigente** (não qualquer publicada) | Qualquer publicada | Edições anteriores devem permanecer congeladas (fidelidade da reemissão). |
| Endpoint aditivo dedicado p/ reconhecimento | Reusar `PUT` (substitui tudo) | `PUT` é destrutivo; um `POST .../reconhecimentos` é claramente aditivo e idempotente por unicidade. |

## 7. Riscos e mitigação

- **Risco:** incluir selo **sem layout** na vigente → emissão indisponível. →
  **Mitigação (resolvida):** **pré-condição de publicação** na feature 002 — a
  edição só publica com os **8 layouts** configurados; logo, toda vigente já tem
  layout para qualquer selo. Esta feature **depende** dessa regra na 002.
- **Risco:** elevar o **maior selo** de uma unidade muda (re)emissões de
  servidores. → **Mitigação:** documentar; o maior selo é calculado na emissão e
  reflete o estado atual da edição vigente (comportamento aceito).
- **Risco:** uso indevido para "burlar" o congelamento de edições antigas. →
  **Mitigação:** `podeIncluir` restringe estritamente à vigente; edições
  anteriores bloqueadas.

## 8. Estratégia de testes

- Unit: `podeIncluir` cobre Rascunho (true), Publicada+vigente (true),
  Publicada não-vigente (false).
- Integração: adicionar magistrado na vigente (CA-1); adicionar reconhecimento
  (CA-2); unidade duplicada → 409 (CA-3); edição publicada não vigente bloqueia
  (CA-4); nova unidade vira gerenciável na 008 (CA-5); `403` não-admin (CA-6);
  Rascunho continua aceitando (CA-7); e **regressão**: `PUT`/`DELETE` seguem
  bloqueados em edição publicada.

## 9. Pontos em aberto

- **RESOLVIDO:** sem inclusão de layout na vigente; garante-se layout via
  **pré-condição de publicação (8 layouts)** na feature 002 (ver §10).
- **RESOLVIDO:** elevar o maior selo recalcula na emissão (vigente "viva").

## 10. Dependência: pré-condição de publicação (feature 002)

Esta feature pressupõe uma **alteração na feature 002**: a operação **publicar**
(`RASCUNHO → PUBLICADA`) só pode ocorrer se a edição tiver os **8 layouts**
(4 selos × 2 tipos). Implementação sugerida:

- `LayoutRepository.countByEdicaoId(edicaoId)` (ou contagem de combinações
  distintas) deve ser **8** para publicar.
- A validação fica no fluxo de publicação. Para não acoplar o pacote `edicao` ao
  `layout`, expor uma **porta** `PreRequisitosPublicacao` (no pacote `edicao`)
  implementada no pacote `layout`, consultada por `EdicaoService.publicar`.
- Mensagem de erro lista as combinações selo×tipo **pendentes** (reaproveita a
  consulta de pendências da feature 003).

> Em consequência, o frontend da feature 002 deve sinalizar quando faltam layouts
> e bloquear o botão **Publicar** até completar os 8.
