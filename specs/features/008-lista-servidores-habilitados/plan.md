# Plano técnico: Gestão da lista de servidores habilitados por unidade

- **ID:** 008-lista-servidores-habilitados
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**. Depende de 004 (unidades reconhecidas) e da porta `EgespClient`.

## 1. Abordagem

Nova entidade `ServidorHabilitado` por (edição, unidade, cpf), semeada a partir
do `EgespClient.listarServidoresPorUnidade(...)` e editável. A autorização do
magistrado é dupla: a edição precisa estar **vigente** e a unidade precisa estar
entre os **reconhecimentos do magistrado** (CPF autenticado) naquela edição. O
administrador edita sem essas restrições (qualquer edição publicada). A emissão
do servidor (006) lê esta tabela — não chama o EGESP.

## 2. Stack e dependências

- **Backend:** Spring Web + Spring Data JPA; Spring Security (regra por papel +
  escopo). Porta `EgespClient.listarServidoresPorUnidade(unidade)` (mock nesta
  fase). Auditoria via colunas `criado_por`/`criado_em` (e remoção lógica).
- **Frontend:** React — tela admin (passo posterior ao cadastro de 004) e tela do
  magistrado (lista das suas unidades na edição vigente).

## 3. Arquitetura

```
[admin]      --> ServidorHabilitadoController --> semear: EgespClient.listarServidoresPorUnidade
[magistrado] -->        (authz por papel/escopo) --> CRUD lista (incluir/remover)
                                                  --> ServidorHabilitadoRepository
[006 emissão] -->  consulta elegibilidade (CPF ∈ lista da unidade/edição)
```

Guardas:
- `PodeEditarLista`: admin → sempre (edição publicada); magistrado → `edicao.vigente == true` E `unidade ∈ reconhecimentos(cpf, edicao)`.

## 4. Modelo de dados

- `servidor_habilitado`
  - `id` (PK)
  - `edicao_id` (FK), `unidade_id` (FK)
  - `cpf`, `nome`
  - `origem` (enum: `EGESP`, `MANUAL`)
  - `ativo` (boolean) — remoção lógica para auditoria
  - `criado_por`, `criado_em`, `atualizado_em`
  - **unique** (`edicao_id`, `unidade_id`, `cpf`)
- Consumido por 006: `existe servidor_habilitado ativo para (edicao, unidade, cpf)`.

## 5. Contratos / APIs

Admin (qualquer edição publicada) e Magistrado (edição vigente + suas unidades):
- `POST /api/edicoes/{edId}/unidades/{unId}/servidores/semear` (admin; magistrado no seu escopo, desde 2026-09-16)
  → busca no EGESP e insere/mescla; retorna total semeado.
- `GET /api/edicoes/{edId}/unidades/{unId}/servidores` → lista atual.
- `POST /api/edicoes/{edId}/unidades/{unId}/servidores` `{ cpf, nome }`
  → inclui (origem MANUAL). `409` se CPF já na lista.
- `DELETE /api/edicoes/{edId}/unidades/{unId}/servidores/{cpf}` → remoção lógica.
- `GET /api/magistrado/servidores?edicaoId=` → unidades do magistrado (na vigente)
  com suas listas, para edição pelo próprio.
- Erros: `403` quando magistrado fora do escopo (edição não vigente ou unidade
  não reconhecida por ele); `403` não-admin/não-magistrado.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Lista persistida por edição (snapshot) | Consulta EGESP ao vivo na emissão | Reemissão de edições antigas precisa do estado **daquela** época; lotação atual não serve. |
| Semeadura via EGESP + edição manual | Só EGESP / só manual | Aproveita o RH e permite correções pontuais. |
| Magistrado restrito a vigente + suas unidades | Magistrado edita tudo | Princípio de menor privilégio; admin cobre o resto. |
| Remoção lógica (`ativo`) | Delete físico | Mantém auditoria de quem entrou/saiu da lista. |

## 7. Riscos e mitigação

- **Risco:** ressemear sobrescrever ajustes manuais → **Mitigação:** **mesclar**
  — não remove inclusões `MANUAL` e não reativa quem foi removido manualmente
  (`ativo=false`).
- **Risco:** magistrado alterar lista de unidade alheia → **Mitigação:** guarda de
  escopo por reconhecimentos do CPF autenticado.
- **Risco:** lista grande → **Mitigação:** paginação e semeadura assíncrona se
  necessário.

## 8. Estratégia de testes

- Integração: semeadura popula do EGESP (CA-1); inclusão manual (CA-2); magistrado
  edita na vigente em sua unidade (CA-3); bloqueio em não vigente (CA-4) e em
  unidade alheia (CA-5); duplicidade rejeitada (CA-6).

## 9. Pontos em aberto

- **RESOLVIDO:** ressemear **mescla**, preservando ajustes manuais. A remoção
  manual (`ativo=false`) **não** é reativada por uma nova semeadura.
- [NEEDS CLARIFICATION: contrato do EGESP para "servidores por unidade" (mock
  nesta fase). Adiado.]
