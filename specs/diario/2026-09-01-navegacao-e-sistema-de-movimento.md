# 2026-09-01 — Navegação e sistema de movimento

Revisão de UX/UI pedida após a primeira apresentação: faltava navegação de
retorno, e a interface era estática demais para o padrão esperado.

> Frente de interface. Nenhuma regra de negócio mudou; nenhuma spec de feature
> foi afetada.

## Navegação — o que faltava

**Sem caminho de volta.** As telas internas (o detalhe de uma edição) não tinham
saída: quem entrava dependia do botão do navegador, que numa aplicação de página
única nem sempre faz o que o usuário espera.

- Novo componente `componentes/Trilha.tsx`: botão de retorno mais o caminho
  atual. O rótulo **nomeia o destino** ("Edições") em vez de dizer "Voltar" — o
  usuário sabe para onde vai antes de clicar. A seta recua no hover, reforçando
  a direção.

**Sem saber onde se está.** A lateral acendia "Edições do prêmio" e nada dizia
em qual edição o usuário estava.

- A lateral passou a mostrar um item aninhado com o **ano da edição aberta**, e
  a marca de vigente quando for o caso.

**Sem como trocar de edição.** Comparar 2025 com 2026 exigia voltar à lista e
entrar de novo, duas vezes por comparação.

- Seletor colado ao título: troca direto para outra edição, mantendo a aba em
  que se estava. Fica ali porque é onde o usuário lê em qual edição está — e é
  ali que ele pensa em trocar.

## Movimento — a régua adotada

Sistema pequeno e consistente, em `estilos/base.css`: três durações
(120/200/340 ms) e duas curvas. A regra que guiou tudo: **movimento com
propósito** — confirmar um clique, situar quem chegou numa tela nova, mostrar de
onde um painel veio. Em sistema de trabalho, o que se usa vinte vezes por dia
precisa sair da frente, não chamar atenção.

| Onde | O quê | Por quê |
|---|---|---|
| Página | entra deslizando de baixo | separa uma tela da seguinte |
| Grades e listas | entrada escalonada | o olho acompanha a montagem em vez de levar tudo de uma vez |
| Botões | sobem no hover, afundam no clique | retorno tátil que faltava em botão plano |
| Abas | sublinhado cresce do centro | mostra a troca, não só o resultado |
| Modais | escala e desfoque no fundo | dá origem ao painel; antes surgia do nada |
| Cartões | elevam e a arte aproxima | sinaliza que é clicável |
| Lateral | a barra dourada cresce | marca posição sem piscar |
| Botão de voltar | a seta recua | direção antes do clique |
| Carregando | esqueleto com brilho | espaço reservado, sem salto de layout |

Tudo desligado sob `prefers-reduced-motion` — quem configurou o sistema para
reduzir animação recebe a interface estática, sem perder função.

## Outras correções de interface

- **Avisos flutuantes** (`componentes/Avisos.tsx`): a confirmação de emissão e o
  resultado da semeadura viravam blocos fixos que empurravam a página para
  baixo. Agora aparecem no canto e somem sozinhos. Erro de formulário continua
  inline, junto do campo — ali o usuário precisa do texto enquanto corrige.
- **Aviso de pendências em colunas**: as oito combinações de layout empilhadas
  formavam um paredão amarelo que dominava a tela. Acima de três itens a lista
  vira colunas.
- **Combinação sem arte**: era um retângulo cinza chapado do tamanho de um
  certificado, o que fazia a tela parecer quebrada. Virou moldura tracejada com
  hachura leve e ícone de envio — espaço a preencher, não erro.
- **Modal fecha com Escape** e trava o rolar do fundo; sem isso a página de trás
  deslizava junto e o painel parecia solto.
- **Botões com indicador de progresso** durante emissão e semeadura, em vez de
  só trocar o texto.
- Elevação tingida com o verde da marca, nunca preto puro: sombra cinza sobre
  papel quente suja a cor.

## Verificação

- Percurso completo pelo Chrome, nos cinco perfis: **39 verificações, 0
  problemas**, nenhum erro de página ou console.
- `npm test` (8) e `npx tsc --noEmit` sem erros; `mvn test` (121) intacto.
