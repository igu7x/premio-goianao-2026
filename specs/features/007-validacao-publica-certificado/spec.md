# Spec: Validação pública de certificado

- **ID:** 007-validacao-publica-certificado
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31

> Esta spec descreve **o quê** e **por quê**.

## 1. Problema / Necessidade

Cada certificado emitido traz um **código de validação** (texto e QR). Terceiros
(ex.: setor de RH, banca, cidadão) precisam **conferir a autenticidade** do
certificado sem precisar de login no sistema. Sem isso, o código impresso não
teria utilidade.

## 2. Objetivo

Oferecer uma página/serviço **público** que, a partir do código (digitado ou via
QR), informe se o certificado é **válido** e exiba seus dados essenciais.
Saberemos que deu certo quando qualquer pessoa, ao ler o QR ou digitar o código,
confirmar a autenticidade e ver nome, unidade, edição e selo.

## 3. Usuários / Personas

- **Verificador** (público, sem autenticação) — confere um certificado.

## 4. Histórias de usuário

- Como **verificador**, quero ler o QR do certificado e ver imediatamente se ele
  é autêntico e a quem pertence.
- Como **verificador**, quero digitar o código impresso para validar, caso não
  consiga ler o QR.

## 5. Requisitos funcionais

- **RF-1:** O sistema DEVE expor uma verificação **pública** (sem login) que
  recebe um **código de validação** e responde se há um certificado correspondente.
- **RF-2:** Quando o código é **válido**, o sistema DEVE exibir os dados do
  certificado: **nome do reconhecido**, **unidade**, **edição (ano)**, **selo**,
  **tipo** (magistrado/servidor) e **data de emissão**.
- **RF-3:** Quando o código **não existe**, o sistema DEVE responder de forma
  clara que **não foi encontrado** certificado válido para aquele código.
- **RF-4:** O **QR code** impresso no certificado DEVE codificar a **URL pública
  de verificação** contendo o código, de modo que lê-lo abra a verificação já
  preenchida; o **código em texto** também impresso permite verificação manual.
- **RF-5:** A verificação DEVE refletir o estado atual do registro de emissão
  (o código é estável; reemitir não invalida).

## 6. Requisitos não-funcionais

- **RNF-1:** Endpoint público; **não exige** autenticação.
- **RNF-2:** Não expor dados sensíveis além do necessário; o **CPF não é
  exibido** na verificação pública.
- **RNF-3:** Proteção contra abuso (rate limiting) e contra enumeração de códigos
  (códigos opacos/não sequenciais — ver feature 005).
- **RNF-4:** Disponibilidade adequada, pois é o ponto de conferência externo.

## 7. Critérios de aceitação

- **CA-1:** Dado um certificado emitido com código C, quando alguém acessa a
  verificação pública com C, então vê "válido" e os dados (nome, unidade, edição,
  selo, tipo, data).
- **CA-2:** Dado um código inexistente, quando alguém o verifica, então recebe
  mensagem de "não encontrado", sem vazar nenhum dado.
- **CA-3:** Dado o QR de um certificado, quando lido, então abre a URL pública de
  verificação já resolvendo o código C.
- **CA-4:** Dado um certificado reemitido, quando verificado pelo mesmo código C,
  então continua válido e com os mesmos dados.
- **CA-5:** Dada a verificação pública, quando consultada, então **não exibe
  nenhum CPF** do reconhecido.

## 8. Fora de escopo

- Emissão e geração do PDF (features 005/006).
- Configuração da posição do código/QR no layout (feature 003).
- Revogação/cancelamento de certificados (não previsto nesta fase).

## 9. Pontos em aberto

- **RESOLVIDO:** a verificação pública **não exibe CPF** (apenas nome, unidade,
  edição, selo, tipo e data) — minimização de dados pessoais (LGPD).
- [NEEDS CLARIFICATION: domínio/base da URL pública de verificação — depende da
  hospedagem; será configuração de ambiente. Adiado.]
