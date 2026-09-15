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
| `OPENSHIFT_SSO_CLAIMS_EMAIL` | não | Lista separada por vírgula, tentada em ordem até achar um e-mail válido. Padrão `email,preferred_username`, que atende o realm do tribunal — **não precisa ser definida**. O e-mail é a chave da pessoa em todo o sistema (DI-24). |
| ~~`OPENSHIFT_SSO_CLAIMS_CPF`~~ | — | Não é mais lida desde 2026-09-10 (DI-24). Se existir no ambiente, é ignorada. |
| `OPENSHIFT_SSO_CLAIMS_NAME` | não | Padrão `name`. |
| `GOIANAO_SUPERADMIN_EMAIL` / `_SENHA` / `_NOME` | 1ª subida | Cria o primeiro superadministrador quando não existe nenhum. A senha é gravada como hash BCrypt e **não fica no repositório**. Podem ser removidas depois da primeira subida. `_CPF` é opcional. |

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

## Como o frontend é servido em produção

A pipeline executa `npm run <script>` numa imagem Node e expõe a **8080**. Sem
um script `start`, ela acabou executando `npm run dev` — o servidor de
desenvolvimento do Vite, que escuta em `localhost:5173`. O pod ficava "Running"
e a Route devolvia 503, porque não havia nada ouvindo onde o Service procurava.

O script `start` passa a rodar `frontend/servidor.mjs`: servidor estático sem
dependência alguma (só o que vem no Node), que serve `dist/`, escuta em
`0.0.0.0:${PORT:-8080}` e devolve `index.html` para rota desconhecida — o que o
QR do certificado exige, já que `/verificar/<codigo>` é rota do React Router e
não arquivo.

Requisitos da pipeline:

1. `npm run build` **antes** de `npm start`. Sem `dist/index.html` o processo
   encerra com código 1 e a mensagem diz exatamente isso, em vez de subir um
   servidor que responde 404 em tudo.
2. Não definir `NPM_RUN=dev` nem `DEV_MODE=true` — é o que faz a imagem Node
   escolher `dev` em vez de `start`.
3. `API_BASE_URL` no ambiente do pod. O `servidor.mjs` reescreve o `config.js`
   na subida a partir dela.

Há uma sonda em `GET /saude` que responde `ok` sem tocar no disco, útil para as
probes.

## As URLs precisam ser https

As Routes do OpenShift redirecionam para HTTPS, e **página em HTTPS não pode
chamar API em HTTP**: o navegador bloqueia como conteúdo misto antes de a
requisição sair, e o que aparece na tela é só "Failed to fetch". Custou um
deploy para descobrir.

Vale para as três, com `https://`:

- `API_BASE_URL` (frontend)
- `GOIANAO_CORS_ORIGENS` (API) — precisa casar com a origem exata do navegador
- `GOIANAO_BASE_VERIFICACAO` (API) — **esta é a mais grave**: vai impressa no QR
  de cada certificado. Um QR gerado com `http://` fica errado para sempre,
  porque o PDF já foi emitido e distribuído.

O cliente HTTP promove `http://` para `https://` quando a própria página está em
HTTPS, com aviso no console — rede de proteção para a configuração errada, não
substituto dela.

## O certificado das Routes (2026-09-11)

No Edge, a tela de login dizia "Nenhuma forma de acesso disponível". O console
mostrava a causa real: `ERR_CERT_AUTHORITY_INVALID` na chamada a
`/api/auth/situacao`. As Routes usam um certificado de uma autoridade em que o
navegador não confia. No frontend a pessoa vê o aviso e clica em "continuar";
na API, que tem endereço próprio e é chamada em segundo plano, o navegador
simplesmente recusa, sem perguntar nada. No Chrome funcionava porque a exceção
para a API já tinha sido aceita antes naquela máquina.

- **Contorno, por navegador:** abrir uma vez o endereço da API, aceitar o aviso
  e recarregar o frontend. A tela de login agora explica isso e traz o link,
  em vez de dizer que não há forma de acesso.

> **Correção do diagnóstico (2026-09-15).** O certificado não está errado: ele
> cobre `*.apps.ocp-c01.tjgo.jus.br` nos nomes alternativos e é emitido pela
> **AC TJGO Serviços**, a autoridade interna do tribunal. O problema é só de
> *confiança na máquina*: onde essa autoridade está instalada — as máquinas de
> domínio, como a da infraestrutura — tudo funciona, inclusive no Edge. Onde
> não está, o navegador recusa. Portanto isto **não bloqueia o tribunal**;
> bloqueia quem estiver fora do domínio. A afirmação anterior, de que seria
> preciso pedir exceção a cada magistrado, estava errada.
>
> O plano da infraestrutura para homologação é um endereço próprio
> (`premio-goianao-teste.tjgo.jus.br`), que herda o certificado público do
> domínio — é o que produção já terá. **Quando esse endereço existir**, quatro
> coisas mudam juntas: `API_BASE_URL` (frontend), `GOIANAO_CORS_ORIGENS`,
> `GOIANAO_BASE_VERIFICACAO` e a URI de callback no client do Keycloak (e nas
> *Valid redirect URIs*). A do QR é a mais grave: certificado emitido com o
> endereço antigo fica com QR errado para sempre.
>
> O certificado atual vence em **02/12/2026**.
- **Correção de verdade, com a infra:** as Routes precisam de certificado de
  uma autoridade confiável nas máquinas do tribunal (o curinga do tribunal, ou
  a CA interna distribuída por política). Em produção isso é obrigatório: não
  dá para pedir a cada magistrado e servidor que aceite exceção de certificado.

