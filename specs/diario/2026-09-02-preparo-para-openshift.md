# Preparo para o OpenShift

**Data.** 02/09/2026
**Origem.** Um levantamento feito em outro sistema do tribunal, já rodando no
cluster, respondeu por escrito o que eu ia perguntar à infra.

> Esse levantamento cita hosts, cluster, namespaces e nomes de secret, e por
> isso **não está neste repositório**, que é público. Ele fica no arquivo local
> `SSO_E_OPENSHIFT_REFERENCIA.md`, fora do controle de versão.

Três suposições minhas caíram com esse documento:

| Eu supunha | O que é |
|---|---|
| Pipeline em GitLab CI | **Tekton**, do lado do tribunal, disparada por uma branch específica |
| Talvez uma rota só | **Dois apps**, rotas distintas — o frontend precisa saber a URL da API |
| Pediríamos um PVC | **Não existe PVC e não se pede**: arquivo grande em object storage, pequeno no banco |

---

## O que foi feito

### 1. Actuator e probes

As probes do OpenShift precisam de um endpoint de saúde, e não havia nenhum —
a regra em `SecurityConfig` já liberava `/actuator/health`, mas a dependência
nunca foi adicionada.

Liveness e readiness ficaram **separados**, e isso é a parte que importa:

- **liveness** não inclui o banco. Se o PostgreSQL cair, reiniciar o pod não
  resolve nada e ainda cria laço de reinício.
- **readiness** inclui o banco: sem ele a aplicação não atende, então o pod sai
  do balanceamento e volta sozinho quando o banco responder.

`show-details: never`, porque a resposta é pública e não deve revelar host de
banco nem espaço em disco. O resto do actuator responde 401 — verificado.

### 2. Base da API resolvida em execução, não em build

Como frontend e API são apps separados, o frontend precisa da URL da API. O
caminho óbvio seria `import.meta.env.VITE_API_BASE_URL`, mas o Vite congela isso
no bundle — seria **uma imagem por ambiente**.

Em vez disso, `public/config.js` é servido ao lado do `index.html` e reescrito
pelo `.s2i/bin/run` a partir de `API_BASE_URL`. A mesma imagem sobe em
homologação e é promovida para produção sem rebuild. Vazio significa mesma
origem, que é o que vale em desenvolvimento com o proxy do Vite.

### 3. Artes fora do disco (migração V6)

O pod roda sem volume persistente. Mais do que isso: o OpenShift roda o
container com **UID aleatório do grupo 0**, então escrita em disco exige
`chgrp -R 0` e `g+rwX` — e ainda assim o conteúdo some no restart.

As artes passaram para o banco, em `arte_layout`. A porta `ImageStorage` já
isolava isso; entrou `BancoImageStorage` como padrão e o `FilesystemImageStorage`
virou opcional (`goianao.storage.tipo=filesystem`).

**Aqui discordei da recomendação do levantamento**, que manda arquivo grande para
object storage. O motivo é o ciclo de vida, não o tamanho: são 8 artes por
edição, e a arte **faz parte da autenticidade do certificado** — uma reemissão
feita daqui a seis anos precisa dela. No banco, ela é restaurada pelo mesmo
backup que restaura `certificado_emitido`; não existe o cenário em que o registro
da emissão volta e a arte não. Com object storage seriam dois backups a manter
em sincronia.

`byte[]` sem `@Lob`, de propósito: com `@Lob` o Hibernate mapeia para large
object (OID) no PostgreSQL, o que cria tabela auxiliar e muda o comportamento do
dump. Sem a anotação vira `bytea` no PostgreSQL e VARBINARY no H2 — o mesmo
código serve aos dois.

### 4. SSO por OIDC contra o Keycloak do TJGO

Implementado atrás de configuração: sem as cinco propriedades do client, o SSO
se declara desligado e o login mockado continua valendo. O frontend pergunta a
situação e mostra um caminho ou o outro, sem alteração de código no dia da
virada.

Três armadilhas do Keycloak do tribunal, herdadas do levantamento e já
tratadas:

- **O sufixo `/auth` na URL é obrigatório** (Keycloak anterior à v17). Há teste
  garantindo que ele sobrevive à montagem da URL.
- **O logout precisa do parâmetro antigo `redirect_uri`** (anterior à v19). São
  enviados os dois nomes.
