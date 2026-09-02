# 2026-09-01 — Reconstrução do projeto a partir das specs

> Diário de implementação. Registra o que foi feito, em que ordem e o que ficou
> pendente, para que o próximo a pegar o projeto (ou o revisor) não precise
> reconstruir o raciocínio a partir do código.

## O que foi feito

O código foi **escrito do zero** tendo como único insumo a pasta `specs/`:
a constituição, os templates e as nove features com `spec.md`, `plan.md` e
`tasks.md`. Nenhuma spec foi reescrita; onde a implementação precisou decidir
algo que as specs não cobriam, a decisão está registrada — em
`specs/memory/decisoes-de-implementacao.md` quando atravessa o projeto, ou na
seção "Implementação" do `tasks.md` da feature quando é local.

Ordem de construção, seguindo as dependências entre as features:

1. **Monorepo e infraestrutura** — `backend/` (Java 21, Spring Boot 3.5, Flyway)
   e `frontend/` (React 18, TypeScript, Vite); cinco migrações portáveis entre
   PostgreSQL e H2.
2. **001** — porta de identidade, papéis acumuláveis, JWT de 8h, RBAC.
3. **002** — ciclo de vida da edição e vigência única garantida no banco.
4. **003** — layouts, validação da arte e o `CertificadoRenderer`, que é o motor
   compartilhado por preview e pelas duas emissões.
5. **004** — unidades vindas do EGESP, reconhecidos e importação em lote.
6. **008** — lista de servidores habilitados (precisa vir antes da 006).
7. **005** e **006** — emissões, com o código de validação estável.
8. **007** — conferência pública, sem login.
9. **009** — inclusão aditiva na edição vigente e a pré-condição de publicação
   dos 8 layouts na 002.
10. **Frontend completo** — nove telas, incluindo o editor visual de layout.
11. **Testes** — 110 no backend, cobrindo todos os critérios de aceitação das
    nove features, mais 8 de componente no frontend (Vitest + Testing Library)
    para as duas telas com regra própria: o modo somente-inclusão da 009 e a
    conferência pública da 007.

## Verificação

- `cd backend && mvn test` → **110 testes, 0 falhas** (hoje 121, após a varredura de correções — ver o diário do mesmo dia).
- `cd frontend && npm test` → **8 testes, 0 falhas**.
- `cd frontend && npm run build` → compila sem erro de tipo.
- Fluxo exercitado ponta a ponta com a aplicação de pé: login mock →
  emissão pelo magistrado → emissão pelo servidor (com a regra do maior selo
  aplicada: unidade com Ouro e Bronze emitiu Ouro) → leitura do PDF gerado →
  conferência pública pelo código impresso.

## Alterações feitas nas specs

- Todas as tarefas dos nove `tasks.md` marcadas como concluídas, com uma seção
  **Implementação** apontando onde cada uma vive no código e o que foi decidido
  no caminho.
- Novo `specs/memory/decisoes-de-implementacao.md` com as decisões que
  atravessam o projeto (DI-1 a DI-8).
- Nenhum `spec.md` ou `plan.md` foi alterado: o contrato permanece como estava.

## Pendências — todas externas

Nenhuma delas exige rearquitetura; estão isoladas atrás de portas ou de
configuração.

| Pendência | Onde entra | O que falta |
|---|---|---|
| SSO corporativo | `auth/IdentityProvider` | implementar `SsoIdentityProvider` (OIDC, Authorization Code) com issuer, client id/secret e a claim do CPF |
| EGESP | `integracao/egesp/EgespClient` | implementar contra a API real (unidades e servidores por unidade) |
| Fonte institucional | `resources/fontes/institucional.ttf` | arquivo e licença do TJGO |
| Domínio de verificação | `GOIANAO_BASE_VERIFICACAO` | definir a hospedagem; é a base que o QR codifica |
| Artes definitivas | tela de layouts | as do setor de comunicação, no padrão A4 paisagem a 300 DPI |

As três primeiras já estão sinalizadas nos próprios `spec.md` como pontos em
aberto por dependência externa; nada mudou quanto a isso.

## Próximos passos sugeridos

Fora do escopo das specs atuais, mas que valem uma conversa antes de ir a
produção:

1. **Segredo do JWT** — `GOIANAO_JWT_SEGREDO` precisa ser definido no ambiente; o
   valor padrão versionado serve só para desenvolvimento.
2. **Limite de taxa distribuído** — o da conferência pública é em memória e vale
   para uma instância só (ver DI-7 de 007 no `tasks.md` da feature).
3. **Armazenamento das artes** — hoje em filesystem, atrás da porta
   `ImageStorage`; com mais de uma instância, apontar para um bucket
   S3-compatível é troca de implementação.
4. **Cadastro de administradores por tela** — hoje a tabela `administrador` é
   alimentada pela carga de demonstração ou diretamente no banco. Se o TJGO
   quiser gerenciar isso pela interface, é uma feature nova (010) e merece a
   sua spec.
