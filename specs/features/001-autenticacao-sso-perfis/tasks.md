# Tarefas: Autenticação SSO e perfis de acesso

- **ID:** 001-autenticacao-sso-perfis
- **Plano relacionado:** ./plan.md
- **Status:** concluído

> Quebra do plano em passos pequenos, ordenados e verificáveis.
> Esta feature também faz o **bootstrap do monorepo** (base para as demais).

## Convenções

- `[ ]` pendente · `[~]` em andamento · `[x]` concluída
- `[P]` tarefas paralelizáveis (sem dependência mútua).
- Cada tarefa referencia o requisito da spec que satisfaz (ex.: `RF-1`).

## Tarefas

### Bootstrap (base do projeto)
- [x] **T-001** — Criar o **monorepo**: `backend/` (Spring Boot, Java 21 LTS) e
  `frontend/` (React), com build unificado e README de execução. _(infra)_
- [x] **T-002** [P] — Configurar **PostgreSQL** + migrações (Flyway) e perfis de
  ambiente (dev/test). _(infra)_
- [x] **T-003** [P] — Configurar **Spring Security** base e estrutura de pacotes
  (domínio/aplicação/infra) e CORS para o frontend. _(infra)_

### Identidade e papéis
- [x] **T-004** — Migração e entidade `administrador` (cpf, nome) _(satisfaz: RF-4)_
- [x] **T-005** — Enum `Papel` {ADMINISTRADOR, MAGISTRADO, SERVIDOR} _(satisfaz: RF-3)_
- [x] **T-006** — Porta `IdentityProvider` + `MockIdentityProvider` (CPF/nome de
  teste) _(satisfaz: RF-1, RF-2)_ _(depende de: T-001)_
- [x] **T-007** — `PapeisResolver.resolver(cpf) -> Set<Papel>`: admin via cadastro;
  magistrado via lookup (stub até a feature 004); servidor como padrão
  _(satisfaz: RF-3, RF-4, RF-5, RF-6)_ _(depende de: T-004, T-005)_
- [x] **T-008** — Emissão de **JWT (8h, sem refresh)** com {cpf, nome, papeis} e
  `AuthController` `POST /api/auth/login` (mock) _(satisfaz: RF-1, RF-2, RF-7)_
  _(depende de: T-006, T-007)_
- [x] **T-009** — `GET /api/auth/me` e `POST /api/auth/logout`
  _(satisfaz: RF-7, RF-9)_ _(depende de: T-008)_
- [x] **T-010** — Filtro de autorização **RBAC** (`@PreAuthorize hasAnyRole`) e
  respostas `401/403` _(satisfaz: RF-8)_ _(depende de: T-008)_

### Frontend
- [x] **T-011** [P] — Tela de **login mock** (seleção de usuário de teste),
  contexto de sessão e armazenamento do token _(satisfaz: RF-1, RF-2, RF-7)_
  _(depende de: T-008)_
- [x] **T-012** — **Guarda de rotas e montagem de menus por papel** (união dos
  papéis) _(satisfaz: RF-3, RF-8)_ _(depende de: T-009, T-011)_

### Testes
- [x] **T-013** — Unit `PapeisResolver`: admin, magistrado, servidor e **acúmulo**
  (admin+magistrado) _(satisfaz: RF-3..6; CA-1..4)_ _(depende de: T-007)_
- [x] **T-014** [P] — Integração: `403` sem papel exigido (CA-5) e `/auth/me`
  reflete papéis (CA-6) _(satisfaz: RF-7, RF-8)_ _(depende de: T-009, T-010)_

## Definição de pronto (Definition of Done)

- [x] CA-1 a CA-6 da spec verificados.
- [x] Testes correspondentes passando.
- [x] Monorepo builda backend + frontend; login mock funcional ponta a ponta.
- [x] CPF não aparece em logs/URLs.

## Implementação (2026-09-01)

**Backend** — `br.jus.tjgo.goianao.seguranca` e `.auth`

| O que | Onde |
|---|---|
| Enum `Papel` e `UsuarioAutenticado` (conjunto de papéis) | `seguranca/Papel.java`, `seguranca/UsuarioAutenticado.java` |
| Emissão e leitura do JWT (8h, sem refresh) | `seguranca/JwtService.java` |
| Filtro que popula o contexto a partir do `Bearer` | `seguranca/FiltroJwt.java` |
| RBAC, CORS e respostas 401/403 em JSON | `seguranca/SecurityConfig.java` |
| Porta de identidade e mock do SSO | `auth/IdentityProvider.java`, `auth/MockIdentityProvider.java` |
| Resolução do conjunto de papéis | `auth/PapeisResolver.java` |
| Cadastro de administradores | `auth/Administrador.java` |
| Endpoints `login`, `me`, `logout`, `usuarios-mock` | `auth/AuthController.java` |

**Frontend** — `sessao/SessaoContexto.tsx` (sessão e papéis), `App.tsx` (guardas
de rota), `componentes/Estrutura.tsx` (menus pela **união** dos papéis) e
`paginas/Entrar.tsx`.

**Testes** — `PapeisResolverTest` (CA-1 a CA-4, incluindo acúmulo) e
`AutenticacaoIT` (CA-5, CA-6, token adulterado, 401 sem token).

### Decisões tomadas na implementação

- **`GET /api/auth/usuarios-mock`** (não previsto no plano): a tela de login
  precisa listar as identidades de teste. É público e some junto com o mock —
  o método `identidadesDisponiveis()` da porta devolverá lista vazia no SSO real.
- **Autoconfiguração de `UserDetailsService` desligada**: não há usuário/senha
  local; sem isso o Spring Boot gera e loga uma senha aleatória a cada boot.
- O papel MAGISTRADO é resolvido por `MagistradoLookup`, uma porta implementada
  no pacote `magistrado` — o pacote de autenticação não depende dele.
