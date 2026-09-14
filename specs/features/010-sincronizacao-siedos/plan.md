# Plano técnico: Sincronização com o ConnectTJ (SIEDOS/EGESP)

- **ID:** 010-sincronizacao-siedos
- **Spec relacionada:** ./spec.md
- **Status:** aprovado

## 1. Abordagem

A porta `EgespClient` já existe e isola o domínio da integração (constituição,
princípio 7). Ela ganha os métodos que a tela e o login precisam, e uma segunda
implementação — `ConnectTjEgespClient` — fala com a API corporativa. O
`MockEgespClient` continua sendo o que roda em dev e nos testes.

A tela de sincronização é um **motor de comparação** que lê dos dois lados e
devolve um relatório categorizado. Aplicar é um segundo passo, por endpoint
próprio, item a item.

## 2. Stack e dependências

Nada novo: `RestClient` do Spring (já usado no `ClienteKeycloak`) e Jackson. O
token é pedido ao Keycloak por `client_credentials`.

## 3. Arquitetura

```
integracao/egesp/
  EgespClient                 (porta — ampliada)
  MockEgespClient             (dev/teste, já existe)
  connecttj/
    ConnectTjEgespClient      (adaptador real; @ConditionalOnProperty)
    ConnectTjProperties       (goianao.connecttj.*; habilitado())
    TokenConnectTj            (cache do token, renova no vencimento e no 401)
    dto/…                     (espelho do payload da API)
sincronizacao/
  SincronizacaoService        (motor de comparação; só leitura)
  SincronizacaoController     (/api/sincronizacao/**, hasRole('SUPERADMIN'))
  dto/ComparacaoResposta      (4 listas + situação da integração)
auth/sso/
  AtualizacaoNoLogin          (@Async, dispara após o callback do SSO)
```

**Fluxo da comparação:** hierarquia da unidade → para cada unidade, casar com o
banco (código, depois nome canônico) → lotados paginados (`page`/`size`, itera
`totalPages`) → resolver e-mail por matrícula (cache por operação) → comparar
com `servidor_habilitado` da edição.

**Fluxo do login:** `SsoController` termina o callback → evento →
`AtualizacaoNoLogin` (assíncrono) → `buscar-servidor-por-login-ad` → atualiza
`usuario`. Exceção é registrada e engolida.

## 4. Modelo de dados

Migração **011**:

- `unidade_judiciaria.codigo_siedos BIGINT NULL UNIQUE` — o código do SIEDOS.
  Nulo enquanto a unidade não for casada com a API; `nome_canonico` continua
  sendo a unicidade principal, porque o mock e a importação manual não têm
  código.
- `unidade_judiciaria.comarca VARCHAR(150) NULL` — já vem da API e ajuda a
  distinguir homônimas na tela.
- `servidor_habilitado.matricula BIGINT NULL` — de onde a linha veio no RH.
- `usuario.matricula BIGINT NULL` e `usuario.login_ad VARCHAR(100) NULL` — para
  a rotina do login e para diagnóstico.

Nenhuma coluna existente muda de tipo ou de obrigatoriedade.

## 5. Contratos / APIs

Da API corporativa (todas exigem Bearer; base em `goianao.connecttj.url`):

| Uso | Endpoint |
|---|---|
| hierarquia | `GET /api/v1/unidades/estrutura-hierarquica?codigoUnidade=` |
| detalhes da unidade | `GET /api/v1/unidades/{cod}` |
| lotados (paginado) | `GET /api/v1/unidades/{cod}/lotados?incluirUnidadesSubordinadas=&page=&size=` |
| servidor por matrícula | `GET /api/v1/servidores/buscar-por-matricula?matricula=` |
| servidor por login | `GET /api/v1/servidores/buscar-servidor-por-login-ad?loginAd=` |

Nossos endpoints (todos `hasRole('SUPERADMIN')`):

- `GET /api/sincronizacao/situacao` → integração ligada? qual base?
- `GET /api/sincronizacao/unidades?codigo=&edicaoId=` → comparação de unidades.
- `GET /api/sincronizacao/unidades/{unidadeId}/servidores?edicaoId=` → comparação
  da lista de habilitados.
- `POST /api/sincronizacao/unidades` `{codigo}` → cadastra unidade da API.
- `PUT /api/sincronizacao/unidades/{unidadeId}` → aplica nome/comarca da API.
- `POST /api/sincronizacao/unidades/{unidadeId}/servidores` `{edicaoId, matricula}`
  → inclui na lista da edição (resolve e-mail pela matrícula).
- `DELETE /api/sincronizacao/servidores/{servidorHabilitadoId}` → desvincula.

## 6. Decisões técnicas (ADR resumido)

| Decisão | Alternativas | Escolha e motivo |
|---|---|---|
| E-mail do lotado | linha "pendente sem e-mail"; pedir mudança na API | **Resolver pela matrícula** (`buscar-por-matricula` existe e devolve `endEmail`): a lista nasce completa e ninguém fica meio-cadastrado |
| Casamento de unidade | só por código; só por nome | **Código, com fallback para nome canônico** e gravação do código: o cadastro atual não tem código e não pode ser recriado |
| Quem aplica | carga automática | **Aprovação item a item**: o nome da unidade vai impresso no certificado |
| Ligar a integração | flag manual | **Configuração completa liga**, como no SSO: variável faltando cai no mock em vez de derrubar o pod |
| Rotina do login | síncrona no callback | **Assíncrona**: a API do RH não pode atrasar nem quebrar o login |

## 7. Riscos e mitigação

- **Risco:** renomear unidade que já imprimiu certificado → **Mitigação:** a
  atualização é manual, a tela avisa, e o certificado emitido guarda o nome que
  foi impresso.
- **Risco:** N+1 de chamadas ao resolver e-mail por matrícula → **Mitigação:**
  cache por operação e só para quem entra na lista.
- **Risco:** token de 5 minutos vencendo no meio de uma varredura →
  **Mitigação:** renovação no vencimento e uma retentativa no 401.
- **Risco:** a API devolver lotação de servidor de outra unidade (subordinadas)
  → **Mitigação:** `incluirUnidadesSubordinadas=false` na semeadura.

## 8. Estratégia de testes

Testes de integração com o mock e com um `RestClient` apontado para um servidor
HTTP de teste (`MockWebServer` não entra: usa-se um `@TestConfiguration` com
stub da porta, e um teste específico do adaptador com `MockRestServiceServer`).
Cobrir: paginação, 401 com renovação única, casamento por código e por nome,
órfão manual preservado, resolução de e-mail por matrícula, login que atualiza
cadastro sem tocar na lista.
