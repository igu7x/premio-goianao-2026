# 2026-10-02 — O ConnectTJ passa a exigir Signed JWT

> Diário de implementação. A decisão e o porquê estão na DI-32; a feature é a
> 010, com as tarefas T-010 a T-012.

## O pedido

O Igor procurou a equipe do ConnectTJ para levar a integração com o RH à
produção, onde ela ainda roda no mock. A resposta veio com um guia novo
("Integrando com o ConnectTJ usando Signed JWT", 2026-10-01): antes de subir,
o Goianão precisava mudar a forma de obter o token. Cada sistema passa a ter o
próprio client e prova quem é com uma chave privada, em vez do segredo
compartilhado `api-connect`.

A equipe criou no `DG-TST` o client `ces-goianao-service-stag` e mandou a chave
privada de homologação (RSA). O nome chegou primeiro sem o `service`, e o
Keycloak respondeu `invalid_client` — o mesmo erro que daria uma chave errada.

## O que entrou

- **`AssercaoDoClient`:** lê a chave e assina a assertion. Ed25519 assina com
  EdDSA, RSA com RS256; o algoritmo sai do tipo da chave.
- **`TokenConnectTj`** pede o token com a assertion quando há chave, e com o
  segredo só quando não há — homologação ainda roda pelo segredo até a troca.
- **Endereços de produção como padrão.** Em produção bastam o client e a chave.
- **Motivo na tela.** Recusa do Keycloak, client não cadastrado no ConnectTJ e
  recurso não liberado chegam ao superadministrador como 502 com a mensagem, e
  não como "erro inesperado".

## O que apareceu no caminho

**O nome que o guia manda usar já estava ocupado.** O guia entrega o client em
`OPENSHIFT_SSO_KEYCLOACK_CLIENT_ID`; aqui esse nome é o client do login das
pessoas. Se a infra seguisse o guia ao pé da letra, sobrescreveria o client do
login e o SSO pararia. O client do ConnectTJ ficou em
`GOIANAO_CONNECTTJ_CLIENT_ID`, e o pedido à infra precisa dizer isso.

**O 403 do AD levaria CPF para o log.** A rota do AD é consultada por CPF na
query string. A mensagem do 403 mostra a rota sem a query.

## Verificação

- API: 269 testes, nenhuma falha (eram 258).
  `AssercaoDoClientTest` (7) confere a assinatura com a chave pública nos dois
  algoritmos, o `jti` novo a cada assertion, os formatos de colagem da chave e a
  recusa de PKCS#1 sem vazar a chave na mensagem. `ConnectTjEgespClientTest`
  ganhou três casos: token por assertion sem o segredo, recusa do Keycloak sem
  segunda tentativa e 403 por recurso.
- **Contra o `DG-TST`, com `ces-goianao-service-stag`:** o Keycloak **aceitou a
  assertion e emitiu o token**. O ConnectTJ respondeu primeiro 403 "Cliente não
  autorizado" — o client ainda não estava cadastrado lá. Liberados os recursos
  pela equipe, `ConnectTjRealIT` passou inteiro (3/3): hierarquia da SGJT,
  lotados paginados e e-mail resolvido pela matrícula, tudo com o token
  assinado em RS256.

## Pendências

- Homologação: trocar `GOIANAO_CONNECTTJ_CLIENT_ID` para
  `ces-goianao-service-stag`, criar o Secret com a chave e retirar
  `GOIANAO_CONNECTTJ_SECRET` — infra.
- Produção (T-012): client criado pela infra (Ed25519), entregue em
  `GOIANAO_CONNECTTJ_CLIENT_ID` + `OPENSHIFT_SSO_KEYCLOACK_PRIVATE_KEY`, sem
  tocar no `OPENSHIFT_SSO_KEYCLOACK_CLIENT_ID`; e cadastro no ConnectTJ de
  produção.
- A chave de homologação foi enviada pelo chat. É de teste, mas vale gerar a
  de produção sem que ela passe por chat nenhum — o guia já diz que é a infra
  que a gera.
