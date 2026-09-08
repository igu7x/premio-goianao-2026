# Plano técnico: Emissão de certificado — Servidor

- **ID:** 006-emissao-certificado-servidor
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**. A elegibilidade vem da **lista de servidores habilitados**
> (feature 008); a emissão **não** chama o EGESP. Reutiliza o
> `CertificadoRenderer` da feature 005 e a regra do maior selo (004).

## 1. Abordagem

Com o CPF autenticado (001), localizar as unidades reconhecidas (em edições
publicadas) cuja **lista de servidores habilitados** (feature 008) contém o CPF.
Para a edição-alvo (padrão: vigente; ou uma anterior publicada selecionada),
calcular o **maior selo** da unidade pelo valor ordinal do enum (004), resolver o
layout (edição × maiorSelo × tipo=SERVIDOR) e gerar o PDF com
`CertificadoRenderer`, registrando a emissão. Nenhuma chamada ao EGESP ocorre na
emissão — isso garante estabilidade da reemissão de edições antigas.

## 2. Stack e dependências

- **Backend:** Spring Web; consulta a `servidor_habilitado` (008) e a
  `reconhecimento`/`unidade_judiciaria` (004). Reuso do `CertificadoRenderer`
  (005), do `LayoutRepository` (003) e do `MaiorSeloResolver` via `Selo` ordinal.
- **Frontend:** React — tela "Meus certificados" do servidor (seletor de edição +
  uma opção por unidade habilitada + download).

## 3. Arquitetura

```
[React] --opções--> EmissaoServidorController --> ServidorHabilitadoRepository(008)  [CPF ∈ lista?]
        --emitir-->                            --> UnidadesReconhecidasQuery(004) [maiorSelo da unidade]
                                               --> LayoutRepository(003)
                                               --> CertificadoRenderer(005) --> PDF
                                               --> CertificadoEmitidoRepository (registro + código)
```

- `MaiorSeloResolver`: dado o conjunto de selos da unidade, retorna `max` ordinal.
- Identidade (nome impresso) vem do **token SSO**; fallback (só na ausência do
  nome no token): `nome` salvo na lista (008). Diferente da feature 005, em que o
  nome do magistrado vem do **cadastro da edição** — ver RF-1/CA-3b e o
  princípio 3a da constituição.

## 4. Modelo de dados

Reusa `edicao`, `layout_certificado`, `reconhecimento`/`unidade_judiciaria`,
`servidor_habilitado` (008) e `certificado_emitido` (com `tipo = SERVIDOR`,
incluindo `codigo_validacao` único e estável na reemissão — mesma modelagem da
feature 005). Sem novas tabelas.

## 5. Contratos / APIs (perfil Servidor; identidade pelo token)

- `GET /api/servidor/edicoes` → edições publicadas em que o CPF consta em alguma
  lista habilitada (seletor; vigente como padrão).
- `GET /api/servidor/certificados?edicaoId=` → opções emitíveis na edição
  informada (default vigente): para cada unidade em que o CPF está habilitado,
    `{ unidadeId, unidadeNome, maiorSelo, layoutDisponivel }`.
- `POST /api/servidor/certificados/emitir` `{ edicaoId?, unidadeId }`
  → `application/pdf`. Valida: CPF presente na **lista habilitada** da unidade na
    edição-alvo (RF-2/7), edição **PUBLICADA** (RF-8), layout existe naquela
    edição (RF-8). Aplica maior selo daquela edição (RF-4). Faz **upsert** do
    `certificado_emitido` (gera/reusa `codigo_validacao`) e renderiza (RF-9).
- Erros: `403/422` CPF não habilitado na unidade/edição; `409` edição em Rascunho
  / sem layout.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Elegibilidade pela lista persistida (008) | Consultar EGESP ao vivo na emissão | Reemissão de edições antigas exige o estado daquela época; lotação atual não serve (decisão do produto). |
| Maior selo via enum ordinal | Tabela de prioridade | Enum ordenado (004) é simples e confiável. |
| Identidade só do token/lista | Servidor informa unidade | Evita emissão indevida (RNF-1). |
| Renderer compartilhado | Render próprio | Paridade com magistrado (005) e preview (003). |

## 7. Riscos e mitigação

- **Risco:** CPF habilitado em várias unidades → **Mitigação:** uma opção por
  unidade, com o maior selo de cada (CA-4).
- **Risco:** lista desatualizada (servidor legítimo de fora) → **Mitigação:**
  edição da lista por admin/magistrado (feature 008).
- **Risco:** texto longo estourar a arte → **Mitigação:** auto-ajuste de tamanho à
  caixa no renderer (005).

## 8. Estratégia de testes

- Unit: `MaiorSeloResolver` ({Bronze,Ouro} → Ouro) → CA-1.
- Integração: CPF não habilitado → sem opções/negado (CA-2); nome/unidade/código
  no PDF (CA-3); múltiplas unidades habilitadas → uma opção por unidade (CA-4);
  sem layout (CA-5); edição em Rascunho bloqueia (CA-6); reemissão em edição
  anterior publicada usa a lista/layout daquela edição (CA-7).

## 9. Pontos em aberto

- **RESOLVIDO:** elegibilidade pela **lista de servidores habilitados** (008); sem
  EGESP na emissão.
- **RESOLVIDO:** múltiplas unidades → **uma opção por unidade habilitada**.
