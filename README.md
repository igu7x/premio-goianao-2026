# Prêmio Goianão — Emissão de Certificados (TJGO)

Sistema em que os reconhecidos do Prêmio Goianão — magistrados e servidores —
emitem, por conta própria, o certificado da edição, conforme o selo (Bronze,
Prata, Ouro ou Diamante) conquistado pela unidade judiciária.

O projeto segue **Spec-Driven Development**: as especificações em [`specs/`](specs/)
são o contrato, e o código existe para satisfazê-las. Antes de mudar
comportamento, atualize a spec.

---

## Como rodar

Pré-requisitos: **Java 21**, **Maven 3.9+** e **Node 20+**. Não é preciso ter
banco instalado para desenvolver.

```bash
# 1) backend  (http://localhost:8080)
cd backend
mvn spring-boot:run

# 2) frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

Abra <http://localhost:5173> e escolha uma das identidades de teste.

Na primeira execução o backend cria o banco, aplica as migrações e gera uma
**carga de demonstração**: duas edições (uma publicada e vigente, outra em
rascunho), os oito layouts de cada uma com arte gerada no padrão correto,
magistrados reconhecidos e as listas de servidores já semeadas do EGESP mockado.
Para começar do zero, apague a pasta `backend/data/`.

### Identidades de teste

CPFs fictícios (válidos apenas quanto aos dígitos verificadores):

| CPF | Nome | Papéis |
|---|---|---|
| 101.202.301-00 | Ana Cristina Marques Rebelo | Administrador |
| 403.105.204-95 | Otávio Lemos Peixoto | Administrador + Magistrado |
| 204.506.702-52 | Rafael Siqueira Bittencourt | Magistrado (3 unidades) |
| 309.801.403-23 | Helena Vasconcelos Aires | Magistrado (Diamante) |
| 507.609.805-78 | Marcos Vinícius de Paula | Servidor |
| 805.307.208-92 | Carla Menezes do Amaral | Servidor (2 unidades) |
| 901.703.609-54 | Eduardo Rocha Teixeira | Servidor sem vínculo |

Otávio existe para exercitar o **acúmulo de papéis**: ele vê os menus de
administrador e de magistrado ao mesmo tempo. Eduardo existe para o caminho
negativo: nenhuma opção de emissão.

### Testes

```bash
cd backend  && mvn test       # 121 testes (unitários e de integração)
cd frontend && npm test       # 8 testes de componente (Vitest)
cd frontend && npm run lint   # checagem de tipos
```

Os testes de integração do backend terminam em `*IT` e ficam ao lado do código
que exercitam. Cada um cita, no `@DisplayName`, o critério de aceitação da spec
que verifica — de `CA-1` a `CA-8`, feature a feature. Os testes de componente
cobrem as duas telas com regra própria: o modo somente-inclusão da feature 009 e
a conferência pública da 007.

### Problemas comuns

Na dúvida, o atalho resolve os três casos abaixo de uma vez:

```powershell
.\liberar-portas.ps1
```

Ele encerra processos java/node presos em 8080 e 5173–5175 e remove a trava do
banco, preservando os dados. Se a porta estiver ocupada por outro programa, ele
avisa em vez de encerrar.

**`Port 8080 was already in use`** — já há um backend rodando, normalmente de uma
execução anterior que não foi encerrada.

**O backend não sobe e reclama que o banco está em uso** — sobrou o arquivo de
trava (`backend/data/goianao.lock.db`) de um encerramento forçado. Apagar **só a
trava** resolve; nunca apague o `goianao.mv.db`, que é o banco inteiro.

**`Schema "public" not found` no H2** — o banco local é anterior à troca do
Flyway pelo Liquibase e foi criado com o esquema em minúsculas, grafia que a
configuração atual não usa mais. O arquivo só abre com a URL antiga, então não
há como migrá-lo em execução: renomeie `backend/data/goianao.mv.db` e deixe a
carga de demonstração recriar na próxima subida. Bancos PostgreSQL **não** são
afetados — lá a grafia sempre foi coerente.

> Se houver um `goianao.mv.db.antes-do-liquibase-*.bak` na pasta, é exatamente
> isso: o banco anterior, guardado em vez de apagado. Pode remover quando tiver
> certeza de que não precisa dos dados que estavam nele.

**O frontend subiu em 5174 (ou outra porta)** — normal quando a 5173 está
ocupada, e funciona: o perfil de desenvolvimento aceita qualquer porta local.
Em produção a origem permitida é exata (`goianao.cors.origens`).

### PostgreSQL

O padrão de desenvolvimento é **H2 em modo de compatibilidade PostgreSQL**, para
que o projeto rode sem instalar banco. As mesmas migrações Liquibase valem nos
dois — elas são aplicadas **na subida da aplicação**, nunca por script enviado
à mão.

Para gerar o SQL de uma migração pendente, quando a equipe de banco quiser
revisar antes de aplicar:

```bash
cd backend && mvn liquibase:updateSQL
```
Para usar PostgreSQL de verdade:

```bash
docker compose up -d
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

---

## Estrutura

```
premio-goianao/
├── specs/           # constituição, templates e as 9 features (spec/plan/tasks)
├── backend/         # Java 21 + Spring Boot 3.5
└── frontend/        # React 18 + TypeScript + Vite
```

### Backend

Pacotes por **domínio**, não por camada — cada um traz entidade, repositório,
serviço, controlador e DTOs do seu assunto:

