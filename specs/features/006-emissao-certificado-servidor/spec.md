# Spec: Emissão de certificado — Servidor

- **ID:** 006-emissao-certificado-servidor
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31

> Esta spec descreve **o quê** e **por quê**. A elegibilidade do servidor vem da
> **lista de servidores habilitados** (feature 008), não de consulta ao EGESP na
> emissão. Inclui a regra do maior selo.

## 1. Problema / Necessidade

O servidor de uma unidade reconhecida também pode emitir o certificado. Como o
sistema permite **reemitir edições anteriores**, a elegibilidade **não** pode
depender da lotação atual (que pode ter mudado). Por isso, a elegibilidade é
determinada pela **lista de servidores habilitados** por unidade × edição
(feature 008), um snapshot semeado do EGESP e mantido pelo admin/magistrado. Se o
servidor consta na lista de uma unidade reconhecida e essa unidade tem **selos
diferentes**, vale o **maior** (Diamante > Ouro > Prata > Bronze).

## 2. Objetivo

Permitir que o servidor habilitado emita (ou reemita) o certificado da(s) sua(s)
unidade(s) reconhecida(s) na edição escolhida, aplicando a regra do maior selo e
gerando o PDF preenchido com nome, unidade e código de validação. Saberemos que
deu certo quando um servidor presente na lista baixar o certificado com o selo
correto (o maior), inclusive para edições anteriores.

## 3. Usuários / Personas

- **Servidor** — emite o certificado da(s) sua(s) unidade(s) habilitada(s).

## 4. Histórias de usuário

- Como **servidor**, quero ver as unidades/edições em que estou habilitado, sem
  precisar informar nada, para emitir meu certificado.
- Como **servidor**, quero emitir o certificado da minha unidade com o maior selo
  que ela recebeu, já com meu nome preenchido.
- Como **servidor**, quero reemitir um certificado de uma edição anterior com o
  visual e os dados daquela época.

## 5. Requisitos funcionais

- **RF-1:** O sistema DEVE identificar o servidor pelo **CPF autenticado** (SSO).
  O **nome impresso** no certificado DEVE ser o **nome vindo do SSO**; apenas na
  ausência dele usa-se o nome salvo na lista de habilitados (feature 008).
  Assimetria proposital com a feature 005: o nome do **magistrado** vem do
  **cadastro da edição** (curadoria do administrador, travada ao publicar),
  enquanto o do **servidor** vem do **SSO**, porque a lista da 008 registra *quem
  pode emitir* (CPF×unidade) e não a grafia oficial do nome — ali o nome é só um
  rótulo semeado do EGESP. Ver princípio 3a da constituição.
- **RF-2:** O sistema DEVE determinar as opções emitíveis listando as **unidades
  reconhecidas** (em edições publicadas) cuja **lista de servidores habilitados**
  (feature 008) **contém o CPF** do servidor. A emissão **não** consulta o EGESP.
- **RF-3:** Por padrão, o contexto é a **edição vigente**; o servidor DEVE poder
  **selecionar uma edição publicada anterior** em que esteja habilitado, para
  **reemitir** com o layout/dados daquela edição.
- **RF-4:** Quando a unidade tiver **selos diferentes** (reconhecida por mais de
  um magistrado), o sistema DEVE selecionar o **maior selo**
  (Diamante > Ouro > Prata > Bronze) **daquela edição**.
- **RF-5:** O sistema DEVE emitir o certificado usando o **layout** (edição ×
  maior selo × tipo=SERVIDOR) configurado na feature 003.
- **RF-6:** O sistema DEVE preencher **nome do servidor** e **unidade**
  automaticamente e gerar **PDF**.
- **RF-7:** O sistema DEVE **negar** a emissão se o CPF do servidor **não constar
  na lista habilitada** da unidade na edição-alvo.
- **RF-8:** O sistema DEVE bloquear emissão se a edição-alvo estiver em
  **Rascunho** ou se **não houver layout** para o selo/tipo naquela edição.
  Edições publicadas anteriores permanecem emitíveis (reemissão).
- **RF-9:** O sistema DEVE **registrar** cada emissão (quem, unidade, selo,
  edição, data/hora) e gerar um **código de validação único** por certificado,
  impresso na área de código do layout (texto e/ou QR). Na **reemissão** do mesmo
  certificado (mesma edição/unidade/pessoa), o código DEVE ser **estável**.

## 6. Requisitos não-funcionais

- **RNF-1:** O servidor só emite certificados das unidades em que está
  **habilitado** (lista da feature 008), nunca de unidade informada manualmente.
- **RNF-2:** A emissão **não** depende de disponibilidade do EGESP (lê a lista
  persistida), o que também garante estabilidade da reemissão de edições antigas.
- **RNF-3:** PDF fiel ao layout (mesmo motor das features 003/005).

## 7. Critérios de aceitação

- **CA-1:** Dado um servidor presente na lista habilitada da unidade A na edição
  vigente, e A reconhecida como Bronze (por um magistrado) e Ouro (por outro),
  quando ele emite, então recebe o certificado com layout **Ouro/Servidor**
  (maior selo).
- **CA-2:** Dado um servidor **não** presente em nenhuma lista habilitada de
  unidade reconhecida, quando ele acessa, então não vê opções e a emissão é
  negada.
- **CA-3:** Dado o nome do servidor (SSO), quando ele emite, então o PDF traz seu
  nome, a unidade A e o **código de validação** nas posições do layout.
- **CA-3b:** Dado um servidor cujo nome no SSO difere do nome semeado na lista de
  habilitados, quando ele emite, então o PDF traz o nome do **SSO**.
- **CA-4:** Dado um servidor habilitado em A e em B, quando ele acessa, então vê
  **uma opção por unidade** (A e B), cada uma com o maior selo respectivo.
- **CA-5:** Dada a ausência de layout para o maior selo/Servidor, quando ele
  tenta emitir, então o sistema informa indisponibilidade.
- **CA-6:** Dada uma edição em **Rascunho**, quando ele tenta emitir, então a
  emissão é bloqueada (edições publicadas anteriores não são bloqueadas).
- **CA-7:** Dado que ele constava na lista habilitada de A numa edição anterior
  publicada, quando seleciona essa edição, então reemite com o maior selo e o
  layout **daquela** edição.

## 8. Fora de escopo

- Emissão por magistrado (feature 005).
- Configuração de layouts (feature 003) e cadastro de reconhecidos (feature 004).
- Gestão da lista de servidores habilitados (feature 008).

## 9. Pontos em aberto

- **RESOLVIDO:** a elegibilidade vem da **lista de servidores habilitados**
  (feature 008), não de consulta ao EGESP na emissão — o que sustenta a reemissão
  de edições anteriores.
- **RESOLVIDO:** múltiplas unidades → **uma opção de emissão por unidade
  habilitada**, cada uma com o maior selo daquela unidade.
