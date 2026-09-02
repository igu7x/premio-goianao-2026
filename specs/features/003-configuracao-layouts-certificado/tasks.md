# Tarefas: Configuração de layouts de certificado

- **ID:** 003-configuracao-layouts-certificado
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Inclui a construção do **`CertificadoRenderer`** (motor de composição PDF),
> reutilizado pelo preview aqui e pela emissão (features 005/006).

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída · `[P]` paralelizável.

## Tarefas

- [x] **T-001** — Enums `Selo` {BRONZE<PRATA<OURO<DIAMANTE} (ordinal) e `Tipo`
  {MAGISTRADO, SERVIDOR} _(satisfaz: RF-1)_
- [x] **T-002** — Migração `layout_certificado` (edicao_id, selo, tipo,
  imagem_ref, `area_nome`/`area_unidade`/`area_codigo` JSON `{x,y,largura,altura,
  alinhamento}` + `qr`) + **unique** (edicao, selo, tipo)
  _(satisfaz: RF-1, RF-3, RF-3b, RF-4)_ _(depende de: T-001)_
- [x] **T-003** [P] — `ImageStorage` (filesystem/objeto) + **validação de upload**:
  **A4 paisagem, 300 DPI** (~3508×2480), formato e tamanho _(satisfaz: RF-2, RNF-3, RNF-4)_
- [x] **T-004** — **`CertificadoRenderer`**: compõe nome/unidade/código sobre a
  arte com **fonte institucional fixa, cor preta, tamanho auto-ajustado** à caixa;
  desenha **QR (URL de verificação)**; exporta **PDF A4 paisagem**
  _(satisfaz: RF-3, RF-3b, RNF-2)_ _(depende de: T-002, T-003)_
- [x] **T-005** — `POST /api/edicoes/{edicaoId}/layouts` (multipart; `409` se a
  combinação já existe) _(satisfaz: RF-1, RF-2, RF-3, RF-3b, RF-4)_ _(depende de: T-002, T-003)_
- [x] **T-006** — `PUT .../layouts/{id}` (somente com edição em **Rascunho**)
  _(satisfaz: RF-5)_ _(depende de: T-005)_
- [x] **T-007** [P] — `GET .../layouts` com **pendências** (combinações selo×tipo
  sem layout) _(satisfaz: RF-7)_ _(depende de: T-002)_
- [x] **T-008** — `POST .../layouts/{id}/preview` `{nome, unidade, codigo exemplo}`
  → PDF usando o `CertificadoRenderer` _(satisfaz: RF-6, RNF-2)_ _(depende de: T-004, T-005)_
- [x] **T-009** — **Travar layouts ao publicar** a edição (bloquear alteração)
  _(satisfaz: RF-8)_ _(depende de: T-006)_
- [x] **T-010** — RBAC: somente Administrador _(satisfaz: RNF-1)_ _(depende de: T-005..T-008)_
- [x] **T-011** — Frontend: upload da arte + **editor visual** (arrastar/
  redimensionar caixas de nome/unidade/código) + botão de **preview**
  _(satisfaz: RF-3, RF-3b, RF-6)_ _(depende de: T-005, T-008)_
- [x] **T-012** — Testes: combinação única (CA-1, CA-2); preview posiciona
  nome/unidade/código (CA-3); pendências (CA-4); `403` não-admin (CA-5); render
  com texto longo auto-ajusta _(satisfaz: RF-3..RF-7)_ _(depende de: T-008, T-010)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-5 verificados.
- [x] Preview e emissão usam o **mesmo** `CertificadoRenderer` (paridade).
- [x] Upload rejeita arte fora do padrão A4/300 DPI.
- [x] Testes passando.

## Implementação (2026-09-01)

**Backend** — `br.jus.tjgo.goianao.layout`

| O que | Onde |
|---|---|
| Áreas como objetos de valor | `AreaTexto`, `AreaQr`, `AreaCodigo` |
| Persistência das áreas em JSON | `JsonAreas.java` (conversores JPA) |
| Entidade e unicidade edição×selo×tipo | `LayoutCertificado.java`, migração `V3` |
| Validação da arte (A4 paisagem, 300 DPI) | `render/ValidadorDeArte.java` |
| Armazenamento das artes | `render/ImageStorage.java`, `render/FilesystemImageStorage.java` |
| **Motor de composição** | `render/CertificadoRenderer.java` |
| Fonte institucional com fallback | `render/FonteInstitucional.java` |
| QR da URL de verificação | `render/GeradorQrCode.java` |
| Pendências (pré-condição da 002) | `LayoutPreRequisitos.java` |

**Frontend** — `paginas/abas/AbaLayouts.tsx` (grade das 8 combinações) e
`paginas/abas/EditorDeLayout.tsx` (editor visual de arrastar/redimensionar).

**Testes** — `LayoutIT` (CA-1 a CA-5, travamento ao publicar, área fora da arte),
`ValidadorDeArteTest` e `CertificadoRendererTest` (inclusive nome longo).

### Decisões tomadas na implementação

- **Fonte institucional ainda não fornecida.** `FonteInstitucional` procura
  `classpath:fontes/institucional.ttf`; não achando, usa uma fonte padrão do PDF
  (que cobre a acentuação do português). A troca é soltar o arquivo no lugar,
  sem tocar em código. A tela de layouts avisa quando o TTF está ausente.
- **Dimensões da arte gravadas no layout** (`imagem_largura`, `imagem_altura`).
  Não estavam no plano, mas são necessárias: as áreas são coordenadas em pixels
  da imagem-base, e o editor precisa da escala para converter o arrasto.
- **`GET .../layouts/{id}/imagem`** serve a arte ao editor. Como é autenticado e
  `<img>` não envia cabeçalho `Authorization`, o frontend busca o binário e usa
  uma URL de objeto.
- **Substituição explícita** implementada como campo `substituir` na parte JSON
  do multipart (CA-2).

### Ajuste de 2026-09-01 — editor realmente travado após publicar (RF-8)

O backend já recusava alterações em edição publicada, mas o editor visual
continuava oferecendo "Salvar layout", permitindo arrastar as caixas e digitar
coordenadas — o administrador ajustava tudo e só descobria no fim, com um 409.
O editor passou a entrar em modo somente-leitura de verdade: sem salvar, sem
arrasto, sem redimensionar, campos em leitura e um aviso explicando o motivo. A
pré-visualização continua disponível, por ser leitura.
