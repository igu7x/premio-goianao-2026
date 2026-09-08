# Plano técnico: Configuração de layouts de certificado

- **ID:** 003-configuracao-layouts-certificado
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**.

## 1. Abordagem

Modelar `LayoutCertificado` como a tupla (edição, selo, tipo) + imagem-base +
definição das áreas de texto (nome e unidade) como coordenadas/estilo. A imagem
é armazenada e referenciada; as áreas ficam em colunas/JSON. A pré-visualização
reaproveita o **mesmo** motor de composição usado na emissão (definido nas
features 005/006), garantindo paridade pixel-a-pixel entre preview e emissão.

## 2. Stack e dependências

- **Backend:** Spring Web (upload multipart), Spring Data JPA. Composição de
  imagem com Java 2D (`BufferedImage`/`Graphics2D`); geração de PDF (ex.:
  PDFBox/OpenPDF) compartilhada com a emissão.
- **Armazenamento de imagem:** filesystem/objeto (ex.: diretório configurável ou
  S3-compat); referência por caminho/URL no banco.
- **Padrão da arte:** **A4 paisagem, 300 DPI** (~3508×2480 px), validado no
  upload; o PDF de saída é A4 paisagem e a composição usa essa base de px.
- **Frontend:** React — formulário de upload + editor simples de posicionamento
  (arrastar marcadores de nome/unidade sobre a imagem) + botão de preview.

## 3. Arquitetura

```
[React admin] --multipart--> LayoutController --> LayoutService --> LayoutRepository
                                   |                      '--> ImageStorage (arte)
                                   '--preview--> CertificadoRenderer (compartilhado 005/006)
```

`CertificadoRenderer.render(layout, nome, unidade, codigoValidacao) -> bytes (PDF)`
é a peça reutilizada por preview e emissão (no preview, o código é um valor de
exemplo).

## 4. Modelo de dados

- `layout_certificado`
  - `id` (PK)
  - `edicao_id` (FK → edicao)
  - `selo` (enum: BRONZE, PRATA, OURO, DIAMANTE)
  - `tipo` (enum: MAGISTRADO, SERVIDOR)
  - `imagem_ref` (caminho/URL da arte)
  - `area_nome` (JSON: `{x, y, largura, altura, alinhamento}`) — fonte
    institucional fixa, cor preta, tamanho **auto-ajustado** para caber
  - `area_unidade` (JSON: idem)
  - `area_codigo` (JSON: idem + `qr: { x, y, tamanho } | null`) — posição do
    código de validação (texto) e, opcionalmente, do QR. O QR codifica a **URL
    pública de verificação** (feature 007); o texto traz o código.
  - `criado_em`, `atualizado_em`
  - **unique** (`edicao_id`, `selo`, `tipo`)

## 5. Contratos / APIs (perfil Administrador)

- `POST /api/edicoes/{edicaoId}/layouts` (multipart: imagem + metadados de áreas)
  → `201 Layout`. `409` se já existir a combinação (sem flag de substituição).
- `PUT /api/edicoes/{edicaoId}/layouts/{id}` → atualiza imagem/áreas.
- `GET /api/edicoes/{edicaoId}/layouts` → lista + **pendências** (combinações sem
  layout) para RF-7.
- `GET /api/edicoes/{edicaoId}/layouts/{id}` → detalhe.
- `POST /api/edicoes/{edicaoId}/layouts/{id}/preview`
  `{ nomeExemplo, unidadeExemplo, codigoExemplo? }` → PDF de pré-visualização.
- Bloqueio de alteração quando a edição **não** está publicada/editável conforme
  regra da feature 002 (não altera layouts de certificados já emitidos).

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Áreas como coordenadas/estilo (JSON) | Template HTML | Arte é imagem fixa; sobrepor texto por coordenadas é direto e fiel ao requisito. |
| Renderer compartilhado preview/emissão | Dois caminhos | Garante que o preview seja idêntico ao certificado real. |
| Imagem em storage externo | BLOB no banco | Mantém o banco leve; artes podem ser grandes. |

## 7. Riscos e mitigação

- **Risco:** divergência preview × emissão → **Mitigação:** mesmo `CertificadoRenderer`.
- **Risco:** texto longo (nome/unidade) estourar a área → **Mitigação:** caixa
  com largura/altura + auto-ajuste (redução de tamanho até o limite).
- **Risco:** alterar layout de edição já publicada muda reemissões →
  **Mitigação:** travar layouts ao publicar (editável só em Rascunho).

## 8. Estratégia de testes

- Integração: cadastro cria combinação única (CA-1, CA-2); preview escreve nas
  posições definidas (CA-3); endpoint de pendências (CA-4); 403 para não-admin (CA-5).
- Render: teste de composição com nome/unidade longos (quebra/ajuste).

## 9. Pontos em aberto

- **RESOLVIDO:** áreas como **caixa** `{ x, y, largura, altura,
  alinhamento(left|center|right) }` em **px**, origem no **canto superior
  esquerdo** da imagem-base. Fonte institucional fixa, cor preta, tamanho
  **auto-ajustado** ao tamanho da caixa.
- **RESOLVIDO:** **fonte institucional fixa** (única) e **cor preta** para todo
  texto; o admin não escolhe fonte/cor/tamanho.
- **RESOLVIDO:** uma **fonte institucional fixa** embarcada, cor preta; tamanho
  auto-ajustado. (Arquivo/licença da fonte a fornecer pelo TJGO.)
