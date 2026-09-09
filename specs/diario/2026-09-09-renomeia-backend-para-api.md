# 2026-09-09 — `backend/` passa a se chamar `api/`

## Por que

Pedido da infraestrutura do TJGO, que constrói e implanta o sistema. A pipeline
é Tekton, criada e mantida por eles, e a pasta de contexto do build é
configuração do lado deles — um repositório fora do padrão vira pergunta a cada
ambiente novo e a cada pessoa nova no time.

Atender custa pouco: **nenhum arquivo `.java` cita o nome da pasta**, porque os
pacotes são `br.jus.tjgo.goianao.*`. O que muda é caminho em documentação e
script.

Foi feito agora, e não depois, porque hoje ninguém além do autor clonou o
repositório, nenhuma pipeline aponta para caminho algum e não há branch de
terceiros para conflitar. Daqui a duas semanas o mesmo rename vira coordenação.

## O que mudou

- `git mv backend api` — o histórico é preservado; `git log` e `git blame`
  atravessam o rename.
- `pom.xml`: `artifactId` e `name` de `goianao-backend` para `goianao-api`. O
  jar passa a sair como `goianao-api-1.0.0.jar`.
- Caminhos em `README.md`, `.gitignore`, `docker-compose.yml`,
  `liberar-portas.ps1`, `specs/memory/decisoes-de-implementacao.md` e
  `specs/features/001-*/tasks.md`.

O `Dockerfile` não precisou de alteração de caminho: ele copia `pom.xml` e `src`
por caminho relativo e pega o artefato por `target/*.jar`, sem citar o nome.

A palavra "backend" continua no texto onde ela é o **conceito** — "a autorização
é sempre no backend" — e não o nome da pasta. As duas entradas antigas do
diário (01/09 e 02/09) também citam `backend/`: ficaram como estão, porque são
registro datado do que existia naquele dia. É esta entrada que explica a troca.

## Uma etapa intermediária que não sobreviveu

Antes do rename existiu, por algumas horas, uma **cópia** `api/` ao lado de
`backend/` (commit `04fe64f`). Foi descartada: duas árvores Java iguais no mesmo
repositório divergem no primeiro commit aplicado em apenas uma delas, e a
pipeline pode construir a errada sem que ninguém perceba.

## Perda: o banco H2 de desenvolvimento

Durante o rename o banco local de desenvolvimento foi **apagado**. A causa: um
`git mv backend api` executado enquanto a pasta `api/` ainda existia moveu
`backend/` para dentro dela, levando junto o `backend/data/` — que é ignorado
pelo git e, por isso, não foi restaurado pelo `git reset --hard` seguinte. O
`rm -rf api` de limpeza levou o banco.

Não havia backup. O que se recria sozinho na próxima subida em perfil `dev`:
edições, magistrados reconhecidos, unidades, artes de exemplo e listas de
servidores — tudo vem de `DadosDemo`, que roda com `goianao.dados-demo=true`. O
superadministrador volta pelas variáveis `GOIANAO_SUPERADMIN_*`.

O que **não** volta: qualquer coisa feita à mão pela interface — certificados
emitidos, ajustes de coordenada de layout, usuários cadastrados e designações de
responsável por unidade.

Lição registrada: antes de mover ou apagar uma pasta de projeto, verificar o que
há de **não versionado** dentro dela. `git status` não mostra o que está no
`.gitignore`, e é justamente ali que mora o banco de desenvolvimento.
