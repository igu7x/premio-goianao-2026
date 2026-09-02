# Plano técnico: Autenticação SSO e perfis de acesso

- **ID:** 001-autenticacao-sso-perfis
- **Spec relacionada:** ./spec.md
- **Status:** aprovada

> O **como**. Pressupõe que a `spec.md` já foi aprovada.

## 1. Abordagem

Modelar a autenticação como uma **porta** (`IdentityProvider`) cuja
implementação atual é um mock que devolve CPF/nome de um conjunto de usuários de
teste. Sobre a identidade autenticada, um `PapeisResolver` calcula o **conjunto
de papéis** acumulados: inclui `ADMINISTRADOR` se o CPF está na lista de admins;
inclui `MAGISTRADO` se consta como reconhecido (feature 004); e `SERVIDOR` como
padrão quando nenhum dos anteriores se aplica. A sessão é um token (JWT) emitido
pelo backend após o "login" mock, carregando CPF, nome e a **lista de papéis**.
O frontend monta os menus pela união dos papéis; RBAC via Spring Security por
papel (`hasAnyRole`).

## 2. Stack e dependências

- **Backend:** Java 21, Spring Boot, Spring Security (filtro de autorização por
  papel), Spring Web. JWT (jjwt/Nimbus) para a sessão.
- **Frontend:** React, com guarda de rotas por perfil e contexto de sessão.
- **Config:** lista de CPFs administradores em propriedade/tabela de config.
- Alinhado à constituição: integração isolada atrás de interface (princípio 7),
  autorização no backend (RNF-2).

## 3. Arquitetura

```
[React] --login mock--> [AuthController] --> IdentityProvider (mock)
                                   |--> PapeisResolver --> {Admin cfg, Magistrados(004)}
                                   '--> emite JWT {cpf, nome, papeis[]}
[React] --Bearer JWT--> [SecurityFilter] --> @PreAuthorize hasAnyRole(...)
```

- `IdentityProvider` (porta): `autenticar(...) -> IdentidadeAutenticada{cpf,nome}`.
  - `MockIdentityProvider` (impl atual) seleciona um usuário de teste.
  - `SsoIdentityProvider` (futuro) implementa **OIDC/OAuth2** (fluxo Authorization
    Code; CPF/nome obtidos de claims do ID token/userinfo).
- `PapeisResolver.resolver(cpf) -> Set<Papel>` (pode conter mais de um papel).

## 4. Modelo de dados

- `administrador` (cpf PK, nome, criado_em) — cadastro de admins.
- `Papel` enum: `ADMINISTRADOR`, `MAGISTRADO`, `SERVIDOR`. A identidade carrega um
  **conjunto** de papéis, não um único.
- Identidade não é necessariamente persistida (vem do SSO); apenas
  administradores são cadastrados. Magistrados vêm da feature 004.

## 5. Contratos / APIs

- `POST /api/auth/login` (mock) → `{ token }`. Body mock: `{ cpfMock }` ou
  seleção de usuário de teste. Resposta inclui `{ cpf, nome, papeis: [...] }`.
  Token com **expiração de 8h, sem refresh** — ao expirar, novo login (`401` →
  redireciona para login).
- `GET /api/auth/me` → `{ cpf, nome, papeis: [...] }` (requer token).
- `POST /api/auth/logout` → invalida sessão no cliente.
- Erros: `401` sem/!token, `403` papel insuficiente.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---------|--------------|------------------|
| Sessão via JWT | Sessão server-side | JWT desacopla front/back e facilita stateless; papéis embutidos evitam reconsulta. |
| Papéis como conjunto | Papel único com precedência | Um CPF pode ser admin e magistrado; menus são a união dos papéis (sem "atuar como"). |
| Mock atrás de porta | Mock espalhado no código | Porta única permite trocar por SSO real sem tocar no domínio (princípio 7). |
| Admin por cadastro | Por claim do SSO | Claims do SSO real ainda indefinidos; cadastro local é determinístico agora. |

## 7. Riscos e mitigação

- **Risco:** mock divergir do contrato do SSO real → **Mitigação:** interface
  mínima (CPF+nome), revisada quando os parâmetros reais chegarem.
- **Risco:** papel incorreto liberar ações indevidas → **Mitigação:** testes de
  autorização por papel em cada endpoint sensível.

## 8. Estratégia de testes

- Unit: `PapeisResolver` cobre admin, magistrado, servidor e **acúmulo**
  (admin+magistrado) → CA-1..4.
- Integração: endpoints protegidos retornam 403 sem o papel exigido → CA-5.
- Integração: `/auth/me` reflete o conjunto de papéis → CA-6.

## 9. Pontos em aberto

- **RESOLVIDO:** papéis acumulam; um CPF admin+magistrado vê os menus de ambos
  (união das capacidades), sem recurso de "atuar como".
- **RESOLVIDO:** token expira em **8h, sem refresh**; expiração leva a novo login.