- **O CPF não tem claim garantido.** É uma lista tentada em ordem
  (`goianao.sso.claims-cpf`), então a resposta da infra vira ConfigMap, não
  código.

**O que não foi copiado:** o outro sistema verifica o token à mão, de um jeito
que o próprio levantamento marca como inseguro. Aqui a verificação é do
`NimbusJwtDecoder`, que busca as chaves no JWKS do realm e confere assinatura,
emissor e expiração. A troca do code por token é feita à mão, porque são poucas
linhas; a verificação criptográfica, não.

O token volta ao frontend no **fragmento** da URL, não na query string. Fragmento
não é enviado ao servidor: não entra em log de proxy nem no `Referer`. Em outro
sistema do tribunal, sessão por query string causou 502 no proxy quando o
payload cresceu.

O logout encerra também no provedor. Sem isso, a sessão do Keycloak continua de
pé e o próximo "entrar" reautentica sem pedir nada — o usuário clica em Sair e
volta logado, o que é pior do que não ter o botão.

### 5. Arquivos de build

- `frontend/.s2i/bin/assemble` e `run`, e `frontend/httpd-cfg/01-spa.conf`,
  seguindo o padrão já em produção no tribunal. Diferença: `npm ci` em vez de
  `npm install`, porque existe lock versionado e build reproduzível importa num
  sistema que emite documento.
- O rewrite de SPA não é detalhe: sem ele, **abrir o QR de um certificado
  devolve 404**, porque `/verificar/ABCD-1234-EFGH` é rota do React Router e não
  arquivo no servidor.
- `backend/Dockerfile` de referência, com `chgrp 0`/`g+rwX` e
  `MaxRAMPercentage=75` — a heap acompanha o limit do cgroup em vez de um `-Xmx`
  fixo que precisaria ser editado junto.
- `.gitattributes` forçando LF nos scripts. Sem isso o Git desta máquina os
  grava com CRLF e o container falha com `bad interpreter: No such file or
  directory` — mensagem que não ajuda em nada a achar a causa.

### 6. Migração das artes que já estavam em disco

Uma falha que a própria mudança introduziu, e que só apareceu quando fui
conferir o banco de desenvolvimento: bases criadas antes da V6 têm os layouts
apontando para nomes de arquivo, e o `BancoImageStorage` procura na tabela nova.
O sintoma não seria erro na subida — seria **cada certificado falhando na hora
de emitir**, que é o pior momento possível para descobrir.

`ImportadorDeArtesDoDisco` roda no `ApplicationReadyEvent`, traz o que faltar e
se desliga sozinho na subida seguinte. Não apaga os arquivos: se algo der
errado, o original continua lá para uma segunda tentativa.

---

## Verificação

- **134 testes de backend** (13 novos de SSO) e 8 de frontend, todos passando.
- Migração V6 aplicada em **PostgreSQL 18.3 real**.
- Arte de 3508x2480 enviada e lida de volta: **SHA idêntico**, 31.234 bytes no
  `bytea`, e **nenhum arquivo novo no disco**.
- Importação verificada no PostgreSQL de desenvolvimento, que tinha 16 layouts
  apontando para disco: **15 artes migradas** (a 16ª já nascera no banco), e um
  certificado emitido com arte migrada gerou PDF válido de 191 KB.
- `/actuator/health`, `/liveness` e `/readiness` respondendo UP; `/actuator/env`
  e `/actuator` respondendo 401.
- Scripts do S2I no índice do Git como `100755` e com LF puro.

## Pendências

- **Em qual claim vem o CPF** — bloqueia o SSO real. Continua sendo a primeira
  pergunta do [briefing](../deploy/briefing-infraestrutura.md).
- Manifests do OpenShift (Deployment, Service, Route, ConfigMap, Secret) e o que
  a pipeline Tekton espera encontrar no repositório.
- O `state` do OIDC carrega um valor aleatório mas não é validado entre as duas
  requisições — fechar isso exige estado compartilhado entre réplicas.
- EGESP segue mockado.
- **O redesenho da interface nunca foi conferido no navegador.** Os testes
  passam e o build sai limpo, mas nenhuma tela foi vista rodando depois da
  reescrita do CSS. É verificação de outra natureza: alinhamento, contraste e
  quebra em telas estreitas não aparecem em teste automatizado.
- A nova tela de entrada (com o caminho do SSO e o do mock) não tem teste de
  frontend.
