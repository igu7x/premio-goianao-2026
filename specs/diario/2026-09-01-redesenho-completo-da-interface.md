# Redesenho completo da interface

**Data.** 01/09/2026
**Origem.** Pedido direto do usuário: *"refaça o design inteiro do sistema, de
todas as páginas, quero algo bem mais premium UX/UI"* — depois de uma primeira
rodada de melhorias incrementais ter sido explicitamente recusada.
**Escopo.** Frontend inteiro. Nenhuma alteração de contrato de API, de regra de
domínio ou de backend.

---

## O diagnóstico

A interface anterior era um painel administrativo competente e genérico:
cartões brancos, tabelas, etiquetas coloridas. O problema não era acabamento —
era ausência de identidade. E o sintoma mais claro estava no elemento central
do produto: **o selo aparecia como um pontinho colorido**. O sistema existe
para entregar medalhas, e a coisa mais importante que ele manipula estava
representada pelo componente mais banal da tela.

## O conceito adotado

> O produto entrega uma medalha. A interface é o papel em que essa medalha é
> impressa.

Disso saem três decisões que percorrem todas as telas:

1. **O selo é uma peça cunhada, não um rótulo.** Componente `Disco`: liga em
   degradê de dois tons, anel gravado (`::before`), varredura de lustro
   (`::after`) que corre no hover. Três tamanhos (`p`, `m`, `g`) e a mesma peça
   serve à tabela, ao cartão de certificado e à conferência pública.
2. **O papel é creme, não branco.** Fundo `#edeae1`, superfícies quentes,
   réguas de filete fino, sombras tingidas de verde institucional — nunca preto
   puro. Serifa editorial (Newsreader) nos títulos, sans (Inter) na interface,
   mono (IBM Plex Mono) nos códigos.
3. **O ouro é reservado.** `#c9a227` só aparece onde há reconhecimento: a
   edição vigente, o anel de progresso completo, o filete sob a marca. Não é
   cor de destaque genérico.

## O que foi refeito, tela a tela

| Tela | Antes | Agora |
|---|---|---|
| `Entrar` | Lista de usuários sobre fundo liso | Apresentação com guilhoché radial e as quatro ligas cunhadas em faixa |
| `Inicio` | Quatro indicadores iguais | Herói escuro com a edição vigente + anel de progresso de layouts (a única métrica que trava a emissão) e indicadores como apoio |
| `Edicoes` | Tabela de cinco colunas | Livro de atas: cada edição é uma faixa com o ano em serifa de 44px; a vigente se anuncia por filete dourado na lateral |
| `EdicaoDetalhe` | Abas sublinhadas | Controle segmentado; conteúdo remontado por chave a cada troca, entrando por baixo |
| `AbaLayouts` | Grade de cartões com etiqueta | Disco cunhado sobreposto à arte, marcador *Configurado / Pendente*, contador "n de 4" por tipo |
| `AbaMagistrados` | Nome em negrito, selos em etiqueta | Nome em serifa com contagem de reconhecimentos; cada reconhecimento com o disco da liga |
| `AbaServidores` | Nome em negrito | Disco do maior selo ao lado do nome da unidade em serifa |
| `PainelDaLista` | Formulário solto no topo | Resumo numérico da lista + bloco de inclusão destacado |
| `MinhasUnidades` | Bloco genérico com uma frase | Cartão de unidade com os dois números que importam (habilitados / removidos) |
| `MeusCertificados` | Linhas de tabela | Cartões de certificado com barra da liga no topo, disco grande, unidade em serifa e o código de validação na meta |
| `Verificar` | Ficha de dados | O próprio atestado: moldura de filete duplo, selo cunhado, dados em `<dl>` |

## Componentes novos

- `componentes/Selo.tsx` — `Disco`, `EtiquetaSelo`, `rotuloDoSelo`. Fonte única
  da representação do selo; `Basicos.tsx` reexporta para não quebrar imports.
- `componentes/Anel.tsx` — anel de progresso SVG. Existe porque "6 de 8
  layouts" só faz sentido como fração: o número exige cálculo, o arco entrega a
  proporção de imediato. O traço é desenhado na entrada.
- `componentes/Trilha.tsx` — caminho de volta que **nomeia o destino**
  ("Edições"), não diz "Voltar".
- `componentes/Avisos.tsx` — provedor de avisos flutuantes (`sucesso` / `erro`),
  com dispensa automática em 6s.

## Movimento

Tokens `--rapido` (130ms), `--medio` (220ms), `--lento` (380ms) com
`cubic-bezier(0.22, 1, 0.36, 1)`. Entradas escalonadas em listas (`.escalonar`,
`animationDelay` por índice). Tudo desligado sob
`prefers-reduced-motion: reduce`. A régua: num sistema usado vinte vezes por
dia, o que se anima precisa sair da frente.

## Verificação

- `npx tsc --noEmit` — limpo
- `npx vitest run` — 8 testes, todos passando
- `npm run build` — 42,6 kB de CSS (8,4 kB gzip), 239 kB de JS (74 kB gzip)

## Relacionado

- [Navegação e sistema de movimento](2026-09-01-navegacao-e-sistema-de-movimento.md)
- DI-12 em [decisões de implementação](../memory/decisoes-de-implementacao.md)
