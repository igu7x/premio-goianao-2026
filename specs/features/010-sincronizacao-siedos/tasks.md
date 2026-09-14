# Tarefas: Sincronização com o ConnectTJ (SIEDOS/EGESP)

- **ID:** 010-sincronizacao-siedos
- **Plano relacionado:** ./plan.md
- **Status:** concluído

## Tarefas

- [x] **T-001** — Migração 011: `codigo_siedos` e `comarca` na unidade,
      `matricula` no servidor habilitado, `matricula` e `login_ad` no usuário.
      _(satisfaz: RF-7, RF-8)_
- [x] **T-002** — Ampliar a porta `EgespClient` (hierarquia, detalhes, lotados,
      servidor por matrícula e por login) e ajustar o `MockEgespClient`.
      _(satisfaz: RF-1, RF-2)_
- [x] **T-003** — `ConnectTjProperties` + `TokenConnectTj` (cache, renovação,
      401) + `ConnectTjEgespClient`. _(satisfaz: RF-1, CA-9)_
- [x] **T-004** — Motor de comparação (`SincronizacaoService`) e DTOs das quatro
      categorias. _(satisfaz: RF-3, RF-4)_
- [x] **T-005** — Endpoints de aplicação (cadastrar unidade, atualizar, incluir
      servidor, desvincular). _(satisfaz: RF-5, RF-6)_
- [x] **T-006** — Semeadura passa a resolver e-mail por matrícula.
      _(satisfaz: RF-6, CA-4)_
- [x] **T-007** — Atualização no login (assíncrona) + testes.
      _(satisfaz: RF-8, RF-9, CA-7, CA-8)_
- [x] **T-008** — Tela de sincronização no frontend. _(satisfaz: RF-3, RF-10)_
- [x] **T-009** — Variáveis novas em `specs/deploy/variaveis-de-ambiente.md` e
      DI-25 + diário. _(satisfaz: RNF-1)_

## Definição de pronto (Definition of Done)

- [x] Critérios de aceitação da spec verificados (menos os que dependem da API real: falta o client)
- [x] `mvn -o test` (206) e `npm test` (19) verdes
- [x] Nada de credencial versionado
