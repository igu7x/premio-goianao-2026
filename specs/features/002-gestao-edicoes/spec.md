# Spec: Gestão de edições do prêmio

- **ID:** 002-gestao-edicoes
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31

> Esta spec descreve **o quê** e **por quê**.

## 1. Problema / Necessidade

O prêmio Goianão acontece **anualmente**. Tudo no sistema (reconhecimentos,
layouts, emissões) é relativo a uma **edição** (ano). É preciso controlar quais
edições existem e qual é a **vigente** — a edição **padrão** (atual). A vigente
**não** bloqueia as demais: edições anteriores já publicadas continuam
disponíveis para **reemissão**, sempre com o layout e os dados **daquela**
edição. Não há "encerramento" que impeça emissão.

## 2. Objetivo

Permitir que o administrador crie e gerencie edições anuais e defina qual é a
edição vigente (padrão). Saberemos que deu certo quando toda emissão e todo
cadastro se ancorarem corretamente a uma edição, houver no máximo uma vigente
por vez, e edições anteriores continuarem emitíveis para reemissão.

## 3. Usuários / Personas

- **Administrador** — cria e gerencia edições.
- **Magistrado/Servidor** — consomem indiretamente: emitem por padrão na edição
  vigente, podendo selecionar uma edição anterior para reemitir.

## 4. Histórias de usuário

- Como **administrador**, quero criar a edição de um ano para começar a cadastrar
  reconhecimentos e layouts daquele ano.
- Como **administrador**, quero marcar uma edição como vigente (padrão) para que
  ela seja a opção pré-selecionada na emissão.
- Como **magistrado/servidor**, quero poder escolher uma edição anterior para
  reemitir um certificado antigo com o visual e os dados daquela época.

## 5. Requisitos funcionais

- **RF-1:** O sistema DEVE permitir ao administrador **criar** uma edição
  identificada pelo **ano** (e descrição opcional).
- **RF-2:** O sistema DEVE impedir edições com **ano duplicado**.
- **RF-3:** O sistema DEVE permitir **publicar** uma edição (sair de Rascunho),
  tornando-a disponível para emissão.
- **RF-3b:** O sistema só DEVE permitir **publicar** uma edição quando ela tiver
  os **8 layouts** (4 selos × 2 tipos) configurados (feature 003). Isso garante
  que qualquer selo emitível — inclusive os incluídos depois na vigente
  (feature 009) — tenha layout. A mensagem de erro DEVE listar as combinações
  pendentes.
- **RF-4:** O sistema DEVE permitir definir uma edição (publicada) como
  **vigente** (padrão).
- **RF-5:** O sistema DEVE garantir **no máximo uma** edição vigente por vez; ao
  tornar uma vigente, a anterior deixa de ser vigente, mas **permanece publicada
  e emitível** (reemissão).
- **RF-6:** O sistema NÃO DEVE bloquear emissão em edições anteriores já
  publicadas; toda edição publicada continua disponível para reemissão com seus
  próprios layout e dados.
- **RF-7:** O sistema DEVE permitir **listar** edições com seu status
  (Rascunho / Publicada) e indicar qual é a **vigente**.
- **RF-8:** O sistema DEVE permitir **editar** dados descritivos de uma edição.
- **RF-9:** O sistema DEVE expor qual é a **edição vigente** (padrão) e a lista
  de edições publicadas para as demais funcionalidades.

## 6. Requisitos não-funcionais

- **RNF-1:** Apenas administradores podem criar/editar/publicar/definir vigente.
- **RNF-2:** Mudar a vigente não pode alterar nada já emitido em nenhuma edição;
  cada certificado reflete o layout/dados da edição a que pertence.

## 7. Critérios de aceitação

- **CA-1:** Dado nenhum cadastro, quando o admin cria a edição 2026, então ela
  passa a existir com status **Rascunho** (não emite até ser promovida a Vigente).
- **CA-2:** Dada a edição 2026 já existente, quando o admin tenta criar outra
  2026, então a criação é rejeitada.
- **CA-3:** Dada a edição 2025 vigente, quando o admin torna a 2026 vigente,
  então 2026 fica vigente e 2025 deixa de ser vigente, mas **continua publicada**.
- **CA-4:** Dada a edição 2025 não mais vigente porém publicada, quando um usuário
  com direito nela seleciona-a, então consegue **reemitir** o certificado de 2025
  com o layout e os dados de 2025 (validado nas features 005/006).
- **CA-5:** Dada uma edição em **Rascunho**, quando alguém tenta emitir nela,
  então a emissão é bloqueada (só edições publicadas emitem).
- **CA-6:** Dado um não-administrador, quando tenta criar/publicar uma edição,
  então o acesso é negado.
- **CA-7:** Dada uma edição em Rascunho **sem os 8 layouts**, quando o admin tenta
  publicar, então a publicação é **bloqueada**, com a lista das combinações
  selo×tipo pendentes (RF-3b).

## 8. Fora de escopo

- Datas de abertura/fechamento automáticas por agendamento (pode ser manual).
- Conteúdo dos cadastros da edição (layouts e magistrados são outras features).

## 9. Pontos em aberto

- **RESOLVIDO:** a edição nasce em **Rascunho**; o admin a publica e pode
  promovê-la a vigente.
- **RESOLVIDO:** **não há encerramento bloqueante**. A vigente é apenas a edição
  padrão; edições anteriores publicadas permanecem disponíveis para **reemissão**
  com seus próprios layout/dados. (Logo, não há "reabrir".)
