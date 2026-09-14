# Spec: Sincronização com o ConnectTJ (SIEDOS/EGESP)

- **ID:** 010-sincronizacao-siedos
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-09-14

> Esta spec descreve **o quê** e **por quê**. NÃO inclui linguagem, framework
> ou detalhes de implementação — isso vai no `plan.md`.

## 1. Problema / Necessidade

As unidades judiciárias e os servidores lotados em cada uma vêm hoje de um
**mock**: uma lista fixa escrita no código. Enquanto o prêmio não saiu de
homologação isso bastou, mas a emissão de verdade depende de dois dados que só
o tribunal tem — quais unidades existem e quem trabalha em cada uma.

O TJGO expõe esses dados na API corporativa **ConnectTJ**, que lê o SIEDOS
(organograma) e o cadastro de pessoal. Ela identifica unidade por **código**
(`cdgUnidade`) e pessoa por **matrícula** (`cdgOrdem`) — e o Goianão identifica
unidade por nome e pessoa por **e-mail corporativo** (DI-24). Ligar os dois é o
problema.

Há ainda um risco de confiança: uma carga automática que entre direto no
cadastro pode renomear unidade impressa em certificado ou tirar da lista quem
tinha direito de emitir. Quem administra o prêmio precisa **ver antes de
aplicar**.

## 2. Objetivo

Substituir o mock pela API corporativa, com **aprovação humana** para tudo que
altera cadastro, e manter atualizados os dados de quem entra no sistema. Deu
certo quando o administrador consegue montar uma edição inteira — unidades e
listas de habilitados — sem digitar nada e sem que nenhuma alteração aconteça
sem clique.

## 3. Usuários / Personas

- **Superadministrador:** confere e aprova as cargas na tela de sincronização.
- **Administrador do prêmio:** semeia a lista de habilitados de cada unidade.
- **Magistrado e servidor:** não usam a tela; são afetados por terem seus dados
  cadastrais atualizados no login.

## 4. Histórias de usuário

- Como **superadministrador**, quero comparar o que a API diz com o que está no
  banco para aprovar, item a item, o que deve ser criado ou corrigido.
- Como **administrador**, quero semear a lista de habilitados de uma unidade com
  gente de verdade, já com e-mail, para que cada servidor consiga emitir.
- Como **servidor**, quero que meu nome e minha lotação estejam certos sem
  precisar pedir a ninguém.

## 5. Requisitos funcionais

- **RF-1:** O sistema DEVE obter os dados de unidades e servidores da API
  corporativa, autenticando-se por conta própria.
- **RF-2:** O sistema DEVE continuar funcionando com dados mockados quando a
  integração não estiver configurada, sem deixar de subir.
- **RF-3:** O sistema DEVE oferecer ao superadministrador uma tela que compare,
  para uma unidade e uma edição, o que vem da API com o que está no banco,
  classificando cada item em: **sincronizado**, **desatualizado**, **só na API**
  ou **órfão** (só no banco).
- **RF-4:** Nenhuma alteração de cadastro DEVE ocorrer sem ação explícita na
  tela. A comparação é leitura.
- **RF-5:** O sistema DEVE permitir, a partir da comparação: cadastrar unidade
  que só existe na API; atualizar nome/comarca divergentes; incluir servidor na
  lista da edição; e desvincular (remoção lógica) servidor órfão.
- **RF-6:** A semeadura da lista de habilitados DEVE trazer o **e-mail** de cada
  servidor, resolvendo-o pela matrícula quando a listagem de lotados não o
  trouxer.
- **RF-7:** O sistema DEVE identificar a unidade primeiro pelo **código** do
  SIEDOS e, quando ele ainda não estiver gravado, pelo nome canônico —
  registrando o código no cadastro a partir daí.
- **RF-8:** Após o login pelo SSO, o sistema DEVE atualizar em segundo plano os
  dados cadastrais de quem entrou (nome, CPF, matrícula, login de rede e
  lotação), sem alterar o e-mail.
- **RF-9:** A rotina do login NÃO DEVE incluir, remover ou reativar ninguém na
  lista de habilitados de qualquer edição.
- **RF-10:** A tela DEVE exibir, para cada unidade, quem a API aponta como
  responsável, como **sugestão** — a designação continua sendo ato do
  superadministrador.

## 6. Requisitos não-funcionais

- **RNF-1:** As credenciais da API não ficam no repositório; vêm do ambiente.
- **RNF-2:** Falha da API não derruba o login nem a emissão: a rotina do login é
  assíncrona e silenciosa, e a emissão nunca consulta a API (princípio 3b).
- **RNF-3:** A comparação de uma unidade com até ~200 servidores DEVE responder
  em tempo de tela, sem repetir chamadas já feitas na mesma operação.
- **RNF-4:** E-mail, CPF e matrícula são dados pessoais: seguem a regra da
  DI-10 — completos só para quem pode editar aquela lista, mascarados para os
  demais, e nunca em URL.

## 7. Critérios de aceitação

- **CA-1:** Dado que a integração não está configurada, quando o sistema sobe,
  então ele usa o mock e a tela de sincronização diz que a integração está
  desligada.
- **CA-2:** Dado um código de unidade, quando o superadministrador compara,
  então vê as unidades da hierarquia e os servidores lotados classificados nas
  quatro categorias, sem que nada tenha sido gravado.
- **CA-3:** Dada uma unidade que só existe na API, quando o superadministrador
  clica em cadastrar, então ela passa a existir no banco com nome, comarca e
  código.
- **CA-4:** Dado um servidor lotado na API cuja listagem não traz e-mail, quando
  ele é incluído na lista da edição, então o e-mail é buscado pela matrícula e
  gravado.
- **CA-5:** Dado um servidor ativo na lista da edição que não veio da API para
  aquela unidade, quando o superadministrador desvincula, então a remoção é
  lógica e o histórico é preservado.
- **CA-6:** Dado um servidor órfão cuja inclusão foi **manual**, quando a tela o
  exibe, então o padrão é mantê-lo, com aviso de que foi ajuste humano.
- **CA-7:** Dado um usuário que entra pelo SSO, quando o login conclui, então
  nome, CPF, matrícula, login de rede e lotação são atualizados e o e-mail
  permanece o mesmo.
- **CA-8:** Dado que a API está fora do ar, quando alguém entra pelo SSO, então
  o login conclui normalmente e a falha fica só no log.
- **CA-9:** Dado um token expirado, quando o sistema chama a API, então ele
  renova o token uma vez e repete a chamada, sem entrar em laço.

## 8. Fora de escopo

- Importar magistrados reconhecidos: quem venceu é cadastro do administrador
  (constituição, princípio 2).
- Criar unidades ou habilitar servidores automaticamente, sem clique.
- Sincronização agendada (cron). Esta feature cobre a tela e o login.
- Estagiários, terceirizados e aposentados.

## 9. Pontos em aberto

- [NEEDS CLARIFICATION: qual realm e qual client emitem o token da API — o
  exemplo recebido cita `DG-TST`, e o sistema usa `tjgo.gov-tst` hoje]
- [NEEDS CLARIFICATION: endereço da API em produção]
- [NEEDS CLARIFICATION: código da unidade raiz a varrer para chegar às unidades
  judiciárias do prêmio]
- [NEEDS CLARIFICATION: o login de rede é sempre o prefixo do e-mail, inclusive
  para magistrados]
