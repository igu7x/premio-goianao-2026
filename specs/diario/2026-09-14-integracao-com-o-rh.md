# 2026-09-14 — Integração com o RH (ConnectTJ/SIEDOS)

> Diário de implementação. A decisão e o porquê estão na DI-25; a spec, na
> feature 010. Aqui fica o que foi feito, o que a API respondeu e o que falta.

## O que a API respondeu (lida do Swagger, não deduzida)

`https://connecttj-api-stag.tjgo.jus.br/swagger-ui/index.html` — a especificação
em `/v3/api-docs` respondeu 200 e listou os endpoints. Duas descobertas mudaram
o plano:

1. **Existe `GET /api/v1/servidores/buscar-por-matricula`**, e ele devolve
   `endEmail`. Era a pergunta mais importante: sem ela, os lotados de uma
   unidade chegariam sem e-mail, e e-mail é a chave da pessoa aqui (DI-24). Com
   ela, a semeadura resolve o e-mail de cada matrícula e a lista nasce completa.
2. **Tudo exige Bearer**: sem token, os endpoints respondem 401 até em
   homologação. Então a integração só liga quando o client existir.

A API também tem `buscar-por-cpf`, `buscar-unidades-ativas` e
`unidades/{cod}/subordinadas`, que não foram usados agora mas resolvem casos
vizinhos.

## O que entrou

- **Migração 011:** `codigo_siedos` e `comarca` na unidade, `matricula` no
  servidor habilitado, `matricula` e `login_ad` no usuário. Todas anuláveis.
- **Porta ampliada:** hierarquia, detalhes da unidade, lotados (paginado),
  servidor por matrícula e por login. O `MockEgespClient` responde a todos, com
  códigos e matrículas estáveis — dá para exercitar a tela sem a API.
- **Adaptador real** com cache de token (5 min), renovação no vencimento e uma
  única retentativa no 401. Escolha entre real e mock na subida, com o motivo
  no log.
- **Motor de comparação** e endpoints da tela, exclusivos do superadministrador.
  Comparar é leitura; aplicar é um clique de cada vez.
- **Atualização no login**, assíncrona, por evento publicado no callback do SSO.
- **Importação da unidade inteira** (pedida depois, no mesmo dia): um botão traz
  todos os lotados, cria no cadastro de usuários quem ainda não existe — com
  papel de servidor — e habilita todos na edição. Não altera o papel de quem já
  está cadastrado (o RH sabe onde a pessoa trabalha, não o que ela pode fazer no
  prêmio) e não ressuscita quem foi removido da lista à mão; o resumo separa os
  números para que a diferença não pareça falha.

## Verificação

- API: 206 testes (eram 196). Novos: `SincronizacaoIT` (comparação não grava,
  casamento por nome e depois por código, inclusão pela matrícula com e-mail
  resolvido, órfão manual reconhecível, desvinculação lógica, acesso restrito) e
  `ConnectTjEgespClientTest` (paginação, 401 com renovação única, 404 vira
  vazio).
- **Não foi testado contra a API real**: falta o client. O que existe hoje é o
  contrato lido do Swagger e os testes contra servidor simulado.

## Pendências

- **Com a infra/equipe da API:** client (id e secret) e o realm que emite o
  token; endereço da API em produção; e confirmação do código da unidade raiz a
  varrer.
- **`loginAd`:** assumido como o prefixo do e-mail. Confirmar para magistrados.
- **Sem agendamento:** a sincronização é manual, pela tela. Se o tribunal quiser
  varredura periódica, é decidir a janela e quem responde pelos conflitos.
