# Variáveis de ambiente

> Resposta ao levantamento da infraestrutura (2026-09-09). Os nomes aqui são os
> que a **infra do TJGO já provisiona** no OpenShift; a aplicação foi ajustada
> para lê-los com essa grafia, em vez de pedir que renomeassem do lado deles.

## Backend

| Variável | Obrigatória | O que é |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | **sim** | Precisa ser `postgres`. Sem isto o padrão é `dev`, que sobe em H2 em memória — a aplicação funcionaria e perderia tudo no restart. |
| `GOIANAO_JWT_SEGREDO` | **sim** | Chave HS256 que assina o token de sessão. Mínimo de 32 bytes. A aplicação **recusa subir** fora de `dev`/`test` se vier o segredo público do repositório. |
| `GOIANAO_BASE_VERIFICACAO` | **sim** | Endereço público do frontend. Vai **impresso no QR de cada certificado**, então precisa ser o domínio definitivo. |
| `GOIANAO_STORAGE_TIPO` | não | `banco` (padrão) ou `filesystem`. Deve ficar `banco` no OpenShift: o pod não tem disco que sobreviva ao restart. |
| `GOIANAO_STORAGE_DIR` | não | Só usada quando `TIPO=filesystem`. Com `banco`, é ignorada. |
| `OPENSHIFT_POSTGRESQL_DB_HOST` | **sim** | |
| `OPENSHIFT_POSTGRESQL_DB_PORT` | **sim** | |
| `OPENSHIFT_POSTGRESQL_DB_NAME` | **sim** | |
| `OPENSHIFT_POSTGRESQL_DB_SCHEMA` | **sim** | Vira `?currentSchema=` na URL JDBC **e** o `default-schema` do Liquibase. |
| `OPENSHIFT_POSTGRESQL_DB_USERNAME` | **sim** | Precisa poder criar tabela: as migrações rodam na subida. |
| `OPENSHIFT_POSTGRESQL_DB_PASSWORD` | **sim** | |
| `OPENSHIFT_SSO_KEYCLOACK_URL` | não¹ | Inclui `/auth` no fim — o Keycloak do tribunal é anterior à v17. |
| `OPENSHIFT_SSO_KEYCLOACK_REALM` | não¹ | |
| `OPENSHIFT_SSO_KEYCLOACK_CLIENT_ID` | não¹ | |
| `OPENSHIFT_SSO_KEYCLOACK_SECRET` | não¹ | |
| `OPENSHIFT_SSO_KEYCLOACK_REDIRECT_URI` | não¹ | |
| `OPENSHIFT_SSO_CLAIMS_CPF` | não | Lista separada por vírgula, tentada em ordem. Padrão `cpf,CPF,preferred_username`. **Ainda não sabemos em qual claim o CPF vem** — quando a infra confirmar, é só definir esta variável. |
| `OPENSHIFT_SSO_CLAIMS_NAME` | não | Padrão `name`. |
| `GOIANAO_SUPERADMIN_EMAIL` / `_SENHA` / `_CPF` / `_NOME` | 1ª subida | Cria o primeiro superadministrador quando não existe nenhum. A senha é gravada como hash BCrypt e **não fica no repositório**. Podem ser removidas depois da primeira subida. |

¹ As cinco de SSO são obrigatórias **juntas**: faltando qualquer uma, o SSO fica
desligado e vale o login mockado. É por isso que a aplicação aceita também a
grafia `KEYCLOAK` (sem o C): uma variável com o nome trocado não quebraria a
subida — deixaria o sistema aberto com login de mentira, que é bem pior.

## Frontend

**Uma só: `API_BASE_URL`** — a URL pública da rota da API (ex.:
`https://goianao-api.<dominio>`). Nenhuma das outras da lista é lida pelo
frontend; em particular, `OPENSHIFT_FRONTEND_AMBIENTE` não é usada.

Ela é consumida em `frontend/.s2i/bin/run`, que reescreve `config.js` na subida
do container. É deliberado não usar variável de build (`VITE_*`): o Vite
congelaria o valor no bundle e seria preciso **uma imagem por ambiente**. Assim
a mesma imagem de homologação é promovida para produção.

Se um dia frontend e API ficarem atrás da mesma rota, a variável pode ficar
vazia — vazio significa "mesma origem".

## O segredo do JWT não deve trafegar

`GOIANAO_JWT_SEGREDO` é chave de assinatura: quem a tem forja sessão de
qualquer usuário, superadministrador incluso. Não deve ser enviada por e-mail,
GLPI ou WhatsApp, nem ficar no repositório — que é público.

O valor deve ser **gerado dentro do cluster**, sem ninguém nunca o ler:

```sh
oc create secret generic goianao-default \
  --from-literal=GOIANAO_JWT_SEGREDO="$(openssl rand -base64 48)"
```

Se for trocado depois, todas as sessões abertas caem — os usuários apenas
entram de novo. Nenhum certificado já emitido é afetado: o código de validação
não depende dessa chave.

## Acrescentado depois do primeiro deploy em homologação (2026-09-09)

| Variável | Obrigatória | O que é |
| --- | --- | --- |
| `GOIANAO_CORS_ORIGENS` | **sim** | Origem pública do frontend, separada por vírgula. Frontend e API têm Routes distintas, então o navegador as trata como origens diferentes: sem esta variável vale o padrão de desenvolvimento (`localhost`) e **nenhuma chamada da tela funciona**, com o erro aparecendo só no console do navegador. Em homologação: `http://goianao-stag-frontend.apps.ocp-c01.tjgo.jus.br`. |

Aceita padrão além de origem exata (`http://host,https://host`), porque em
desenvolvimento o Vite troca de porta.
