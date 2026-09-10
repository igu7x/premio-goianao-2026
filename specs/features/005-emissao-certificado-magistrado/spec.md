# Spec: Emissão de certificado — Magistrado

- **ID:** 005-emissao-certificado-magistrado
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31
- **Emenda (2026-09-10, DI-24):** onde esta spec diz que o SSO identifica o
  magistrado pelo CPF, leia-se **e-mail corporativo**.

> Esta spec descreve **o quê** e **por quê**. Inclui o motor de geração do
> certificado (composição nome+unidade sobre a arte → PDF), compartilhado com a
> feature 006.

## 1. Problema / Necessidade

O magistrado reconhecido precisa **emitir seu certificado** de cada unidade pela
qual foi reconhecido na edição vigente, com o selo correspondente, sem digitar
seus dados (já vêm do SSO) e sem escolher selo/unidade indevidos (já vêm do
cadastro do admin).

## 2. Objetivo

Após o login, mostrar ao magistrado os certificados que ele **pode** emitir
(uma opção por unidade reconhecida, com seu selo) e gerar o PDF preenchido com
seu nome e a unidade, usando o layout configurado. Saberemos que deu certo
quando ele baixar o certificado correto para cada unidade/selo.

## 3. Usuários / Personas

- **Magistrado** — emite seus próprios certificados.

## 4. Histórias de usuário

- Como **magistrado**, quero ver a lista de unidades pelas quais fui reconhecido
  na edição vigente, com o selo de cada uma, para emitir o certificado.
- Como **magistrado**, quero emitir e baixar o certificado de uma unidade, já
  preenchido com meu nome e a unidade, no layout do selo correto.

## 5. Requisitos funcionais

- **RF-1:** O sistema DEVE identificar o magistrado pelo **CPF autenticado**
  (SSO) e localizar seus reconhecimentos. Por padrão usa a **edição vigente**,
  mas DEVE permitir **selecionar qualquer edição publicada** em que ele tenha
  reconhecimento (para reemissão de certificados de edições anteriores).
- **RF-2:** O sistema DEVE listar, para o magistrado e para a edição selecionada,
  **uma opção de emissão por unidade reconhecida**, exibindo unidade e selo.
- **RF-3:** O sistema DEVE emitir o certificado de uma unidade escolhida usando o
  **layout** (edição × selo × tipo=MAGISTRADO) configurado na feature 003.
- **RF-4:** O sistema DEVE preencher automaticamente o **nome** do magistrado e a
  **unidade** selecionada no certificado. O nome impresso DEVE ser o do **cadastro
  de magistrados reconhecidos daquela edição** (feature 004) — **não** o nome
  vindo do SSO. O SSO identifica o emissor pelo CPF (RF-1); quem fornece o texto
  impresso é o cadastro. Como o cadastro é travado ao publicar a edição, a
  reemissão de uma edição anterior imprime sempre a mesma grafia, ainda que o nome
  no SSO mude depois. (Para o **servidor** a origem é outra — ver feature 006,
  RF-1 — e a assimetria é proposital.)
- **RF-5:** O sistema DEVE gerar o certificado em **PDF** (arte + texto sobreposto)
  disponível para download/visualização.
- **RF-6:** O sistema DEVE impedir emissão para unidade pela qual o magistrado
  **não** foi reconhecido.
- **RF-7:** O sistema DEVE impedir emissão se a edição-alvo estiver em
  **Rascunho** (não publicada) ou se **não houver layout** para o selo/tipo
  naquela edição. Emissão em edições **publicadas anteriores** é permitida.
- **RF-8:** O sistema DEVE permitir **reemitir** (gerar novamente) o certificado
  de uma unidade já emitida.
- **RF-9:** O sistema DEVE **registrar** cada emissão (quem, unidade, selo,
  edição, data/hora) para histórico/auditoria.
- **RF-10:** O sistema DEVE gerar um **código de validação único** por
  certificado e imprimi-lo na **área de código** do layout (texto e/ou QR),
  permitindo verificação posterior de autenticidade. Na **reemissão** da mesma
  unidade/edição, o código DEVE ser **estável** (o mesmo certificado mantém seu
  código).

## 6. Requisitos não-funcionais

- **RNF-1:** O magistrado só acessa os próprios certificados (autorização por CPF
  autenticado, não por parâmetro manipulável).
- **RNF-2:** O PDF gerado deve ser fiel à pré-visualização do layout (mesmo motor).
- **RNF-3:** Nome e unidade longos devem caber na área (ajuste automático).

## 7. Critérios de aceitação

- **CA-1:** Dado um magistrado reconhecido em unidade A/Ouro e B/Bronze na edição
  vigente, quando ele acessa, então vê duas opções: A (Ouro) e B (Bronze).
- **CA-1b:** Dado que ele foi reconhecido também numa edição anterior publicada,
  quando seleciona essa edição, então vê as opções e o layout **daquela** edição.
- **CA-2:** Dado que ele escolhe a unidade A, quando emite, então recebe um PDF
  com o layout Ouro/Magistrado, seu nome, "unidade A" e o **código de validação**
  nas posições do layout.
- **CA-2b:** Dado um magistrado cujo nome no cadastro da edição difere do nome
  devolvido pelo SSO, quando ele emite, então o PDF traz o nome do **cadastro**.
- **CA-3:** Dado um magistrado, quando tenta emitir para uma unidade não
  reconhecida (ex.: manipulando a requisição), então a emissão é negada.
- **CA-4:** Dada a ausência de layout para B/Bronze/Magistrado, quando ele tenta
  emitir B, então o sistema informa indisponibilidade e não gera PDF.
- **CA-5:** Dada uma edição em **Rascunho**, quando ele tenta emitir, então a
  emissão é bloqueada (edições publicadas anteriores **não** são bloqueadas).
- **CA-6:** Dado que ele já emitiu A, quando emite novamente, então um novo PDF é
  gerado e a emissão é registrada (reemissão).

## 8. Fora de escopo

- Emissão por servidor (feature 006).
- Configuração de layouts (feature 003).

## 9. Pontos em aberto

- **RESOLVIDO:** o PDF é gerado **sob demanda** a cada emissão/reemissão; o
  sistema guarda apenas **metadados** + `codigo_validacao`. A fidelidade da
  reemissão é garantida pelo travamento do layout ao publicar a edição.
- **RESOLVIDO:** o texto da unidade no certificado é o **nome salvo** (oriundo do
  EGESP, feature 004), impresso tal qual cadastrado.
- **RESOLVIDO:** o **nome do magistrado** impresso vem do **cadastro da edição**
  (feature 004), não do SSO (RF-4/CA-2b) — o SSO só identifica o CPF. No
  certificado de **servidor** (feature 006) o nome vem do SSO; ver princípio 3a da
  constituição.
