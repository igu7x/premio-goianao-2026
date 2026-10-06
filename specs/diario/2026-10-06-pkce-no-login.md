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
- A troca do código com o verifier só se confere num login de verdade, em stag,
  depois do deploy.

## Pendências

- Publicar em stag e testar o login pelo SSO; depois, levar a produção pelo MR
  `stag` → `main`.
- Responder à equipe do SSO: o login do Goianão é feito pela API, não por SPA.
  Com PKCE na API, eles podem manter a exigência; o segredo pode sair se o
  client virar público.
- Instalar a raiz da AC TJGO no notebook novo (arquivo com a infra).
