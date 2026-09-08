# 2026-09-01 — Ferramenta de teste no navegador (agent-browser)

> Entrada de infraestrutura de desenvolvimento, não de produto: nenhuma spec de
> feature foi afetada.

## O que foi feito

Instalada e configurada a ferramenta [agent-browser](https://github.com/vercel-labs/agent-browser)
(Vercel Labs) para que os testes exploratórios de interface possam ser feitos
dirigindo um navegador de verdade, e não apenas pelos testes automatizados.

- **CLI:** `npm install -g agent-browser` (v0.36.0).
- **Navegador:** o **Google Chrome instalado na máquina**, por escolha do
  desenvolvedor — fixado por `AGENT_BROWSER_EXECUTABLE_PATH`. O perfil usado é
  limpo e isolado: o perfil pessoal, com as sessões logadas, não entra no
  caminho.
- **MCP:** registrado em escopo de usuário como `agent-browser mcp --tools all`.
  Vale para todos os projetos da máquina; **nada foi acrescentado ao repositório**,
  para não impor ferramenta a quem for revisar o código.

## Verificação

Dois fluxos reais percorridos no ambiente local, com backend e frontend de pé.

**Emissão pelo magistrado:**

1. `open http://localhost:5173/entrar`
2. `snapshot` — a árvore de acessibilidade voltou com os nove usuários de teste,
   cada um com seus papéis, e a acentuação correta.
3. `click @e6` (Rafael Siqueira Bittencourt) — navegou para `/meus-certificados`.
4. `get url` e `get text h1` confirmaram o destino.

**Conferência pública (feature 007), ponta a ponta:** emitido o certificado
Diamante da 3ª Vara Criminal de Goiânia pela API, o código impresso foi digitado
na página pública e conferido — a tela devolveu "Certificado autêntico" com nome,
unidade, edição e selo corretos, e `errors` não acusou nenhum erro de página.

## Por que isso importa para o projeto

Os 110 testes de backend e os 8 de frontend verificam os critérios de aceitação,
mas não substituem olhar a tela: posicionamento das caixas no editor de layout,
comportamento do arrasto, e a aparência do PDF frente ao que o preview mostra são
coisas que só se conferem vendo. A ferramenta cobre exatamente essa lacuna.

Ela também expõe `a11y` (auditoria axe-core) e `vitals` (Core Web Vitals), úteis
caso o TJGO cobre acessibilidade ou desempenho antes da homologação.

## Observações operacionais

Três coisas que custaram tempo e é melhor não redescobrir:

1. No Git Bash do Windows, a saída do `agent-browser` **não pode ser canalizada**
   (`| tail`): o daemon de sessão herda o stdout e o pipe nunca fecha, fazendo o
   comando parecer travado. Redirecionar para arquivo resolve.
2. Exportar `MSYS_NO_PATHCONV=1` antes de chamar, senão o Git Bash converte rotas
   como `/entrar` em caminho do Windows e o `open` falha.
3. Clicar por **ref** obtido do `snapshot` (`click "@e4"`), não por texto: o
   `find text` não acha botões que misturam ícone SVG e rótulo — que é o caso de
   quase todos os botões deste frontend.
