# Spec: Base de dados independente por edição

- **ID:** 011-base-por-edicao
- **Status:** aprovada
- **Autor(es):** Igor
- **Data:** 2026-09-17

> Esta spec descreve **o quê** e **por quê**. NÃO inclui linguagem, framework
> ou detalhes de implementação — isso vai no `plan.md`.

## 1. Problema / Necessidade

O prêmio se repete todo ano, e o sistema é um só. Hoje parte do que ele guarda é
por edição (magistrados reconhecidos, listas de servidores habilitados, layouts,
certificados) e parte é compartilhada por todas elas (usuários, unidades
judiciárias, designação de responsável). A divisão nasceu de decisões pontuais —
a chefia da unidade não muda quando o prêmio muda de ano (DI-23) —, não de um
desenho.

O efeito prático é que edições não são independentes: mexer no cadastro de
usuários de 2027 altera o que 2026 enxerga, uma unidade renomeada na
sincronização de um ano reescreve o nome em todos os anos, e a pessoa que abre o
sistema não tem como saber sobre qual ano está agindo — parte da tela é do ano
escolhido, parte é de todos. Ao longo de vários anos, isso só piora: a base
acumula o passado e o presente misturados, e nenhuma operação de um ano pode ser
feita sem pensar no efeito sobre os outros.

## 2. Objetivo

Cada edição do prêmio passa a ter a **sua própria base de dados**, completa e
isolada: usuários, unidades, reconhecimentos, listas e layouts. Uma edição nova
nasce vazia e é povoada pelas mesmas ferramentas que já existem. Nada que se faça
dentro de um ano alcança outro ano. Deu certo quando trocar a edição corrente
troca a base inteira que se está vendo, e quando nenhuma operação numa edição
consegue alterar um registro de outra.

## 3. Usuários / Personas

- **Superadministrador** — monta a base de cada edição: sincroniza unidades com o
  RH, cadastra usuários, designa responsáveis, importa a planilha de reconhecidos.
- **Administrador** — configura layouts e reconhecimentos da edição em que está.
- **Magistrado / Servidor** — entra para emitir o certificado do ano em que foi
  reconhecido, e continua alcançando os anos anteriores em que tem direito.

## 4. Histórias de usuário

- Como **superadministrador**, quero que criar a edição de 2027 não traga nada de
  2026, para montar o ano novo sem herdar cadastro errado ou desatualizado.
- Como **superadministrador**, quero saber sempre sobre qual edição estou agindo,
  para não sincronizar 190 unidades no ano errado.
- Como **administrador**, quero que corrigir um nome de unidade em 2027 não
  reescreva o nome impresso nos certificados de 2026.
- Como **magistrado premiado em 2026**, quero continuar entrando e reemitindo meu
  certificado depois que 2027 virar a edição vigente.
- Como **qualquer pessoa**, quero verificar um código de certificado na página
  pública sem saber de que ano ele é.

## 5. Requisitos funcionais

- **RF-1:** Cada edição DEVE ter a sua própria base, contendo tudo o que o sistema
  guarda sobre o prêmio daquele ano: usuários e papéis, unidades judiciárias e
  seus responsáveis, magistrados reconhecidos e selos, listas de servidores
  habilitados, layouts, artes e certificados emitidos.
- **RF-2:** Uma edição criada DEVE nascer com a base vazia. Nenhum dado de outra
  edição é copiado.
- **RF-3:** Como exceção única ao RF-2, a edição criada DEVE receber os usuários
  com papel de superadministrador que existem na edição de onde ela foi criada.
  Sem isso a edição nova nasceria sem ninguém capaz de administrá-la.
- **RF-4:** Toda operação do sistema DEVE acontecer no contexto de exatamente uma
  edição, e essa edição DEVE ficar visível em tela enquanto se trabalha.
- **RF-5:** A edição do contexto DEVE fazer parte da sessão de quem entrou, não de
  uma escolha que o navegador possa forjar a cada requisição.
