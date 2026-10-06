# 2026-10-06 — O login parou por falta de PKCE

> Diário de implementação. A decisão e o porquê estão na DI-33; a feature é a
> 001.

## O que aconteceu

A infra ajustou as variáveis do ConnectTJ em stag (DI-32), e o Igor foi validar
pela tela. Primeiro veio "Não foi possível falar com o servidor": era o
certificado da AC TJGO, que o notebook novo ainda não tem instalado — aceitar
o aviso abrindo a API direto resolveu para a sessão.

Depois veio "O login corporativo foi recusado". A rota de login da API mostrou
que o client era o certo (`goianao-stag`); seguindo o redirecionamento, o
Keycloak devolvia `invalid_request — Missing parameter: code_challenge_method`.
A equipe do SSO tinha aplicado o padrão novo do tribunal aos clients de login,
com PKCE S256 obrigatório, supondo um frontend SPA. **Produção estava igual**,
e lá só se entra por SSO: ninguém conseguia acessar.

## O que foi feito

- Pedido à equipe do SSO para suspender a exigência de PKCE nos dois clients
  enquanto a versão nova não sobe.
- PKCE S256 na API (`DesafioPkce`, cookie da tentativa, `code_verifier` na
  troca), com o `state` passando a ser conferido contra o navegador.
- Segredo do client de login opcional, para o caso de ele virar público.

## Verificação

- Testes do SSO verdes, com os casos novos de PKCE e de cookie.
- **Contra o Keycloak real:** uma URL de autorização com `code_challenge` S256
  é aceita por `goianao-stag` e `goianao-prd` — o Keycloak serve a tela de
  login, em vez do erro.
- **Login de verdade pelo SSO, com o PKCE exigido pelo Keycloak:** em stag e,
  depois do MR `stag` → `main`, em produção. O login de produção voltou no
  mesmo dia.

## Pendências

- Responder à equipe do SSO: o login do Goianão é feito pela API, não por SPA.
  Com PKCE na API, eles podem manter a exigência; o segredo pode sair se o
  client virar público.
- Produção chama a API pelo endereço interno, de certificado da AC TJGO: fora
  das máquinas do domínio, o navegador bloqueia. A rota `/api` no host público
  continua pendente com a infra.
- Instalar a raiz da AC TJGO no notebook novo (arquivo com a infra).

## O ConnectTJ no padrão da infra, validado em stag

No mesmo dia, a infra definiu o padrão dos clients JWT: `goianao-service-stag`
e `goianao-service-prd`, no **realm dos usuários** (`tjgo.gov-tst` e
`tjgo.jus.br-2fa`), com chave **Ed25519** gerada por eles — e não o
`ces-goianao-service-stag` do `DG-TST` que a equipe do ConnectTJ tinha criado
(DI-32). O código não mudou; mudaram as variáveis de stag.

Três tropeços até funcionar, cada um com a mensagem do Keycloak na tela
(commit "Recusa do Keycloak mostra o motivo que ele devolve"):

1. `Invalid signature algorithm` — o Secret ainda tinha a chave RSA antiga; a
   Ed25519 foi colocada pela infra e o pod reiniciado.
2. `unauthorized_client — Client not enabled to retrieve service account` — o
   client não tinha *Service accounts roles* ligado.
3. Nenhum: com o ConnectTJ de homologação aceitando o `tjgo.gov-tst` numa
   janela de teste aberta pela equipe dele, a comparação da SGJT (`901190605`)
   trouxe 190 unidades do RH.

Depois do teste a equipe do ConnectTJ volta a aceitar só o `DG-TST` em
homologação, e o RH de stag deixa de responder. É esperado: o teste provou o
caminho de produção.

**Para produção, pedir de uma vez:** `goianao-service-prd` no
`tjgo.jus.br-2fa`, Ed25519, *Service accounts roles* ligado; a chave em
`OPENSHIFT_SSO_KEYCLOACK_PRIVATE_KEY` com o pod reiniciado; e a pipeline de
produção do ConnectTJ. Endereços de token e da API já são o padrão do sistema.
