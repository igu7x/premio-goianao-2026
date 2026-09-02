# 2026-09-01 — Varredura completa e correções

Varredura de todos os fluxos e requisições do sistema, com correção do que
apareceu. Três frentes: sondagem da API (incluindo casos negativos e de
segurança), navegação da interface em cada perfil por um navegador real, e
revisão do código à luz do que os testes mostraram.

## Cobertura

| Frente | O que foi exercitado | Resultado |
|---|---|---|
| API — varredura 1 | autenticação, RBAC de cada perfil fora do seu escopo, edições, layouts, magistrados, servidores, as duas emissões, verificação pública, e sondagens (IDOR, path traversal, injeção, mass assignment, CORS, spoof de CPF no corpo) | 96 verificações |
| API — varredura 2 | multipart da arte (retrato, baixa resolução, proporção errada, mime inválido, não-imagem, área fora da arte, dimensão negativa), travamento de layout, importação CSV, mascaramento de CPF, dados extremos | 27 verificações |
| Interface | as nove telas, nos perfis administrador, magistrado, servidor, servidor sem vínculo e público — conferindo erros de página e de console em cada uma | 39 verificações |
| Suíte automatizada | backend + frontend | 121 + 8 testes |

## Defeitos encontrados e corrigidos

### 1. Toda requisição malformada devolvia 500 _(o mais grave)_

Id de rota não numérico, JSON quebrado, corpo ausente, valor fora de um enum,
método não aceito, rota inexistente, multipart sem a parte esperada,
content-type errado — **tudo** caía no tratador genérico e voltava como
`500 Internal Server Error`.

Dizia ao cliente que o servidor falhou quando quem errou foi a requisição, e
enchia o log de stack trace a cada erro de digitação. Um integrador do TJGO
tentando consumir a API não teria como distinguir bug do servidor de engano
próprio.

**Correção:** `TratadorDeErros` passou a tratar as exceções padrão do Spring MVC,
cada uma com o seu código (400, 405, 404, 415), no mesmo formato de corpo dos
demais erros. Quando o valor inválido é de um enum, a mensagem diz **qual campo**
e **quais valores são aceitos**.

### 2. CPF completo exposto a quem só consulta _(LGPD — 008/RNF-2)_

`ServidorHabilitadoResposta` devolvia sempre o CPF completo, além do mascarado.
Como qualquer magistrado pode **consultar** a lista de qualquer unidade, um
magistrado conseguia ler os CPFs dos servidores de unidades que não são dele.

**Correção:** o CPF completo só acompanha a resposta de quem pode **editar**
aquela lista — é ele que identifica o servidor na remoção. Para quem apenas
consulta vai somente o mascarado. Regressão coberta em `ServidorHabilitadoIT`.

### 3. Cabeçalho do código de validação invisível fora da mesma origem

O CORS expunha apenas `Content-Disposition`. O `X-Codigo-Validacao`, que a tela
usa para mostrar o código logo após a emissão, não estava na lista — em
desenvolvimento o proxy do Vite mascara isso, porque ali tudo é mesma origem,
mas num frontend servido de outro domínio a mensagem de sucesso sairia sem o
código.

**Correção:** cabeçalho acrescentado aos expostos.

### 4. Console do H2 liberado incondicionalmente

`/h2-console/**` estava em `permitAll` independentemente de o console estar
ligado. Hoje ele só é ativado no perfil `dev`, mas um ambiente que o ligasse por
engano o teria aberto a qualquer um.

**Correção:** a liberação passou a depender de `spring.h2.console.enabled`.

### 5. Editor de layout deixava editar o que não pode ser salvo

Com a edição publicada, os layouts estão travados (003/RF-8) — mas o editor
continuava oferecendo "Salvar layout", permitindo arrastar as caixas e digitar
coordenadas. O administrador ajustava tudo e só descobria no fim, com um 409.

**Correção:** o editor entra em modo somente-leitura de verdade — sem botão de
salvar, sem arrasto, sem punho de redimensionar, campos em leitura, e um aviso
no topo explicando o porquê. A pré-visualização continua disponível, porque é
leitura.

### 6. Download cancelado em alguns navegadores

`salvarArquivo` revogava a URL de objeto no mesmo instante do clique; alguns
navegadores ainda estão lendo o blob nesse momento e abortam o download.

**Correção:** a revogação passou a acontecer depois.

### 7. `GET /api/edicoes/vigente` devolvia `200` com corpo nulo

**Correção:** passou a devolver `204` quando ainda não há edição vigente.

## Conflito entre specs — decisão registrada

A varredura expôs uma contradição entre duas specs aprovadas:

- **004/RF-11:** a importação em lote está "Disponível só em **Rascunho**".
- **009/T-003:** diz que `criar`/**`importar`** passam a usar `podeIncluir`
  (rascunho **ou** vigente).

Prevaleceu a **004/RF-11**, por três razões: é um requisito numerado e explícito;
o §3 do plano da 009 — o documento técnico detalhado — troca a regra apenas em
`criar`, sem mencionar a importação; e a inclusão na vigente foi desenhada para o
caso pontual (um reconhecido que faltou), enquanto subir uma planilha inteira
numa edição já em uso é operação de outra escala.

O código está comentado com essa decisão e o caminho para reverter, caso o TJGO
prefira o contrário: trocar uma chamada e emendar a RF-11.

## O que foi verificado e está correto

Vale registrar o que a varredura confirmou, para não se retestar à toa:

- Nenhum IDOR: layout, magistrado e lista de outra edição devolvem 404, não os
  dados do vizinho.
- CPF enviado no corpo da emissão é ignorado — a identidade vem sempre do token.
- Mass assignment não passa: `status` e `vigente` enviados na criação são
  descartados e a edição nasce em rascunho.
- Path traversal na referência da arte é barrado pelo `ImageStorage`.
- A verificação pública não devolve CPF em nenhuma forma, e o limitador de taxa
  corta a varredura de códigos.
- CORS não libera origem desconhecida.
- Ressemear preserva os ajustes manuais, em ambas as direções.
- A regra do maior selo continua valendo depois de inclusões na vigente.

## Pontos deixados em aberto, de propósito

**`GET /api/auth/usuarios-mock` é público e devolve CPFs completos.** São CPFs
fictícios, e o endpoint existe apenas enquanto o SSO está mockado — é ele que
alimenta a tela de login desta fase. Desaparece junto com o mock. Registrado aqui
para que a troca pelo SSO real não o deixe para trás.