## As duas variáveis de SSO que erraram no primeiro login

**`OPENSHIFT_SSO_KEYCLOACK_URL`** veio sem `https://`. O `Location` do
redirecionamento saiu relativo e o navegador pediu
`/api/auth/sso/sso.tjgo.jus.br/realms/...` à **própria API**, que respondeu 404.
Nada no erro apontava para a variável.

**`OPENSHIFT_SSO_KEYCLOACK_REDIRECT_URI`** veio como
`https://goianao-stag-frontend.../*` — o **padrão de URIs permitidas** do
cadastro do client, não uma URI de callback. Duas coisas erradas: o `*`, que não
é endereço, e o destino, que é a **API**, não o frontend. Quem recebe o `code`
do Keycloak e o troca por token é o backend; só depois o navegador volta ao
frontend com a sessão pronta.

Valores corretos em homologação:

```
OPENSHIFT_SSO_KEYCLOACK_URL=https://sso.tjgo.jus.br
OPENSHIFT_SSO_KEYCLOACK_REDIRECT_URI=https://goianao-stag-api.apps.ocp-c01.tjgo.jus.br/api/auth/sso/callback
```

Esse mesmo endereço de callback precisa estar nas **Valid redirect URIs** do
client `goianao-stag` no realm `tjgo.gov-tst` — é lá que o `/*` faz sentido.

Sobre o sufixo `/auth` na URL do Keycloak: era obrigatório até a versão 16 e
deixou de ser na 17. A URL montada no primeiro teste não o tinha e chegou a
formar `/realms/...` direto, o que indica instalação nova. Se o login falhar com
"Resource not found", é a primeira coisa a testar.

Os endereços agora são completados com `https://` quando vêm sem esquema, com
aviso no log — rede de proteção, não substituto da configuração correta.

## Keycloak: valores confirmados no servidor (09/09/2026)

Lidos do discovery, não deduzidos:

```
curl https://sso.tjgo.jus.br/auth/realms/tjgo.gov-tst/.well-known/openid-configuration
```

| | |
| --- | --- |
| issuer | `https://sso.tjgo.jus.br/auth/realms/tjgo.gov-tst` |
| authorization | `.../protocol/openid-connect/auth` |
| token | `.../protocol/openid-connect/token` |
| jwks | `.../protocol/openid-connect/certs` |
| logout | `.../protocol/openid-connect/logout` |

**O sufixo `/auth` é obrigatório.** Sem ele o Keycloak responde "Resource not
found" — e a resposta vem com a cara dele, o que engana: parece realm errado ou
client inexistente, e é só o caminho. O mesmo `curl` sem `/auth` devolve 404 e
com `/auth` devolve 200; é o teste de dez segundos.

```
OPENSHIFT_SSO_KEYCLOACK_URL=https://sso.tjgo.jus.br/auth
```

Os caminhos que a aplicação monta (`issuer + /protocol/openid-connect/...`)
batem exatamente com os do discovery, então nada além da base precisa mudar.

### O CPF não vem — e deixou de ser necessário

> **Resolvido em 2026-09-10 (DI-24).** O sistema passou a identificar as
> pessoas pelo **e-mail corporativo**, que o realm publica. As perguntas abaixo
> ficam como registro de por que se chegou a essa decisão; não há mais nada a
> pedir à infra sobre CPF.

O `claims_supported` do realm lista apenas:

```
iss sub aud exp iat auth_time name given_name family_name
preferred_username email acr azp nonce
```

**Não há `cpf`.** Isso não é prova definitiva: no Keycloak, `claims_supported` é
uma lista estática do realm e **não reflete protocol mappers configurados por
client**. O client `goianao-stag` pode ter um mapper que acrescente `cpf` ao
token sem aparecer aqui.

Duas perguntas para a infra, nessa ordem:

1. O client `goianao-stag` tem mapper de CPF? Se sim, qual o nome do claim —
   basta pôr em `OPENSHIFT_SSO_CLAIMS_CPF`.
2. Se não tem: o `preferred_username` do realm **é** o CPF? Se for, já funciona,
   porque ele é o último da lista tentada. Se for matrícula ou login, precisamos
   de um mapper — sem CPF o sistema não identifica ninguém, já que magistrado
   reconhecido, servidor habilitado e certificado emitido são todos indexados
   por ele.

