# 2026-09-10 — O e-mail corporativo vira a chave do domínio

> Diário de implementação. A decisão e o porquê estão na DI-24
> (`specs/memory/decisoes-de-implementacao.md`); aqui fica o que mudou, onde, e
> o que ficou pendente.

## Origem

O primeiro login pelo SSO em homologação parou em "O login corporativo não
informou o CPF". O realm do tribunal publica `email`, `preferred_username` e
`name`, e nada de CPF. Pedido do usuário: identificar pelo e-mail corporativo.
Das duas leituras possíveis — só traduzir e-mail em CPF no login, ou fazer do
e-mail a chave do domínio — ficou a segunda, com o CPF mantido como campo
opcional.

## O que mudou

**Banco — migração 010** (`010-email-como-chave.sql`). Colunas de e-mail em
`administrador`, `magistrado_reconhecido`, `servidor_habilitado` e
`certificado_emitido` (`email_emissor`); chaves únicas refeitas sobre o e-mail;
CPF anulável e sem unicidade em todas, inclusive `usuario`. Linhas antigas sem
correspondência no cadastro de usuários recebem `<cpf>@cpf.invalid`.

**API.**
- `comum/Email` (normalizar, validar, exigir, mascarar) e `Cpf.opcional`.
- Identidade: `IdentidadeAutenticada`, `UsuarioAutenticado` e o JWT carregam o
  e-mail; `PapeisResolver` resolve por ele.
- SSO: `ClienteKeycloak` lê o e-mail dos claims de `goianao.sso.claims-email`
  (padrão `email,preferred_username`).
- Cadastro de usuários: e-mail obrigatório, único e imutável; CPF opcional (em
  branco na edição mantém o atual). `GOIANAO_SUPERADMIN_CPF` virou opcional.
- Magistrados: cadastro, conflito por edição e emissão por e-mail. A planilha
  passou a `email;nome;unidade;selo;cpf`; o formato antigo é recusado inteiro,
  com a explicação.
- Servidores habilitados: inclusão por e-mail; remoção pelo **id do item**
  (antes o CPF ia na URL); resposta com `email` só para quem edita e
  `emailMascarado` para os demais; semeadura conta `ignoradosSemEmail`.
- Mock: identidades de teste e EGESP falso passaram a e-mails no domínio
  reservado `tjgo.example`.

**Frontend.** Sessão e identidade por e-mail; login de teste lista e-mails;
formulários de usuário, magistrado e servidor pedem o e-mail corporativo e o
CPF como opcional; a lista de habilitados mostra e-mail (completo ou mascarado,
conforme a permissão) e remove por id; a semeadura avisa quantos vieram sem
e-mail.

**Specs.** Princípio 3 da constituição emendado com a justificativa; DI-24;
notas de emenda no topo das specs 001 e 004 a 009; variáveis de ambiente
(`OPENSHIFT_SSO_CLAIMS_EMAIL`, `OPENSHIFT_SSO_CLAIMS_CPF` obsoleta).

## Verificação

- API: 196 testes passando (eram 162). Os que tratavam o CPF como identidade
  foram traduzidos para o e-mail, sem perder a intenção; entraram testes novos
  para `Email`, remoção por id, e-mail mascarado para quem só consulta, planilha
  no formato antigo, CPF opcional e não único, e e-mail imutável.
- Frontend: `tsc` sem erros e 16 testes passando.
- **Migração 010 em PostgreSQL 18 real, sobre dados reais.** Numa cópia do
  banco local da validação de 01/09 (6 magistrados, 33 servidores, 3
  certificados, 2 administradores), a versão anterior aplicou 007–009 e a nova
  aplicou 010. Resultado: o magistrado cujo CPF batia com um usuário recebeu o
  e-mail dele nas duas edições, e os 3 certificados também — com os mesmos
  códigos de validação; as demais linhas receberam `<cpf>@cpf.invalid`; nenhum
  e-mail nulo; CPF anulável nas cinco tabelas; unicidades refeitas sobre o
  e-mail. A aplicação subiu em seguida com a validação de esquema do Hibernate,
  e a conferência pública achou o certificado antigo pelo código. O banco local
  foi devolvido ao estado original depois.

## Pendências

- **EGESP real precisa entregar o e-mail** de cada servidor. Sem ele, a pessoa
  fica fora da lista de habilitados — a semeadura avisa quantos.
- **Banco de desenvolvimento.** O H2 em `api/data/` guarda a carga de
  demonstração antiga, com CPFs; a migração converte essas linhas para
  `@cpf.invalid` e as identidades de teste novas não as encontram. Apagar
  `api/data/` e subir de novo regera a carga já com e-mails.
- **Homologação** não exige variável nova: o padrão de claims atende o realm.
  `OPENSHIFT_SSO_CLAIMS_CPF`, se existir lá, é ignorada.
