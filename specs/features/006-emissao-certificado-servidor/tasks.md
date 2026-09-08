# Tarefas: Emissão de certificado — Servidor

- **ID:** 006-emissao-certificado-servidor
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Elegibilidade pela **lista de servidores habilitados** (008); **sem EGESP** na
> emissão. Reutiliza `CertificadoRenderer` (003) e `certificado_emitido` (005).

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.

## Tarefas

- [x] **T-001** — `MaiorSeloResolver` (máximo pelo ordinal de `Selo`)
  _(satisfaz: RF-4)_
- [x] **T-002** — `GET /api/servidor/edicoes` (publicadas em que o CPF consta em
  alguma lista habilitada) _(satisfaz: RF-2, RF-3)_
- [x] **T-003** — `GET /api/servidor/certificados?edicaoId=` (uma opção por unidade
  habilitada; `maiorSelo`; `layoutDisponivel`) _(satisfaz: RF-2, RF-4)_
  _(depende de: T-001, T-002)_
- [x] **T-004** — `POST /api/servidor/certificados/emitir` `{edicaoId?, unidadeId}`:
  valida **CPF na lista habilitada** da unidade/edição (RF-7), edição **PUBLICADA**
  e layout (RF-8), aplica maior selo (RF-4); **upsert** do certificado
  (gera/reusa código) e renderiza PDF _(satisfaz: RF-1..RF-9)_
  _(depende de: T-003, 005/T-001, 005/T-002, 003/T-004, 008/T-002)_
- [x] **T-005** — Autorização: somente unidades em que o **CPF está habilitado**
  (nada por parâmetro) _(satisfaz: RNF-1)_ _(depende de: T-004)_
- [x] **T-006** [P] — Frontend: "Meus certificados" do servidor com **seletor de
  edição**, uma opção por unidade e download _(satisfaz: RF-2, RF-6)_
  _(depende de: T-003, T-004)_
- [x] **T-007** — Testes: maior selo {Bronze,Ouro}→Ouro (CA-1); CPF não habilitado
  negado (CA-2); PDF com nome/unidade/código (CA-3); **nome impresso vem do SSO
  quando difere do nome semeado na lista (CA-3b)**; múltiplas unidades → uma
  opção por unidade (CA-4); sem layout (CA-5); Rascunho bloqueia (CA-6); reemissão
  de edição anterior usa a lista/layout daquela edição (CA-7)
  _(satisfaz: RF-1..RF-9)_ _(depende de: T-004, T-005)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-7 verificados (inclusive CA-3b).
- [x] Nome impresso do servidor = **SSO** (fallback: nome salvo na lista da 008).
- [x] Emissão **não** chama o EGESP (lê a lista persistida).
- [x] PDF sob demanda; código estável na reemissão.
- [x] Testes passando.

## Implementação (2026-09-01)

**Backend** — `certificado/EmissaoServidorService.java` e
`certificado/EmissaoServidorController.java`; regra do maior selo em
`comum/Selo.maior(...)` combinada com
`MagistradoService.maiorSeloDaUnidade(...)`.

**Frontend** — mesma tela `paginas/MeusCertificados.tsx`: quem acumula magistrado
e servidor vê os dois painéis, cada um com sua regra.

**Testes** — `EmissaoServidorIT` cobre CA-1 a CA-7, incluindo **CA-3b** (nome
impresso vem do SSO, não do nome semeado) e o caso de o servidor ser removido da
lista depois de habilitado. `SeloTest` cobre a regra do maior selo isoladamente.

### Decisões tomadas na implementação

- **O maior selo é calculado na consulta e na emissão, nunca gravado.** É o que
  faz uma inclusão na edição vigente (feature 009) valer imediatamente, conforme
  o ponto resolvido da 009.
- **Unidade habilitada mas sem reconhecimento é ignorada** na listagem de opções,
  em vez de gerar erro: sem selo não há certificado a emitir, e mostrar a opção
  só levaria o servidor a um erro.
- Confirmado por teste que a emissão **não** toca o `EgespClient`: a
  elegibilidade sai inteira da tabela `servidor_habilitado`.
