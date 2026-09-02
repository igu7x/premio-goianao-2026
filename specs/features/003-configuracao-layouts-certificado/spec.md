# Spec: Configuração de layouts de certificado

- **ID:** 003-configuracao-layouts-certificado
- **Status:** aprovada
- **Autor(es):** equipe Goianão
- **Data:** 2026-05-31

> Esta spec descreve **o quê** e **por quê**.

## 1. Problema / Necessidade

Cada certificado tem uma arte (imagem) específica por **selo** (Bronze, Prata,
Ouro, Diamante) e por **tipo de reconhecido** (magistrado ou servidor), e essa
arte muda a cada **edição**. O certificado é a imagem com o **nome** do
reconhecido e a **unidade** preenchidos automaticamente na emissão. O
administrador precisa cadastrar essas artes e indicar **onde** nome e unidade
serão escritos.

## 2. Objetivo

Permitir ao administrador cadastrar, por edição, um layout para cada combinação
de selo × tipo, definindo a imagem-base e as posições/estilo do nome e da
unidade. Saberemos que deu certo quando a emissão (005/006) conseguir compor o
certificado final usando exatamente o layout configurado.

## 3. Usuários / Personas

- **Administrador** — cadastra e mantém os layouts.

## 4. Histórias de usuário

- Como **administrador**, quero subir a arte do certificado de cada selo para a
  edição vigente, para que os reconhecidos emitam com o visual correto.
- Como **administrador**, quero **arrastar e redimensionar** as caixas de nome,
  unidade e código sobre a arte (editor visual) e definir o alinhamento, para
  determinar onde cada texto será escrito (fonte/cor/tamanho são automáticos).
- Como **administrador**, quero pré-visualizar o layout com dados de exemplo para
  conferir o posicionamento antes de liberar.

## 5. Requisitos funcionais

- **RF-1:** O sistema DEVE permitir cadastrar um layout para uma combinação de
  **edição × selo × tipo** (magistrado/servidor).
- **RF-2:** Um layout DEVE conter uma **imagem-base** (arte do certificado).
- **RF-3:** Um layout DEVE definir a **área de nome** e a **área de unidade**
  por um **editor visual** (arrastar/redimensionar a área sobre a arte),
  persistindo **posição, largura/altura da área e alinhamento**. A **fonte é
  institucional fixa**, a **cor é preta** e o **tamanho é auto-ajustado** para o
  texto caber na área (não são escolhidos pelo admin).
- **RF-3b:** Um layout DEVE definir a **área do código de validação** (texto e,
  opcionalmente, um **QR code**), também posicionada pelo editor visual, onde
  será impresso o código único de autenticidade gerado na emissão (features
  005/006).
- **RF-4:** O sistema DEVE impedir mais de um layout para a mesma combinação
  edição × selo × tipo (substituição explícita permitida).
- **RF-5:** O sistema DEVE permitir **atualizar** e **substituir** a imagem e as
  áreas de um layout enquanto a edição estiver em **Rascunho**.
- **RF-6:** O sistema DEVE permitir **pré-visualizar** o certificado composto com
  nome, unidade e código de exemplo.
- **RF-7:** O sistema DEVE indicar, para uma edição, quais combinações de selo ×
  tipo ainda **não têm layout** configurado.
- **RF-8:** Ao **publicar** a edição, o sistema DEVE **travar** seus layouts
  (sem alteração), preservando a fidelidade das reemissões.

## 6. Requisitos não-funcionais

- **RNF-1:** Apenas administradores configuram layouts.
- **RNF-2:** A composição (nome+unidade sobre a arte) deve ser legível e
  consistente entre pré-visualização e emissão real.
- **RNF-3:** Formatos de imagem aceitos e limite de tamanho devem ser validados.
- **RNF-4:** A arte DEVE seguir o padrão **A4 paisagem a 300 DPI**
  (~3508×2480 px); o upload valida orientação/proporção e resolução mínima. As
  coordenadas das áreas são em px relativos a essa base.

## 7. Critérios de aceitação

- **CA-1:** Dado a edição vigente, quando o admin cadastra o layout Ouro/Servidor
  com imagem e áreas, então ele fica disponível para emissão desse selo/tipo.
- **CA-2:** Dado um layout já existente para Ouro/Servidor na edição, quando o
  admin tenta criar outro igual, então é exigida substituição explícita.
- **CA-3:** Dado um layout configurado, quando o admin pré-visualiza com nome e
  unidade de exemplo, então vê o nome e a unidade nas posições definidas.
- **CA-4:** Dada uma edição sem layout para Diamante/Magistrado, quando o admin
  consulta o status, então o sistema aponta essa combinação como pendente.
- **CA-5:** Dado um não-administrador, quando tenta configurar layout, então o
  acesso é negado.

## 8. Fora de escopo

- A emissão em si (features 005/006) — esta feature só configura e pré-visualiza.
- Editor gráfico avançado (basta posicionar nome e unidade).

## 9. Pontos em aberto

- **RESOLVIDO:** as áreas são definidas por **editor visual** e persistidas como
  **caixa (x, y, largura, altura) em px relativos à imagem-base** + alinhamento.
  **Fonte institucional fixa, cor preta, tamanho auto-ajustado** para caber. O
  preview reflete exatamente o que será emitido.
- **RESOLVIDO:** os campos variáveis são **nome**, **unidade** e **código de
  validação** (texto e/ou QR). Demais elementos (ano, título, selo, textos fixos)
  já vêm embutidos na própria arte.
- **RESOLVIDO:** padrão da arte = **A4 paisagem, 300 DPI** (~3508×2480 px),
  validado no upload; coordenadas em px sobre essa base.
