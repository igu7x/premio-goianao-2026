# Spec: Cadastro de magistrados reconhecidos

- **ID:** 004-cadastro-magistrados-reconhecidos
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31
- **Emenda (2026-09-10, DI-24):** o magistrado é identificado pelo **e-mail
  corporativo**, e não pelo CPF — no cadastro, na unicidade por edição e na
  planilha (`email;nome;unidade;selo;cpf`, com o CPF opcional por último). O CPF
  continua existindo como dado opcional, só informativo.

> Esta spec descreve **o quê** e **por quê**. Inclui a seleção de unidades
> judiciárias a partir da lista do EGESP, base para os reconhecimentos.

## 1. Problema / Necessidade

O sistema só sabe quem pode emitir certificado porque o administrador **cadastra
os magistrados reconhecidos** de cada edição, indicando por quais **unidades**
foram reconhecidos e o **selo** de cada uma. Esse cadastro é a fonte da verdade:
ele habilita a emissão dos magistrados (feature 005) e identifica as **unidades
reconhecidas**, que habilitam a emissão dos servidores (feature 006).

As unidades **não** são digitadas livremente: o sistema **busca no EGESP** a
lista de unidades e o administrador **seleciona** dentre elas. O **nome da
unidade, exatamente como veio do EGESP**, é o que fica salvo. Isso garante que,
na feature 006, o nome da lotação retornado pelo EGESP para o servidor seja
**idêntico** ao nome reconhecido salvo no banco — o **nome é a chave de
casamento** entre as duas integrações.

## 2. Objetivo

Permitir ao administrador registrar, na edição vigente, cada magistrado
reconhecido (CPF, nome) e seus reconhecimentos (unidade + selo), possivelmente
vários por magistrado e com selos diferentes. Saberemos que deu certo quando o
conjunto de reconhecimentos for suficiente para que magistrados e servidores
emitam corretamente.

## 3. Usuários / Personas

- **Administrador** — realiza o cadastro.
- (Consumidores indiretos: features 005 e 006.)

## 4. Histórias de usuário

- Como **administrador**, quero cadastrar um magistrado reconhecido com CPF e
  nome para a edição vigente.
- Como **administrador**, quero associar a esse magistrado uma ou mais unidades,
  cada uma com seu selo, pois ele pode ser bronze por uma e diamante por outra.
- Como **administrador**, quero selecionar a unidade a partir da lista oficial do
  EGESP para que o nome salvo seja idêntico ao que o EGESP informará na lotação
  do servidor.

## 5. Requisitos funcionais

### Unidades (origem EGESP)
- **RF-1:** O sistema DEVE obter a lista de **unidades judiciárias do EGESP** e
  permitir ao administrador **selecionar** dentre elas; o **nome da unidade,
  exatamente como veio do EGESP**, é o que fica salvo, servindo para **identificar
  a unidade** ao semear a lista de servidores habilitados (feature 008). O
  administrador **não** digita unidades livremente.

### Magistrados reconhecidos (por edição)
- **RF-2:** O sistema DEVE permitir cadastrar um **magistrado reconhecido** com
  **CPF** e **nome**, vinculado a uma **edição**.
- **RF-3:** O sistema DEVE permitir associar ao magistrado **um ou mais
  reconhecimentos**, cada um com **unidade** e **selo** (Bronze/Prata/Ouro/Diamante).
- **RF-4:** O sistema DEVE permitir que o mesmo magistrado tenha **selos
  diferentes** em unidades diferentes.
- **RF-5:** O sistema DEVE impedir reconhecimento **duplicado** para o mesmo
  (magistrado, unidade, edição) — uma unidade tem um selo por magistrado/edição.
- **RF-6:** O sistema DEVE validar o **CPF** (formato) e exigir nome.
- **RF-7:** O sistema DEVE permitir **editar e remover** magistrados e
  reconhecimentos enquanto a edição estiver em **Rascunho** (ao publicar, o
  cadastro é travado para preservar a fidelidade das reemissões). _Exceção:_ a
  **inclusão** (aditiva) de novos magistrados/reconhecimentos na **edição
  vigente** é permitida pela **feature 009** — editar/remover continua só em
  Rascunho.
