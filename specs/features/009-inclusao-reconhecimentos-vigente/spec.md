# Spec: Inclusão de reconhecimentos na edição vigente

- **ID:** 009-inclusao-reconhecimentos-vigente
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31

> Esta spec descreve **o quê** e **por quê**. NÃO inclui linguagem/framework.

## 1. Problema / Necessidade

Hoje o cadastro de magistrados reconhecidos e seus reconhecimentos (feature 004)
só é editável enquanto a edição está em **Rascunho**; ao publicar, trava
(constituição, princípio 5). Na prática, porém, surgem casos legítimos de
**inclusão tardia** já com a edição vigente: um magistrado reconhecido que faltou
no cadastro, ou uma **nova unidade** pela qual um magistrado já cadastrado também
foi reconhecido. Bloquear isso obriga a recriar a edição ou ignorar o
reconhecimento — ambos ruins.

A chave é distinguir **incluir** (aditivo) de **editar/remover** (destrutivo):
**adicionar** novos magistrados/reconhecimentos **não altera** nenhum certificado
já emitido — apenas habilita novos. Já editar ou remover o que existe quebraria a
fidelidade das reemissões. Portanto, inclusões aditivas podem ser permitidas na
edição vigente.

## 2. Objetivo

Permitir que o **administrador**, **na edição vigente**, adicione um **novo
magistrado reconhecido** e/ou **um novo reconhecimento (unidade + selo)** a um
magistrado já cadastrado — sem poder editar ou remover os existentes. Saberemos
que deu certo quando o novo reconhecido conseguir emitir na edição vigente e a
nova unidade ficar disponível para a lista de servidores (feature 008), sem afetar
o que já foi emitido.

## 3. Usuários / Personas

- **Administrador** — realiza as inclusões.
- (Consumidores indiretos: emissão 005/006 e lista de servidores 008.)

## 4. Histórias de usuário

- Como **administrador**, quero adicionar um magistrado reconhecido que faltou,
  mesmo com a edição já vigente, para que ele possa emitir seu certificado.
- Como **administrador**, quero registrar uma **nova unidade** pela qual um
  magistrado já cadastrado foi reconhecido, sem reabrir/recriar a edição.

## 5. Requisitos funcionais

- **RF-1:** Na **edição vigente** (publicada e marcada como vigente), o sistema
  DEVE permitir **adicionar um novo magistrado reconhecido** (CPF, nome e um ou
  mais reconhecimentos unidade+selo).
- **RF-2:** Na edição vigente, o sistema DEVE permitir **adicionar um novo
  reconhecimento (unidade + selo)** a um magistrado **já cadastrado** naquela
  edição.
- **RF-3:** As inclusões DEVEM ser **estritamente aditivas**: o sistema NÃO DEVE
  permitir **editar** nem **remover** magistrados ou reconhecimentos existentes
  em edição publicada (preserva a fidelidade dos certificados já emitidos).
- **RF-4:** A unidade DEVE ser **selecionada da lista do EGESP** (mesma regra da
  feature 004), salvando o **nome cru** do EGESP.
- **RF-5:** O sistema DEVE impedir duplicidade: um magistrado com **mesmo CPF** já
  existente na edição não é recriado (deve-se usar a inclusão de reconhecimento);
  um reconhecimento de **unidade já existente** para o magistrado é rejeitado.
- **RF-6:** Ao incluir um reconhecimento de uma unidade ainda **não reconhecida**
  na edição, essa unidade DEVE passar a ser **gerenciável na lista de servidores
  habilitados** (feature 008) sem etapa adicional.
- **RF-7:** O comportamento existente é preservado: em **Rascunho**, inclusões
  continuam permitidas; em edições **publicadas porém NÃO vigentes** (anteriores),
  inclusões DEVEM ser **bloqueadas** (permanecem congeladas).
- **RF-8:** As operações DEVEM exigir perfil **Administrador**.
- **RF-9:** Após adicionar um novo magistrado, seu CPF DEVE passar a ter o papel
  **Magistrado** (já automático pelo lookup, feature 004) e poder **emitir** na
  edição vigente.
- **RF-10:** Toda edição vigente DEVE ter **os 8 layouts** (4 selos × 2 tipos)
  configurados — garantido por uma **pré-condição de publicação** (feature 002):
  uma edição só pode ser **publicada** se tiver os 8 layouts. Assim, qualquer
  inclusão (RF-1/RF-2) sempre encontra layout disponível para emissão.

## 6. Requisitos não-funcionais

- **RNF-1:** As inclusões NÃO podem alterar nenhum certificado já emitido; apenas
  habilitam novas emissões.
- **RNF-2:** A unicidade (edição+CPF; magistrado+unidade) DEVE ser garantida.

## 7. Critérios de aceitação

- **CA-1:** Dada a edição 2026 **vigente**, quando o admin adiciona o magistrado
  (CPF X, unidade A/Ouro), então X passa a constar e pode emitir A/Ouro.
- **CA-2:** Dado um magistrado já cadastrado na vigente, quando o admin adiciona a
  unidade B/Prata a ele, então B vira reconhecimento dele e unidade reconhecida
  da edição.
- **CA-3:** Dado um magistrado com a unidade A, quando o admin tenta adicionar A
  novamente, então é rejeitado (409).
- **CA-4:** Dada uma edição publicada **não vigente** (2025), quando o admin tenta
  adicionar magistrado/reconhecimento, então é **bloqueado**.
- **CA-5:** Dado que o admin incluiu a unidade B (nova) na vigente, quando acessa a
  lista de servidores, então consegue **semear/gerenciar** B (feature 008).
- **CA-6:** Dado um não-administrador, quando tenta incluir, então **403**.
- **CA-7:** Dada uma edição em **Rascunho**, as inclusões continuam funcionando
  (regressão da feature 004).
- **CA-8:** Dada uma edição em Rascunho **sem os 8 layouts**, quando o admin tenta
  **publicar**, então é bloqueado (pré-condição de publicação — feature 002).

## 8. Fora de escopo

- **Editar/remover** magistrados ou reconhecimentos existentes em edição publicada
  (continua restrito a Rascunho — feature 004).
- **Adicionar/alterar layouts** da edição publicada (permanecem travados na 003).
- Reabrir/encerrar edições.

## 9. Pontos em aberto

- **RESOLVIDO:** o problema de "selo sem layout" é **prevenido na origem** — uma
  edição só pode ser **publicada** quando tiver os **8 layouts** (selo×tipo)
  configurados (pré-condição na feature 002, RF-10/CA-8). Logo, toda edição
  vigente tem layout para qualquer selo incluído. (Não há inclusão de layout na
  vigente; layouts seguem travados após publicar.)
- **RESOLVIDO:** quando uma inclusão **eleva o maior selo** de uma unidade, o novo
  maior selo vale para as emissões seguintes — o maior selo é **calculado na
  emissão** e reflete o estado atual da edição vigente ("viva").
