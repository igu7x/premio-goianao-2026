# 2026-09-09/10 — Primeiro deploy em homologação

> Diário de implementação. Relata a subida no OpenShift do tribunal, na ordem em
> que os problemas apareceram. Os valores de cada variável estão em
> `specs/deploy/variaveis-de-ambiente.md`; aqui fica o porquê e a sequência.

## Ordem dos acontecimentos

Cada item foi descoberto só depois de o anterior ser resolvido — nenhum deles
aparecia antes de o sistema chegar ao ambiente real.

1. **Nomes de variável.** A infra provisiona `OPENSHIFT_SSO_KEYCLOACK_*` e
   `OPENSHIFT_POSTGRESQL_DB_*`; a aplicação lia outros nomes. Ajustado do nosso
   lado. A URL JDBC passou a ser montada a partir de host/porta/base/esquema.
2. **`backend/` virou `api/`**, a pedido da infra (`git mv`, histórico
   preservado). Ver `2026-09-09-renomeia-backend-para-api.md`.
3. **API no ar.** Primeira resposta real: 401 do filtro de segurança — o que
   provou Spring, PostgreSQL e as migrações funcionando.
4. **Frontend em 503.** A pipeline roda `npm run <script>` numa imagem Node; sem
   script `start`, caiu no `dev` — o Vite escutando em `localhost:5173`. Entrou
   `frontend/servidor.mjs`.
5. **CORS fixo em localhost**, sem variável. Passou a vir de
   `GOIANAO_CORS_ORIGENS`.
6. **Conteúdo misto.** Página em HTTPS, `API_BASE_URL` em HTTP. O cliente passou
   a promover o esquema, com aviso.
7. **URL do Keycloak sem esquema** — o redirecionamento saiu relativo e virou 404
   na própria API. Endereços de SSO passaram a ser completados com `https://`.
8. **Keycloak sem `/auth`** — "Resource not found". Confirmado no discovery do
   servidor que o sufixo é obrigatório.
9. **`REDIRECT_URI` errada** — veio com o padrão de URIs permitidas do client
   (`/*`), apontando para o frontend. O callback é da API.

## Falha de segurança encontrada e corrigida

O login mockado (`POST /api/auth/login`) respondia em qualquer ambiente e emitia
sessão sem credencial. Corrigido no mesmo dia: as portas de entrada viraram
configuração (`goianao.login.senha`, `goianao.login.mock`), ambas desligadas por
padrão, e a aplicação recusa iniciar com o login mockado fora de `dev`/`test`.
Detalhes no commit `a62b309` e em `variaveis-de-ambiente.md`.

Efeito colateral previsível que aconteceu: com a trava no ar e
`GOIANAO_LOGIN_SENHA` ainda não criada, o formulário de senha sumiu de
homologação. A variável foi pedida à infra.

## Superadministrador inicial

Criado pela migração 009, com senha aleatória de 24 caracteres. O hash está no
repositório — que é público — e isso só é aceitável pelos dois motivos
registrados no próprio arquivo da migração. A senha deve ser trocada no
primeiro acesso.

## Pendências

- **Com a infra:** `OPENSHIFT_SSO_KEYCLOACK_URL` com `/auth`,
  `OPENSHIFT_SSO_KEYCLOACK_REDIRECT_URI` apontando para o callback da API,
  `GOIANAO_LOGIN_SENHA=true`, e o callback nas *Valid redirect URIs* do client.
- ~~**Identificação pelo SSO.**~~ Resolvido em 2026-09-10: o e-mail corporativo
  virou a chave do domínio inteiro e o CPF ficou opcional. Ver DI-24 e
  `2026-09-10-email-como-chave.md`.
- **Hosts internos no repositório público.** Este diário evita citá-los, mas
  `variaveis-de-ambiente.md` e algumas mensagens de commit os citam. Decidir se
  sanitiza, coerente com a regra que manteve `SSO_E_OPENSHIFT_REFERENCIA.md` fora
  do git.
