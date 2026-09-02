# Plano técnico: Validação pública de certificado

- **ID:** 007-validacao-publica-certificado
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**. Consome `certificado_emitido` (features 005/006).

## 1. Abordagem

Endpoint e página **públicos** (fora do filtro de autenticação) que buscam um
`certificado_emitido` por `codigo_validacao` e retornam um DTO de verificação
com os dados não sensíveis. O QR gerado na emissão (005/006) aponta para a URL
desta página com o código na rota/query. Rate limiting e códigos opacos
mitigam enumeração.

## 2. Stack e dependências

- **Backend:** Spring Web; rota pública liberada no Spring Security
  (`permitAll`); rate limiting (ex.: bucket por IP). Spring Data JPA.
- **Frontend:** React — página pública de verificação (rota sem guarda de auth).
- **Banco:** PostgreSQL (somente leitura de `certificado_emitido`).
- **Config:** base da URL pública de verificação (usada também pela emissão para
  compor o QR).

## 3. Arquitetura

```
[QR/Link] --> [Página pública /verificar/{codigo}] --> GET /api/public/certificados/{codigo}
                                                          --> CertificadoEmitidoRepository
                                                          --> VerificacaoDTO (dados não sensíveis)
```

A base da URL pública é injetada na emissão para montar o conteúdo do QR
(`${BASE_VERIFICACAO}/verificar/{codigo}`).

## 4. Modelo de dados

Nenhuma tabela nova. Leitura de `certificado_emitido`
(`codigo_validacao`, `nome_emissor`, `unidade_id`→nome, `edicao_id`→ano, `selo`,
`tipo`, `emitido_em`). CPF **não** é retornado.

## 5. Contratos / APIs (público, sem autenticação)

- `GET /api/public/certificados/{codigo}`
  → `200 { valido: true, nome, unidade, edicaoAno, selo, tipo, emitidoEm }`
  → `404 { valido: false }` quando o código não existe.
- Página SPA: `GET /verificar/{codigo}` (resolve via o endpoint acima).
- Sem CPF no payload.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Rota pública dedicada (`/api/public/**`) | Reusar rotas autenticadas | Verificação é para terceiros sem login; isolar a superfície pública. |
| QR codifica a URL de verificação | QR só com o código | Leitura do QR já abre a conferência; melhor UX (decisão do produto). |
| Código opaco/não sequencial | Sequencial | Evita enumeração/adivinhação de certificados (RNF-3). |
| Sem CPF no retorno | Exibir CPF | Minimização de dados pessoais (RNF-2). |

## 7. Riscos e mitigação

- **Risco:** enumeração de códigos para varrer certificados → **Mitigação:**
  código opaco + rate limiting + resposta uniforme para inexistentes.
- **Risco:** exposição de dado pessoal → **Mitigação:** DTO mínimo, sem CPF (ou
  mascarado).
- **Risco:** QR com URL errada (base mal configurada) → **Mitigação:** base única
  por ambiente, validada; teste de ponta a ponta emissão→leitura.

## 8. Estratégia de testes

- Integração: código válido retorna dados sem CPF (CA-1, CA-5); inexistente → 404
  uniforme (CA-2); reemitido continua válido pelo mesmo código (CA-4).
- E2E: QR aponta para a URL pública e resolve o código (CA-3).

## 9. Pontos em aberto

- **RESOLVIDO:** a verificação pública **não exibe CPF**.
- [NEEDS CLARIFICATION: domínio/base da URL pública de verificação (config de
  ambiente; necessário para compor o QR). Adiado até a definição de hospedagem.]
