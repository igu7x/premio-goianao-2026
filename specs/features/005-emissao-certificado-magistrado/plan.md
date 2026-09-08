# Plano técnico: Emissão de certificado — Magistrado

- **ID:** 005-emissao-certificado-magistrado
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**. Define também o **motor de geração** (`CertificadoRenderer`)
> reutilizado pela feature 006 e pelo preview da feature 003.

## 1. Abordagem

Com o CPF autenticado (feature 001), buscar os reconhecimentos do magistrado na
edição vigente (feature 004) e apresentar as opções de emissão. Ao emitir,
resolver o layout (feature 003) por (edição, selo, tipo=MAGISTRADO), compor o
certificado com `CertificadoRenderer` e devolver o PDF, registrando a emissão.

## 2. Stack e dependências

- **Backend:** Spring Web; Java 2D (`Graphics2D`) para sobrepor texto na arte;
  PDFBox/OpenPDF para empacotar em PDF. Reuso do `CertificadoRenderer`.
- **Frontend:** React — tela "Meus certificados" listando opções e download.

## 3. Arquitetura

```
[React] --GET opções--> EmissaoMagistradoController --> ReconhecimentoQuery(004)
        --POST emitir-->                              --> LayoutRepository(003)
                                                      --> CertificadoRenderer --> PDF
                                                      --> EmissaoRepository (registro)
```

`CertificadoRenderer.render(layout, nome, unidadeNome, codigoValidacao) -> byte[] (PDF)`:
carrega `layout.imagem`, desenha nome em `area_nome`, unidade em `area_unidade` e
o código (texto e/ou QR) em `area_codigo` usando a **fonte institucional fixa** e
**cor preta**, com **tamanho auto-ajustado** para o texto caber na caixa
(redução até o limite; reticências se ainda exceder), exporta PDF A4 paisagem.

O `nome` passado ao renderer é o do **magistrado cadastrado naquela edição**
(`magistrado_reconhecido.nome`, feature 004) — o token SSO fornece apenas o **CPF**
usado para localizar o cadastro (RF-4/CA-2b, princípio 3a). O mesmo nome é gravado
em `certificado_emitido.nome_emissor` na emissão e na reemissão. Note o contraste
com a feature 006, onde o nome do **servidor** vem do token SSO.

## 4. Modelo de dados

Reusa `edicao` (002), `layout_certificado` (003), `magistrado_reconhecido` /
`reconhecimento` / `unidade_judiciaria` (004). Acrescenta:

- `certificado_emitido`
  - `id` (PK), `edicao_id`, `tipo` (MAGISTRADO/SERVIDOR), `cpf_emissor`,
    `nome_emissor`, `unidade_id`, `selo`, `codigo_validacao` (**unique**),
    `emitido_em`, `reemitido_em`
  - **unique** (`edicao_id`, `tipo`, `cpf_emissor`, `unidade_id`) — garante **um
    certificado lógico** por (edição, tipo, pessoa, unidade); a reemissão
    encontra o registro e **reusa o mesmo `codigo_validacao`** (RF-10).
  - índice por (`cpf_emissor`, `edicao_id`) para histórico.
- `codigo_validacao`: identificador opaco e não sequencial (ex.: ULID/base32
  curto), apto a virar texto e QR. O **QR codifica a URL pública de verificação**
  (`${BASE_VERIFICACAO}/verificar/{codigo}`, feature 007); o **texto** traz o
  código para conferência manual.

## 5. Contratos / APIs (perfil Magistrado; identidade pelo token)

- `GET /api/magistrado/edicoes` → edições publicadas em que o magistrado tem
  reconhecimento (para o seletor; a vigente vem marcada como padrão).
- `GET /api/magistrado/certificados?edicaoId=` → opções da edição informada
  (default: vigente): `[{ unidadeId, unidadeNome, selo, layoutDisponivel }]`.
- `POST /api/magistrado/certificados/emitir` `{ edicaoId?, unidadeId }`
  → `application/pdf`. `edicaoId` opcional (default vigente). Valida: unidade
  pertence ao magistrado naquela edição (RF-6), edição **PUBLICADA** (RF-7),
  layout existe naquela edição (RF-7). Faz **upsert** do `certificado_emitido`
  (gera `codigo_validacao` se novo; reusa se reemissão) e renderiza com o código
  (RF-9, RF-10).
- Erros: `403` unidade não reconhecida; `409`/`422` sem layout ou edição em
  Rascunho.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Identidade pelo token, não por parâmetro | CPF no body | Impede emitir em nome de outro (RNF-1). |
| PDF sob demanda | Pré-gerar e guardar arquivo | Simplifica; layout/dados são determinísticos. Registro de metadados garante auditoria. |
| `CertificadoRenderer` compartilhado | Render duplicado por feature | Paridade com preview (003) e com servidor (006). |

## 7. Riscos e mitigação

- **Risco:** emitir para unidade alheia → **Mitigação:** validar contra
  reconhecimentos do CPF autenticado.
- **Risco:** texto estourar a arte → **Mitigação:** auto-ajuste de tamanho à caixa.
- **Risco:** layout ausente → **Mitigação:** checagem prévia + mensagem clara.

## 8. Estratégia de testes

- Integração: opções refletem reconhecimentos (CA-1); reemissão de edição
  anterior publicada usa layout daquela edição (CA-1b); PDF usa layout correto
  (CA-2); emissão para unidade não reconhecida negada (CA-3); sem layout bloqueia
  (CA-4); edição em Rascunho bloqueia (CA-5); reemissão registra (CA-6).
- Render: snapshot do PDF com nome/unidade de exemplo.

## 9. Pontos em aberto

- **RESOLVIDO:** PDF **sob demanda**; persistem-se apenas metadados +
  `codigo_validacao` (sem armazenar o arquivo).
- **RESOLVIDO:** **fonte institucional fixa**, **cor preta**, **tamanho
  auto-ajustado** para caber o texto na caixa da área.
