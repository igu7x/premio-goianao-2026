# Reunião com a infraestrutura — o que perguntar

O que o sistema é, em três linhas, e as cinco perguntas cuja resposta muda o que
eu preciso escrever. O resto eles vão te explicar sozinhos ao descrever como
trabalham.

## O sistema

Spring Boot 3.5 com Java 21, empacotado como JAR, porta 8080. Frontend React
compilado em estático. PostgreSQL, com as migrações aplicadas na subida da
aplicação. Sem estado de sessão — escala horizontalmente, não precisa de sticky
session. Grava em disco só as artes dos certificados.

## As perguntas

**1. O build é por Dockerfile ou por S2I?**
Muda o que eu entrego: `Dockerfile` + `.gitlab-ci.yml`, ou `BuildConfig` do
OpenShift.

**2. Frontend e API ficam na mesma rota?**
O front chama a API por caminho relativo. Mesma origem (nginx servindo o
estático e passando `/api` para o backend): nada muda. Rotas separadas: preciso
mexer no build do front e no CORS.

**3. Tem volume persistente? RWO ou RWX?**
As artes dos certificados são arquivos. RWX está ótimo. RWO só funciona com uma
réplica. Sem volume, eu guardo no banco.

**4. Qual a versão do PostgreSQL?**
Validei no 18 e funciona, mas o Flyway do projeto só foi testado até o 17. Se
for 18, subo a versão do Flyway antes.

**5. Qual vai ser o domínio definitivo?**
Ele é impresso no QR de cada certificado. Se mudar depois que houver emissões,
os QR antigos apontam para um endereço morto — e certificado é documento, não dá
para reemitir todo mundo.

## O que pedir

Projeto no OpenShift, banco PostgreSQL, repositório no GitLab com runner,
acesso ao registry, e um Secret para a senha do banco e o segredo do JWT.

Se o runner não tiver saída para a internet, preciso do endereço do Nexus
interno — o build baixa dependências Maven e npm.

---

## Pendências minhas (não é assunto da reunião)

- Dockerfiles, `.gitlab-ci.yml` e manifests do OpenShift: não existem ainda.
- O Actuator não está no `pom.xml`, então não há `/actuator/health` para as
  probes. Eles vão pedir.
- `GOIANAO_JWT_SEGREDO` tem valor padrão no `application.yml`. Se ninguém
  definir a variável em produção, a aplicação sobe assinando com um segredo que
  está no repositório — falha silenciosa.
- SSO e EGESP seguem mockados.

Variáveis a definir no deploy: `SPRING_PROFILES_ACTIVE=postgres`,
`GOIANAO_DB_URL`, `GOIANAO_DB_USER`, `GOIANAO_DB_PASSWORD`,
`GOIANAO_JWT_SEGREDO`, `GOIANAO_BASE_VERIFICACAO`, `GOIANAO_STORAGE_DIR`.
