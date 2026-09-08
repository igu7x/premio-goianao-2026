# Reunião com a infraestrutura — o que perguntar

Atualizado depois de um levantamento feito em outro sistema do tribunal que já
roda no OpenShift. Três das cinco perguntas originais já estão respondidas por
lá; o que sobrou é isto.

> As referências a hosts, cluster, namespaces e nomes de secret desse
> levantamento **não estão neste repositório**, que é público. Elas ficam no
> arquivo local `SSO_E_OPENSHIFT_REFERENCIA.md`, fora do controle de versão.

## O sistema

Spring Boot 3.5 com Java 21, empacotado como JAR, porta 8080. Frontend React
compilado em estático, servido separadamente. PostgreSQL, com migrações
Liquibase aplicadas na subida. Sem estado de sessão e **sem escrita em disco** —
as artes dos certificados ficam no banco.

## As perguntas

**1. Em qual claim do Keycloak vem o CPF?**
A mais importante. Todo o domínio é indexado por CPF: magistrado reconhecido,
servidor habilitado, certificado emitido. O outro sistema chaveia por e-mail e
não sabe responder. Se o realm não expuser CPF, o plano B é resolvê-lo a partir
do e-mail pelo EGESP — o que amarra o login à disponibilidade do EGESP e é pior.

Se ninguém souber de cabeça: fazer um login de teste, capturar o `id_token` e
ler os claims, ou chamar o `userinfo`.

**2. Como se pede o registro do client no Keycloak?**
Com qual equipe, o que precisa ser informado, qual o prazo, e se homologação e
produção usam clients separados. No outro sistema os valores chegaram por um
secret criado manualmente pela infra, e ninguém registrou o processo. Costuma
virar chamado no GLPI, e fila é o que atrasa.

**3. Qual vai ser o domínio público definitivo?**
Ele é impresso no QR de cada certificado. Se mudar depois que houver emissões,
os QR antigos apontam para um endereço morto — e certificado é documento.

Atenção: no outro sistema a URL pública é um CNAME para um proxy, que encaminha
para a Route. O domínio do QR é o do proxy, não o da Route. Peça o nome final,
não o intermediário.

**4. Qual pipeline vai construir, e quem a cria?**
Não é GitLab CI: o deploy é por Tekton, e as pipelines leem uma branch
específica. Preciso saber se criam uma nova para este sistema e o que devo
entregar no repositório para ela funcionar.

**5. A Route aceita upload de 30 MB?**
As artes dos certificados são imagens em 300 DPI. Qual o modo de TLS
(edge/reencrypt/passthrough) e qual annotation controla o limite de corpo na
versão do OpenShift de vocês.

**6. Qual namespace, e o runner tem saída para a internet?**
Se o runner for fechado, preciso do endereço do Nexus/Artifactory e dos
`settings.xml` / `.npmrc`.

## O que pedir

Projeto no OpenShift (homologação e produção), banco PostgreSQL, repositório no
GitLab, client no Keycloak, e os secrets — separados por finalidade, como já se
faz no tribunal: `<app>-default`, `<app>-postgres`, `<app>-sso`.

---

## Já respondido — não precisa perguntar

**Build.** Frontend por **S2I** com httpd da Red Hat; backend construído pela
pipeline. Imagens base do catálogo UBI 9 da Red Hat (`openjdk-21`, `nodejs-20`).

**Frontend e API são apps separados**, com rotas distintas. Por isso o frontend
precisa de variável com a base da API — já implementado.

**Volume persistente: não existe e não se pede.** O padrão é arquivo grande em
object storage e pequeno no banco. Aqui vai tudo para o banco: são 8 artes por
edição, e elas fazem parte da autenticidade do certificado — precisam estar no
mesmo backup da tabela `certificado_emitido`, senão uma reemissão de daqui a
seis anos não sai.

**Probes** em `/actuator/health`. Liveness com `initialDelaySeconds: 60`, porque
a migração no boot passa de 30s e derruba o pod em loop.

**O container roda com UID aleatório do grupo 0.** Qualquer diretório de escrita
precisa de `chgrp -R 0` e `chmod -R g+rwX`. Como não escrevemos mais em disco,
não nos afeta.

**Keycloak do tribunal:** OIDC. A URL precisa terminar em **`/auth`**, porque a
versão em uso é anterior à 17. E o logout exige o parâmetro antigo
`redirect_uri`, além do `post_logout_redirect_uri`.

**Não passar sessão por query string.** Já causou 502 no proxy em outro sistema
quando o payload cresceu. Nós usamos cabeçalho `Authorization`, então não se
aplica — mas é bom saber que o limite existe.
