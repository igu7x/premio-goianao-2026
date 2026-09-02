# Spec: Gestão da lista de servidores habilitados por unidade

- **ID:** 008-lista-servidores-habilitados
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31

> Esta spec descreve **o quê** e **por quê**.

## 1. Problema / Necessidade

O servidor emite o certificado da sua unidade reconhecida. Como o sistema
permite **reemitir certificados de edições anteriores**, não é possível validar a
lotação do servidor **ao vivo** no momento da emissão — a lotação dele pode ter
mudado desde aquela edição. É preciso registrar, **por edição**, **quem** estava
habilitado em cada unidade reconhecida: uma **lista de servidores habilitados**
(snapshot), semeada a partir do EGESP e ajustável.

## 2. Objetivo

Manter, para cada **unidade reconhecida × edição**, a lista de servidores
habilitados a emitir o certificado daquela unidade. A lista é semeada do EGESP e
pode ser editada (incluir/remover). Saberemos que deu certo quando a emissão do
servidor (feature 006) puder decidir a elegibilidade apenas consultando essa
lista, inclusive para edições anteriores.

## 3. Usuários / Personas

- **Administrador** — semeia e edita a lista de qualquer unidade/edição.
- **Magistrado** — edita a lista das **suas** unidades, apenas quando a edição é
  **vigente**.

## 4. Histórias de usuário

- Como **administrador**, quero, após cadastrar os reconhecimentos, buscar no
  EGESP os servidores de cada unidade reconhecida para já formar a lista.
- Como **administrador**, quero incluir/remover servidores da lista (inclusive de
  edições anteriores) para corrigir omissões ou enganos.
- Como **magistrado**, quero ajustar a lista de servidores da minha unidade na
  edição vigente, para garantir que minha equipe consiga emitir.

## 5. Requisitos funcionais

- **RF-1:** Para cada **unidade reconhecida** numa edição (feature 004), o sistema
  DEVE permitir **semear** a lista de servidores habilitados buscando no **EGESP**
  os servidores lotados naquela unidade, salvando um **snapshot** (CPF e nome).
- **RF-2:** Cada item da lista DEVE conter **CPF** e **nome** do servidor e a
  **origem** (EGESP ou inclusão manual).
- **RF-3:** O **administrador** DEVE poder **incluir** e **remover** servidores da
  lista de qualquer unidade reconhecida, em **qualquer edição publicada**.
- **RF-4:** O **magistrado** DEVE poder **incluir** e **remover** servidores
  **somente** das unidades pelas quais **ele** foi reconhecido e **somente quando
  a edição for vigente**. Em edição não vigente, apenas o administrador edita.
- **RF-5:** A lista é por **(edição, unidade)**; um mesmo servidor pode constar em
  várias unidades/edições. O sistema DEVE impedir **CPF duplicado** na mesma
  (edição, unidade).
- **RF-6:** O sistema DEVE permitir **consultar** a lista de uma unidade/edição.
- **RF-7:** Esta lista é a **fonte de verdade da elegibilidade do servidor** na
  emissão (feature 006); a emissão NÃO consulta o EGESP ao vivo.
- **RF-8:** Inclusões/remoções DEVEM ser **auditáveis** (quem alterou e quando),
  preservando o histórico mínimo para rastreio.

## 6. Requisitos não-funcionais

- **RNF-1:** Autorização estrita por papel e por escopo (magistrado só nas suas
  unidades e só na edição vigente).
- **RNF-2:** CPF é dado sensível (LGPD); tratar com cuidado em telas e logs.
- **RNF-3:** A semeadura deve lidar com unidades grandes (muitos servidores) sem
  travar a interface.

## 7. Critérios de aceitação

- **CA-1:** Dada uma unidade A reconhecida na edição vigente, quando o admin
  semeia a lista, então os servidores lotados em A (segundo o EGESP) entram na
  lista com CPF e nome.
- **CA-2:** Dado o admin, quando ele inclui um servidor manualmente na lista de A,
  então o servidor passa a constar com origem "manual".
- **CA-3:** Dado um magistrado reconhecido na unidade A na **edição vigente**,
  quando ele remove um servidor da lista de A, então a alteração é aceita.
- **CA-4:** Dado um magistrado e uma **edição não vigente**, quando ele tenta
  editar a lista, então o acesso é negado (apenas admin).
- **CA-5:** Dado um magistrado, quando ele tenta editar a lista de uma unidade
  pela qual **não** foi reconhecido, então o acesso é negado.
- **CA-6:** Dado um CPF já presente na lista de (edição, unidade), quando se tenta
  incluí-lo de novo, então é rejeitado (RF-5).

## 8. Fora de escopo

- Emissão do certificado em si (feature 006).
- Cadastro de magistrados/reconhecimentos (feature 004).
- Sincronização contínua com o EGESP (a semeadura é pontual; ajustes são manuais).

## 9. Pontos em aberto

- **RESOLVIDO:** ressemear **mescla** — adiciona novos servidores do EGESP e
  **preserva** as inclusões/remoções manuais já feitas (não desfaz ajustes).
- [NEEDS CLARIFICATION: o EGESP expõe "servidores por unidade"? (dependência
  externa; mock nesta fase). Adiado.]
