# Constituição do Projeto

> Princípios que governam todas as decisões. Specs, planos e código devem
> respeitá-los. Alterar um princípio exige justificativa explícita.

## Identidade

- **Nome do projeto:** Goianão — Emissão de Certificados (TJGO)
- **Propósito em uma frase:** Permitir que os reconhecidos do prêmio Goianão
  (magistrados e servidores) emitam, por conta própria, o certificado da edição
  vigente conforme o selo (Bronze, Prata, Ouro ou Diamante) reconhecido por
  unidade judiciária do Tribunal de Justiça do Estado de Goiás.

## Princípios

1. **Spec antes de código** — toda feature começa por uma `spec.md` aprovada;
   o `plan.md` só é detalhado depois. Nada é implementado sem spec aprovada.
2. **Fonte única da verdade é o administrador** — quem venceu, em qual unidade e
   com qual selo, é informação cadastrada pelo administrador. O sistema nunca
   infere ou calcula vencedores.
3. **Identidade vem de integração, não do emissor** — CPF e nome do emissor são
   obtidos do SSO. O emissor nunca digita esses dados; ele apenas emite o que lhe
   é de direito.
3a. **O nome impresso no certificado tem origem definida por tipo** — para o
   **magistrado**, o nome impresso é o do **cadastro daquela edição** (princípio 2:
   o administrador é a fonte da verdade; o cadastro é travado ao publicar, então a
   reemissão de uma edição antiga sempre imprime a mesma grafia). Para o
   **servidor**, o nome impresso vem do **SSO**, porque a lista de habilitados
   registra *quem pode emitir* (CPF×unidade) e não a grafia oficial do nome. Em
   ambos os casos o SSO é quem **identifica** o emissor (CPF); a diferença é apenas
   qual fonte fornece o **texto** que vai no PDF.
3b. **Elegibilidade do servidor é versionada por edição (snapshot)** — a relação
   servidor↔unidade é registrada numa **lista de servidores habilitados** por
   edição, semeada do EGESP e editável (admin sempre; magistrado só na vigente).
   A emissão consulta essa lista, nunca a lotação ao vivo — assim a reemissão de
   edições anteriores permanece fiel ao que valia naquela época.
4. **Regra do maior selo** — quando uma unidade é reconhecida com selos
   diferentes (por magistrados distintos), o certificado do servidor usa o de
   maior valor: Diamante > Ouro > Prata > Bronze.
5. **Layout é configuração versionada por edição** — cada combinação de
   edição × selo × tipo (magistrado/servidor) tem seu próprio layout. A
   configuração (layouts e cadastro de magistrados) é editável enquanto a edição
   está em **Rascunho** e é **travada ao publicar**, preservando a fidelidade das
   reemissões de edições anteriores.
   - **Exceção aditiva (feature 009):** na **edição vigente** o administrador
     pode **incluir** novos magistrados reconhecidos e novos reconhecimentos
     (unidade+selo). Apenas inclusão — **editar/remover** o que já existe segue
     restrito a Rascunho, pois adicionar não altera certificados já emitidos.
6. **Perfis estritamente separados (RBAC)** — Administrador, Magistrado e
   Servidor têm capacidades distintas e isoladas; cada endpoint valida o perfil.
7. **Integrações externas isoladas atrás de interface** — SSO e EGESP são
   acessados por uma porta (interface) com implementação mockável, para que a
   troca pelo provedor real não afete o domínio.
8. **Edição vigente é o contexto padrão, não uma trava** — a emissão usa a
   edição vigente por padrão, mas qualquer edição **publicada** anterior continua
   disponível para **reemissão**, sempre com o layout e os dados daquela edição.
   Não há "encerramento" que bloqueie emissão.

## Restrições conhecidas

- **Stack / plataforma:** Java 21 LTS + Spring (backend), React (frontend),
  PostgreSQL (banco), organizados em **monorepo** (backend + frontend no mesmo
  repositório).
- **Integrações externas:** SSO corporativo (identifica CPF/nome) e EGESP
  (sistema de RH; **fornece a lista de unidades** e os **servidores por unidade**
  para semear a lista de habilitados). Ambos **mockados** nesta fase — parâmetros
  reais de OAuth/API ainda indisponíveis. O **nome da unidade** (como vem do
  EGESP) identifica a unidade ao semear; a **emissão do servidor não chama o
  EGESP** (usa a lista persistida da feature 008).
- **Formato do certificado:** PDF gerado a partir de uma imagem de layout, com
  nome e unidade sobrepostos em posições configuradas.
- **Não-objetivos (o que o projeto explicitamente NÃO faz):**
  - Não define nem calcula quem venceu o prêmio (entrada manual do admin).
  - Não gerencia o ciclo de avaliação/julgamento do prêmio.
  - Não implementa OAuth/SSO real nesta fase (mock).
  - Não integra de fato com o EGESP nesta fase (mock).

## Glossário

| Termo | Significado |
|-------|-------------|
| Edição | Realização anual do prêmio Goianão, identificada pelo ano. |
| Selo / Modalidade | Nível de reconhecimento: Bronze, Prata, Ouro ou Diamante. |
| Reconhecimento | Vínculo de um magistrado a uma unidade com um selo, numa edição. |
| Unidade Judiciária | Unidade do TJGO pela qual o reconhecimento é concedido. |
| Magistrado | Juiz reconhecido; emite certificados das unidades/selo que recebeu. |
| Servidor | Funcionário lotado em unidade reconhecida; emite com a regra do maior selo. |
| Administrador | Perfil que alimenta todos os cadastros e configurações. |
| Layout de certificado | Imagem-modelo + posições de nome e unidade, por edição/selo/tipo. |
| SSO | Login corporativo que fornece CPF e nome do usuário (mock). |
| EGESP | Sistema de RH; fornece a lista de unidades e os servidores por unidade (mock). |
| Lista de servidores habilitados | Snapshot por edição×unidade de quem pode emitir o certificado de servidor; semeada do EGESP e editável (feature 008). |
| Edição vigente | Edição padrão (atual); não bloqueia reemissão de edições anteriores. |
| Código de validação | Identificador único e opaco impresso no certificado (texto + QR) para verificação pública de autenticidade. |