| Pacote | Responsabilidade | Feature |
|---|---|---|
| `seguranca` | JWT, filtro, papéis, RBAC | 001 |
| `auth` | porta de identidade (mock do SSO), resolução de papéis | 001 |
| `edicao` | ciclo de vida da edição e vigência | 002 |
| `layout` | arte, áreas e o `CertificadoRenderer` | 003 |
| `unidade` + `integracao.egesp` | catálogo de unidades vindo do EGESP (mock) | 004 |
| `magistrado` | reconhecidos, importação CSV, maior selo | 004, 009 |
| `servidor` | lista de habilitados por edição × unidade | 008 |
| `certificado` | emissão, código de validação, registro | 005, 006 |
| `publico` | conferência de autenticidade, sem login | 007 |
| `demo` | carga de demonstração (perfil dev) | — |

Decisões que valem conhecer antes de mexer:

- **PDF sob demanda.** Nada de arquivo guardado: persistem-se metadados e o
  `codigo_validacao`. A fidelidade da reemissão vem do travamento do layout ao
  publicar a edição, não de um arquivo arquivado.
- **Um motor de composição só.** `CertificadoRenderer` serve à pré-visualização
  do administrador e às duas emissões. É o que garante que o preview seja
  exatamente o que sai.
- **Integrações atrás de portas.** `IdentityProvider` e `EgespClient` são
  interfaces; hoje há implementação mockada. Trocar pelo SSO/EGESP reais não
  toca no domínio.
- **Autorização sempre no backend.** O frontend apenas reflete o que a API já
  decidiu — inclusive o `podeEditar` das listas de servidores.

### Frontend

React com roteamento por papel e um sistema visual próprio em CSS (sem framework
de UI). Telas: entrada, visão geral, edições, configuração da edição (layouts /
magistrados / servidores), meus certificados, servidores da minha unidade e a
conferência pública.

O editor de layout é o ponto mais delicado: as caixas de nome, unidade e código
são arrastadas sobre a arte, mas o que se grava são **coordenadas em pixels da
imagem original**, convertidas a cada movimento pela escala do canvas.

---

## O que ainda depende do TJGO

Estes pontos estão isolados de propósito; nenhum deles exige rearquitetura:

1. **SSO corporativo** — implementar `SsoIdentityProvider` (OIDC, Authorization
   Code). O contrato `IdentityProvider` já é o que o resto do sistema enxerga.
2. **EGESP** — implementar `EgespClient` contra a API real (unidades e servidores
   por unidade).
3. **Fonte institucional** — colocar o TTF em
   `backend/src/main/resources/fontes/institucional.ttf`. Sem ele, os
   certificados usam a fonte padrão do PDF; a tela de layouts avisa.
4. **Domínio público de verificação** — configurar `GOIANAO_BASE_VERIFICACAO`.
   É essa base que o QR do certificado codifica.
5. **Artes definitivas** — as do setor de comunicação substituem as geradas pela
   carga de demonstração, pela própria tela de layouts.

## Configuração

Tudo em `backend/src/main/resources/application.yml`, sobrescrevível por variável
de ambiente:

| Variável | Para quê | Padrão |
|---|---|---|
| `GOIANAO_JWT_SEGREDO` | assinatura do token (mín. 32 bytes) | chave de desenvolvimento |
| `GOIANAO_BASE_VERIFICACAO` | base da URL pública que o QR codifica | `http://localhost:5173` |
| `GOIANAO_STORAGE_TIPO` | `banco` ou `filesystem` | `banco` |
| `GOIANAO_STORAGE_DIR` | diretório das artes, só com `filesystem` | `./data/artes` |
| `GOIANAO_DB_URL` / `_USER` / `_PASSWORD` | PostgreSQL (perfil `postgres`) | banco local |
| `GOIANAO_URL_FRONTEND` | para onde o SSO devolve o navegador | `http://localhost:5173` |
| `OPENSHIFT_SSO_KEYCLOAK_URL` | base do Keycloak, **com `/auth`** | vazio (SSO desligado) |
| `OPENSHIFT_SSO_KEYCLOAK_REALM` | realm | vazio |
| `OPENSHIFT_SSO_CLIENT_ID` | client id | vazio |
| `OPENSHIFT_SSO_SECRET` | client secret | vazio |
| `OPENSHIFT_SSO_KEYCLOAK_REDIRECT_URI` | callback registrado no client | vazio |
| `GOIANAO_SSO_CLAIMS_CPF` | claims tentados até achar o CPF | `cpf,CPF,preferred_username` |

> **Em produção, defina `GOIANAO_JWT_SEGREDO`.** O valor padrão existe apenas
> para o ambiente local e está versionado — sem a variável, a aplicação **sobe
> normalmente** assinando com um segredo que está no repositório.

> **O SSO só liga com as cinco propriedades `OPENSHIFT_SSO_*` preenchidas.**
> Faltando qualquer uma, o sistema segue no login mockado em vez de subir
> quebrado. E a URL do Keycloak do TJGO **precisa terminar em `/auth`** — o
> servidor é anterior à versão 17, e sem o sufixo o login falha com
> "Resource not found".

### Onde ficam as artes dos certificados

No banco, na tabela `arte_layout` — não em disco. A arte faz parte da
autenticidade do certificado: uma reemissão feita daqui a anos precisa dela, e
guardá-la no banco garante que ela seja restaurada pelo mesmo backup que
restaura as emissões.

Bases criadas antes dessa mudança têm os layouts apontando para arquivos. Elas
são migradas sozinhas na primeira subida, e o log diz quantas artes vieram; os
arquivos em `backend/data/artes` deixam de ser usados e podem ser apagados.
