# 2026-09-09 — Confirmações do responsável pelo negócio

> Diário de implementação. Este registro não descreve código novo: registra
> **respostas do dono do negócio** a dúvidas que até aqui só tinham as specs
> herdadas como fonte. Fica aqui para que ninguém precise perguntar de novo.

## O magistrado não atribui selo a servidor

Perguntado se, na tela "Servidores da unidade", o magistrado seleciona o
servidor lotado e **indica qual prêmio ele ganhou**, a resposta foi:

> *"Indica o prêmio não."*
> *"[Ele] só controla a relação de servidores na unidade."*
> *"O prêmio é o mesmo q o magistrado ganhou para aquela unidade. Se o
> magistrado foi ouro lá, servidores pode emitir o de ouro."*

Isso **confirma** o que já estava implementado, sem alteração:

- `ServidorHabilitado` não tem campo de selo — só edição, unidade, CPF, nome,
  origem e auditoria. A lista responde *quem pode emitir*, nada mais.
- O selo do servidor é **derivado**, calculado na emissão a partir dos
  reconhecimentos daquela unidade naquela edição
  (`EmissaoServidorService.opcoes/emitir` → `magistrados.maiorSeloDaUnidade`).
  Nunca é gravado, portanto nunca fica defasado.
- A tela do magistrado só inclui e remove pessoas; a do servidor não deixa
  escolher selo — oferece uma opção por unidade, com o selo já determinado.

## O que a confirmação NÃO cobre

A frase do dono do negócio pressupõe **um** magistrado por unidade. O caso de
uma unidade reconhecida por **dois** magistrados com selos diferentes (que
existe: é o cenário montado de propósito em `DadosDemo`, 1ª Vara Cível com Ouro
de um e Bronze de outro) continua resolvido apenas pelas specs herdadas —
constituição, princípio 4 e 006/CA-1: **vale o maior selo**.

Está implementado assim e há teste cobrindo. Mas é uma decisão de negócio que
veio das specs sem confirmação de ninguém vivo — vale confirmar antes de entrar
em produção, porque a alternativa (o servidor escolher entre os selos, ou valer
o do magistrado que responde pela unidade) mudaria o PDF emitido.
