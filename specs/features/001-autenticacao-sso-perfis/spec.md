# Spec: Autenticação SSO e perfis de acesso

- **ID:** 001-autenticacao-sso-perfis
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31

> Esta spec descreve **o quê** e **por quê**. NÃO inclui linguagem, framework
> ou detalhes de implementação — isso vai no `plan.md`.

## 1. Problema / Necessidade

O sistema precisa saber **quem** está acessando para decidir o que essa pessoa
pode fazer (configurar, ou emitir certificados). O acesso será via SSO
corporativo do TJGO, que identifica o CPF do usuário. Nesta fase os parâmetros
de OAuth ainda não estão disponíveis, então o login será **mockado**, mas o
restante do sistema deve enxergá-lo como um login real.

## 2. Objetivo

Autenticar o usuário, obter seu CPF e nome, e resolver seu **perfil**
(Administrador, Magistrado ou Servidor), disponibilizando essa identidade para
todas as demais funcionalidades. Saberemos que deu certo quando cada perfil só
acessar o que lhe compete e a identidade do emissor nunca for digitada.

## 3. Usuários / Personas

- **Administrador** — alimenta cadastros e configurações.
- **Magistrado** — emite os certificados das unidades/selo que recebeu.
- **Servidor** — emite o certificado da unidade onde é lotado.

## 4. Histórias de usuário

- Como **usuário**, quero entrar via SSO para não precisar criar/lembrar senha.
- Como **usuário**, quero que o sistema já saiba quem eu sou (CPF/nome) para não
  digitar meus dados.
- Como **sistema**, quero classificar o usuário em um ou mais papéis para exibir
  todos os menus/ações a que ele tem direito.
- Como **usuário que acumula papéis** (ex.: admin que também é magistrado
  reconhecido), quero ver simultaneamente os menus de todos os meus papéis
  (ex.: "Cadastro de Edição" e "Emitir Certificado").

## 5. Requisitos funcionais

- **RF-1:** O sistema DEVE autenticar o usuário via SSO corporativo e, ao final,
  dispor de seu **CPF** e **nome**.
- **RF-2:** Nesta fase, o login DEVE ser servido por um provedor **mockado** que
  produz CPF/nome de teste, sem alterar o contrato visto pelo restante do app.
- **RF-3:** O sistema DEVE resolver o **conjunto de papéis** do usuário
  autenticado. Um mesmo CPF pode acumular mais de um papel (Administrador,
  Magistrado, Servidor) e, nesse caso, recebe **todos** os menus/ações
  correspondentes (união das capacidades) — não há troca de contexto/"atuar como".
- **RF-4:** O papel **Administrador** DEVE ser atribuído quando o CPF constar no
  cadastro explícito de administradores (lista de CPFs).
- **RF-5:** O papel **Magistrado** DEVE ser atribuído quando o CPF autenticado
  constar como magistrado reconhecido em alguma edição (ver feature 004).
- **RF-6:** O papel **Servidor** DEVE ser o papel padrão dos usuários
  autenticados que não sejam Administrador nem Magistrado.
- **RF-7:** O sistema DEVE expor a identidade autenticada (CPF, nome, **papéis**)
  para as demais funcionalidades de forma confiável durante a sessão.
- **RF-8:** O sistema DEVE negar acesso a ações fora dos papéis do usuário
  (controle de acesso por papel).
- **RF-9:** O sistema DEVE permitir encerrar a sessão (logout).

## 6. Requisitos não-funcionais

- **RNF-1:** A troca do provedor mock pelo SSO real NÃO deve exigir mudança nas
  features que consomem a identidade.
- **RNF-2:** Toda decisão de autorização ocorre no backend; o frontend apenas
  reflete o que o usuário pode ver.
- **RNF-3:** O CPF é dado sensível; não deve ser exposto em logs ou URLs.

## 7. Critérios de aceitação

- **CA-1:** Dado um CPF cadastrado como administrador, quando ele autentica,
  então seus papéis incluem Administrador.
- **CA-2:** Dado um CPF que consta como magistrado reconhecido, quando ele
  autentica, então seus papéis incluem Magistrado.
- **CA-3:** Dado um CPF que não é admin nem magistrado, quando ele autentica,
  então seu papel é Servidor.
- **CA-4:** Dado um CPF que é **administrador e magistrado**, quando ele
  autentica, então vê os menus de **ambos** (ex.: "Cadastro de Edição" e
  "Emitir Certificado").
- **CA-5:** Dado um usuário **sem** papel de administrador, quando tenta acessar
  uma ação de administrador, então o acesso é negado.
- **CA-6:** Dado um usuário autenticado, quando consulta sua identidade, então
  recebe CPF, nome e o conjunto de papéis coerentes com o cadastro.

## 8. Fora de escopo

- Configuração real de OAuth/OIDC do SSO corporativo (fase posterior).
- Gestão de senhas, MFA ou recuperação de acesso (responsabilidade do SSO).
- Integração com EGESP (tratada na feature 006).

## 9. Pontos em aberto

- **RESOLVIDO:** um mesmo CPF pode acumular papéis; nesse caso o usuário vê
  **todos os menus** dos seus papéis (união das capacidades), sem "atuar como".
- **RESOLVIDO:** o SSO real usará **OIDC/OAuth2**; o adaptador futuro
  (`SsoIdentityProvider`) seguirá o fluxo Authorization Code. Parâmetros concretos
  (issuer, client id/secret, claim do CPF) ainda pendentes do TJGO.
