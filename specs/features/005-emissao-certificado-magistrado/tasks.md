# Tarefas: Emissão de certificado — Magistrado

- **ID:** 005-emissao-certificado-magistrado
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Reutiliza o `CertificadoRenderer` (003). Define a tabela `certificado_emitido`
> e o `codigo_validacao`, também usados pela feature 006.

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.

## Tarefas

- [x] **T-001** — Migração `certificado_emitido` (edicao, tipo, cpf_emissor,
  nome_emissor, unidade, selo, `codigo_validacao` **unique**, emitido_em,
  reemitido_em) + **unique** (edicao, tipo, cpf, unidade) _(satisfaz: RF-9, RF-10)_
- [x] **T-002** [P] — Gerador de `codigo_validacao` **opaco/não sequencial** e
  montagem da **URL de verificação** (`BASE_VERIFICACAO`) para o QR
  _(satisfaz: RF-10)_
- [x] **T-003** — `GET /api/magistrado/edicoes` (publicadas com reconhecimento do
  CPF) _(satisfaz: RF-1, RF-3)_
- [x] **T-004** — `GET /api/magistrado/certificados?edicaoId=` (uma opção por
  unidade reconhecida; default vigente; `layoutDisponivel`)
  _(satisfaz: RF-1, RF-2)_ _(depende de: T-003)_
- [x] **T-005** — `POST /api/magistrado/certificados/emitir` `{edicaoId?, unidadeId}`:
  valida unidade do CPF (RF-6), edição **PUBLICADA** e layout (RF-7); **upsert** do
  certificado (gera/reusa código); renderiza PDF _(satisfaz: RF-3..RF-10)_
  _(depende de: T-001, T-002, T-004, 003/T-004)_
- [x] **T-006** — Autorização por **CPF autenticado** (não por parâmetro)
  _(satisfaz: RNF-1)_ _(depende de: T-005)_
- [x] **T-007** [P] — Frontend: "Meus certificados" com **seletor de edição** e
  download do PDF _(satisfaz: RF-1, RF-2, RF-5)_ _(depende de: T-004, T-005)_
- [x] **T-008** — Testes: opções refletem reconhecimentos (CA-1); reemissão de
  edição anterior usa layout daquela edição (CA-1b); PDF com layout/nome/unidade/
  código corretos (CA-2); **nome impresso vem do cadastro da edição, não do SSO,
  quando as grafias divergem (CA-2b)**; unidade não reconhecida negada (CA-3); sem
  layout (CA-4); edição em Rascunho bloqueia (CA-5); reemissão estável registra
  (CA-6) _(satisfaz: RF-1..RF-10)_ _(depende de: T-005, T-006)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-6 verificados (inclusive CA-1b e CA-2b).
- [x] Nome impresso do magistrado = **cadastro da edição** (004); o SSO só provê o CPF.
- [x] PDF **sob demanda**; persistem apenas metadados + `codigo_validacao`.
- [x] Código **estável** na reemissão (mesmo certificado lógico).
- [x] Testes passando.

## Implementação (2026-09-01)

**Backend** — `br.jus.tjgo.goianao.certificado`

| O que | Onde |
|---|---|
| Registro do certificado lógico | `CertificadoEmitido.java`, migração `V5` |
| Código opaco e não sequencial | `GeradorCodigoValidacao.java` |
| Núcleo comum das emissões | `EmissaoService.java` |
| Regras do magistrado | `EmissaoMagistradoService.java` |
| Endpoints | `EmissaoMagistradoController.java` |

**Frontend** — `paginas/MeusCertificados.tsx`, com seletor de edição e download.

**Testes** — `EmissaoMagistradoIT` cobre CA-1, CA-1b, CA-2, **CA-2b** (nome do
cadastro prevalece sobre o do SSO), CA-3 a CA-6, com o PDF lido de volta para
conferir o texto impresso.

### Decisões tomadas na implementação

- **Formato do código:** três grupos de quatro caracteres (`XXXX-XXXX-XXXX`) no
  alfabeto de Crockford, que não tem I, L, O nem U. Ditar e digitar o código ao
  telefone ou no balcão é caso de uso real; ambiguidade de caractere seria
  problema. Sorteado de `SecureRandom`, com conferência de colisão.
- **`X-Codigo-Validacao` no cabeçalho da resposta** de emissão: o corpo é o PDF,
  e a tela precisa mostrar o código logo após o download.
- **`totalEmissoes`** acrescentado ao registro. Não estava no plano, mas o
  histórico pedido pelo RF-9 fica pobre sem saber quantas vezes o mesmo
  certificado foi gerado.
- Nome e selo são reavaliados na reemissão: o nome do servidor vem do SSO a cada
  emissão e o maior selo é recalculado na edição vigente (feature 009).