- **RF-6:** Trocar a edição do contexto DEVE ser um ato explícito de quem está
  logado, e o sistema DEVE oferecer apenas as edições em que aquela pessoa existe.
- **RF-7:** O login DEVE procurar a pessoa em todas as edições. Ela entra na
  edição vigente quando existe nela; caso contrário, na edição mais recente em que
  existe.
- **RF-8:** Os papéis de uma pessoa DEVEM ser os que ela tem **naquela edição**.
  A mesma pessoa pode ser magistrada em um ano e apenas servidora em outro.
- **RF-9:** Quem não existe em edição nenhuma NÃO DEVE entrar no sistema.
- **RF-10:** A verificação pública de um certificado pelo código DEVE funcionar sem
  sessão e alcançar certificados de qualquer edição.
- **RF-11:** Nenhuma operação executada no contexto de uma edição DEVE ler ou
  alterar registro de outra edição, com a exceção declarada no RF-7 (procurar a
  pessoa no login), no RF-10 (verificação pública) e no RF-3.
- **RF-12:** Os dados que hoje existem DEVEM ser preservados: cada edição já
  cadastrada passa a ter a sua base com o que era dela, e o que hoje é
  compartilhado (usuários, unidades, responsáveis) passa a existir em cada uma
  dessas edições.
- **RF-13:** O sistema DEVE ter sempre ao menos uma edição. Subindo sem nenhuma,
  ele cria a do ano corrente.

## 6. Requisitos não-funcionais

- **RNF-1:** O isolamento entre edições DEVE ser estrutural, e não depender de
  cada consulta lembrar de filtrar pelo ano.
- **RNF-2:** A separação NÃO DEVE exigir novo servidor, novo banco ou nova
  variável de ambiente da infraestrutura do tribunal.
- **RNF-3:** Criar uma edição DEVE continuar sendo uma operação de tela, concluída
  enquanto o superadministrador espera.
- **RNF-4:** Todas as funcionalidades existentes DEVEM continuar funcionando
  exatamente como hoje, agora dentro da edição corrente.

## 7. Critérios de aceitação

- **CA-1:** Dado que 2026 tem 190 unidades e 400 usuários, quando o
  superadministrador cria a edição de 2027, então a base de 2027 tem zero
  unidades, zero reconhecimentos, zero listas e apenas os superadministradores.
- **CA-2:** Dado que estou trabalhando em 2027, quando eu sincronizo as unidades
  com o RH, então a base de 2026 continua com as unidades que tinha, com os mesmos
  nomes.
- **CA-3:** Dado que uma pessoa é magistrada reconhecida em 2026 e não existe em
  2027, quando ela entra depois de 2027 virar vigente, então ela entra em 2026 e
  consegue reemitir o certificado dela.
- **CA-4:** Dado que uma pessoa existe em 2026 e em 2027, quando ela entra, então
  entra em 2027 (vigente) e pode trocar para 2026 pelo seletor de edição.
- **CA-5:** Dado que uma pessoa é administradora em 2026 e apenas servidora em
  2027, quando ela troca da edição 2026 para a 2027, então perde o menu de
  administração.
- **CA-6:** Dado um certificado emitido em 2026, quando alguém digita o código na
  página pública com 2027 vigente, então o certificado é confirmado com os dados
  de 2026.
- **CA-7:** Dado que a pessoa não existe em nenhuma edição, quando tenta entrar,
  então o acesso é recusado com a explicação de que não há cadastro.
- **CA-8:** Dado o banco de homologação como está hoje, quando a versão nova sobe,
  então cada edição existente passa a ter a sua base com os mesmos dados que
  apareciam antes, e nenhum registro é perdido.

## 8. Fora de escopo

- Banco de dados ou servidor físico separado por edição.
- Copiar, herdar ou importar dados de uma edição para outra (além do RF-3). Se
  isso for desejado depois, entra como feature própria.
- Excluir uma edição e a sua base.
- Relatório ou painel que compare anos diferentes lado a lado.
- Mudança em qualquer regra de negócio existente: o que muda é onde o dado vive.

## 9. Pontos em aberto

Nenhum.