O jeito mais rápido de responder as duas: fazer um login de teste, capturar o
`id_token` e olhar o conteúdo.

## Portas de entrada por ambiente

Além do SSO, existem duas formas de entrar. **As duas nascem desligadas** — a
variável ausente deixa o ambiente no estado seguro.

| Variável | dev | **stag** | **prod** | O que é |
| --- | --- | --- | --- | --- |
| `GOIANAO_LOGIN_SENHA` | `true` (perfil) | **`true`** | não definir | Login por e-mail e senha do cadastro próprio. |
| `GOIANAO_LOGIN_MOCK` | `true` (perfil) | não definir | não definir | Identidade de teste. **Dispensa credencial.** |

Em homologação, portanto, basta:

```
GOIANAO_LOGIN_SENHA=true
```

O login mockado **não pode** ser ligado fora de desenvolvimento: informado o
e-mail, a sessão é emitida sem senha, e `/api/auth/usuarios-mock` publica as
identidades disponíveis — uma delas administrador. A aplicação **recusa
iniciar** com `GOIANAO_LOGIN_MOCK=true` fora dos perfis `dev`/`test`, no mesmo
espírito da recusa por segredo de JWT público.

Endpoint desligado responde **404**, não 403: 403 confirmaria que existe e está
apenas fechado. Para quem sondar a API em produção, o login mockado não existe.

O frontend consulta `GET /api/auth/situacao` e desenha só o que o ambiente
aceita. Nada disso é decidido no build — a mesma imagem serve homologação e
produção.

## O primeiro superadministrador

Criado na subida, quando não existe nenhum. As variáveis vão no ambiente da
**API**:

```
GOIANAO_SUPERADMIN_EMAIL=teixeiraigor09@gmail.com
GOIANAO_SUPERADMIN_SENHA=<a senha>
GOIANAO_SUPERADMIN_NOME=Igor Freitas Costa Cupertino Teixeira
```

`GOIANAO_SUPERADMIN_CPF` também existe, mas é opcional desde a DI-24: quem
identifica o superadministrador é o e-mail.

A senha é gravada como **hash BCrypt**; o valor em claro não é guardado nem
registrado em log. Ela nunca entra no repositório — por isso vem de variável, e
o ideal é criá-la como Secret no cluster, não em ConfigMap.

Depois da primeira subida bem-sucedida todas podem ser removidas: o usuário
já está no banco. Se não forem, não há efeito — a criação só ocorre quando não
existe superadministrador algum.

Sem essas variáveis a aplicação sobe normalmente e o log diz que nenhum
superadministrador foi cadastrado. Como o login por senha depende de alguém
existir no cadastro, sem elas não há como entrar em homologação a não ser pelo
SSO.

## API corporativa do TJGO — ConnectTJ (2026-09-14)

A integração com o RH (unidades, lotações e dados cadastrais) fala com a API
ConnectTJ, que lê o SIEDOS. **As quatro primeiras são obrigatórias juntas:**
faltando qualquer uma, a integração se declara desligada, o sistema usa os dados
mockados e o log diz o que falta — a aplicação não deixa de subir por isso.

| Variável | Obrigatória | O que é |
| --- | --- | --- |
| `GOIANAO_CONNECTTJ_URL` | não¹ | Base da API, com esquema (homologação: `https://connecttj-api-stag.tjgo.jus.br`). |
| `GOIANAO_CONNECTTJ_TOKEN_URL` | não¹ | Endereço do token no Keycloak. Em homologação, realm `DG-TST`: `https://sso.tjgo.jus.br/auth/realms/DG-TST/protocol/openid-connect/token`. O token é obtido por `client_credentials` e vale 5 minutos. |
| `GOIANAO_CONNECTTJ_CLIENT_ID` | não¹ | Client do Goianão na API. |
| `GOIANAO_CONNECTTJ_SECRET` | não¹ | Segredo do client. **Secret, nunca ConfigMap.** |
| `GOIANAO_CONNECTTJ_TAMANHO_PAGINA` | não | Lotados por página ao varrer uma unidade. Padrão `100`. |
| `GOIANAO_CONNECTTJ_DOMINIO_EMAIL` | não | Padrão `tjgo.jus.br`. Boa parte dos lotados vem do RH sem e-mail; para esses, o login do AD mais este domínio reconstrói o endereço. |

¹ Juntas: sem as quatro, valem os dados mockados.

**Sobre o realm:** é `DG-TST` por enquanto (informado em 2026-09-15), **ainda
não é o definitivo** — é um realm diferente do `tjgo.gov-tst`, usado no login
das pessoas. Por isso o endereço do token é variável, e não montado no código a
partir do realm do SSO: quando sair a versão definitiva, troca-se a variável e
reinicia, sem build novo.

**O que ainda falta pedir:** o client (id e secret). Sem token, a API responde
401 até em homologação.

**Como conferir sem acesso ao cluster:** `GET <api>/api/sincronizacao/situacao`
(exige superadministrador) responde `"ligada": true` quando a integração está
configurada.