- **RF-8:** O sistema DEVE permitir **listar/consultar** os magistrados e
  reconhecimentos de uma edição.
- **RF-9:** O sistema DEVE expor, para uma edição, o conjunto de **unidades
  reconhecidas** (pelo **nome** salvo do EGESP) e, por unidade, os selos
  atribuídos (insumo para a regra do maior selo da feature 006).
- **RF-10:** Uma mesma unidade pode ser reconhecida por **mais de um magistrado**
  na mesma edição, possivelmente com **selos distintos**.
- **RF-11:** O sistema DEVE permitir **importação em lote** (planilha/CSV) dos
  reconhecidos, com colunas **CPF, nome, unidade, selo**. A importação DEVE:
  validar cada linha (CPF, selo válido, **nome de unidade existente no EGESP**),
  agrupar múltiplas linhas do mesmo CPF em um magistrado com vários
  reconhecimentos, e produzir um **relatório de erros** por linha sem abortar as
  válidas (ou modo "tudo-ou-nada", conforme RNF). Disponível só em **Rascunho**.

## 6. Requisitos não-funcionais

- **RNF-1:** Apenas administradores realizam estes cadastros.
- **RNF-2:** CPF é dado sensível; tratar conforme proteção de dados.
- **RNF-3:** A importação em lote DEVE reportar erros por linha de forma legível
  e não deixar o cadastro em estado parcial inconsistente.

## 7. Critérios de aceitação

- **CA-1:** Dado a edição vigente, quando o admin cadastra o magistrado (CPF X,
  nome Y) com unidade A/Ouro e unidade B/Bronze, então ambos reconhecimentos
  ficam registrados para ele.
- **CA-2:** Dado o magistrado já com unidade A/Ouro, quando o admin tenta
  adicionar unidade A novamente, então é rejeitado (RF-5).
- **CA-3:** Dada a unidade A reconhecida por um magistrado como Bronze e por
  outro como Ouro, quando consultamos as unidades reconhecidas, então A aparece
  com os selos {Bronze, Ouro} (insumo do maior selo).
- **CA-4:** Dado um CPF inválido, quando o admin tenta cadastrar, então a
  operação é rejeitada com erro de validação.
- **CA-5:** Dado um não-administrador, quando tenta cadastrar, então o acesso é
  negado.
- **CA-6:** Dada uma planilha com 3 linhas do CPF X (unidades A/Ouro, B/Bronze e
  uma linha com unidade inexistente no EGESP), quando o admin importa, então o
  magistrado X é criado com A/Ouro e B/Bronze e a 3ª linha aparece no **relatório
  de erros** (unidade não encontrada).

## 8. Fora de escopo

- Emissão de certificados (features 005/006).
- Determinar quem venceu (entrada manual do admin — princípio 2 da constituição).

## 9. Pontos em aberto

- **RESOLVIDO:** a unidade é **selecionada da lista do EGESP** (não há texto
  livre); salva-se o **nome exatamente como veio do EGESP**, que é a **chave de
  casamento** com a lotação do servidor na feature 006.
- **RESOLVIDO:** **haverá importação em lote (CSV/planilha)** nesta fase, com
  colunas CPF/nome/unidade/selo e relatório de erros por linha (RF-11).
- **RESOLVIDO:** após registrar os reconhecimentos, um **passo posterior** semeia
  a **lista de servidores habilitados** por unidade (feature **008**), que passa
  a ser a base da elegibilidade do servidor (não mais a lotação ao vivo).
- **RESOLVIDO:** o nome da unidade é guardado cru (do EGESP) e comparado de forma
  **canônica** (trim + caixa + acentos). Observação: com a feature 008, essa
  comparação é usada para **identificar a unidade ao semear** a lista, e não mais
  na emissão do servidor.
- [NEEDS CLARIFICATION: o EGESP expõe endpoints de **listagem de unidades** e de
  **servidores por unidade**? (dependência externa; mock nesta fase). Adiado.]
