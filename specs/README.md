# Specs — Spec-Driven Development

Este diretório contém as especificações do projeto seguindo o fluxo de
**Spec-Driven Development (SDD)**: primeiro descrevemos *o que* e *por que*,
depois *como*, e só então *o quê fazer* (tarefas) — antes de escrever código.

## Estrutura

```
specs/
├── README.md              # este arquivo
├── memory/
│   ├── constitution.md    # princípios e regras invioláveis do projeto
│   └── decisoes-de-implementacao.md  # decisões técnicas que atravessam o projeto
├── templates/
│   ├── spec-template.md   # modelo de especificação (o quê / por quê)
│   ├── plan-template.md   # modelo de plano técnico (como)
│   └── tasks-template.md  # modelo de quebra em tarefas
├── diario/
│   └── AAAA-MM-DD-assunto.md  # registro do que foi feito em cada frente
└── features/
    └── NNN-nome-da-feature/
        ├── spec.md        # o quê e por quê (sem detalhes de implementação)
        ├── plan.md        # arquitetura, stack, decisões técnicas
        └── tasks.md       # passos executáveis, ordenados
```

## Fluxo de trabalho

1. **Constituição** — definir os princípios do projeto em `memory/constitution.md`.
   Feito uma vez; revisitado quando algo fundamental muda.
2. **Specify** — para cada feature, criar `features/NNN-nome/spec.md` a partir do
   template. Foca em comportamento observável e necessidade do usuário, **não** em
   tecnologia.
3. **Plan** — preencher `plan.md` com a abordagem técnica, stack e decisões.
4. **Tasks** — quebrar o plano em `tasks.md`: passos pequenos, ordenados e testáveis.
5. **Implementar** — só depois das specs aprovadas.
6. **Registrar** — ao concluir, marcar as tarefas em `tasks.md` e acrescentar ali
   uma seção **Implementação**: onde o código vive e o que foi decidido no
   caminho. Decisão que atravessa mais de uma feature vai para
   `memory/decisoes-de-implementacao.md`; o relato da frente de trabalho, com o
   que ficou pendente, vai para `diario/`.

## Convenções

- Pastas de feature numeradas em sequência: `001-`, `002-`, ...
- Tudo o que estiver marcado `[NEEDS CLARIFICATION: ...]` precisa ser resolvido
  antes de avançar para a etapa seguinte.
- `spec.md` nunca menciona linguagem, framework ou detalhe de implementação.
- `spec.md` e `plan.md` **não são reescritos** durante a implementação: o que a
  implementação decidir vai para as seções e arquivos do passo 6. Mudar o
  contrato é voltar ao passo 2, deliberadamente.
