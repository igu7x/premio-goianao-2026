# 2026-09-17 — Base de dados independente por edição

Feature 011. Spec, plano e tarefas em `specs/features/011-base-por-edicao/`;
a decisão técnica está na DI-30.

## O pedido

Cada edição do prêmio com a sua base de dados completa — usuários, unidades,
reconhecidos, listas, tudo —, uma edição nova nascendo vazia e com as mesmas
funcionalidades. O motivo dado foi escala: o sistema vai rodar por vários anos,
e as edições não podem depender umas das outras.

Duas decisões foram tomadas com o Igor antes de começar:

- **Schema por edição**, e não uma coluna `edicao_id` em cada tabela. O
  isolamento fica estrutural em vez de depender de toda consulta lembrar do
  filtro.
- **Quem foi premiado num ano continua entrando depois que o ano seguinte
  vira vigente.** O login procura a pessoa em todas as edições.

## O que ficou

- Um schema por edição no mesmo banco. O compartilhado guarda só o catálogo de
  edições e o índice da verificação pública.
- A edição vai no token. O seletor no topo troca de edição pedindo um token
  novo, com os papéis daquela edição.
- Criar edição cria a base vazia; os superadministradores vão junto, senão a
  edição nasceria sem quem a administrasse.
- Rota que fala de outra edição que não a da sessão é recusada. O catálogo
  (publicar, tornar vigente) alcança todas.
- Na subida, a base antiga é levada para o schema de cada edição e as tabelas
  originais ficam como `legado_*`.

## O que apareceu no caminho

- O rollback dos testes de integração escondia que a conexão ficava presa à
  base do início da transação. Os testes passaram a recomeçar de uma base
  reconstruída — e isso revelou um defeito antigo, corrigido: editar magistrado
  mantendo a mesma unidade dava 409 em produção.
- O banco H2 de desenvolvimento é anterior à troca de CPF por e-mail (DI-24):
  os cadastros de demonstração estão como `cpf@cpf.invalid` e não casam com as
  identidades de teste. Antes isso passava despercebido porque quem não era
  encontrado entrava como servidor; agora quem não existe em edição nenhuma não
  entra (RF-9). Apagar `api/data/` faz a carga de demonstração ser gerada de
  novo, já no formato atual. O banco foi migrado nesta máquina durante a
  conferência no navegador; a cópia de antes ficou fora do repositório.

## Estado ao fim do dia

**No repositório** (`main`): spec da 011 · backend da base por edição · guarda
de rota e testes novos · seletor de edição no frontend · este registro.

**Antes do deploy em homologação:**

1. **Backup do banco.** A primeira subida da versão nova migra o banco de
   homologação sozinha — cria os schemas, copia os dados e renomeia as tabelas
   antigas. É idempotente e não apaga nada, mas é a primeira vez que ela roda
   contra dados reais. Um dump antes custa pouco. Pedido para a infra, junto com
   o que mais houver.
2. **Permissão de `CREATE SCHEMA`** para o usuário do banco. Sem ela a aplicação
   não sobe. Em homologação o usuário é dono do banco e deve ter; vale confirmar
   com a infra no mesmo pedido.

**A conferir em homologação depois do deploy:**

1. O log da subida mostra "Migração concluída" e uma linha por edição.
2. Entrar como superadministrador: o topo mostra a edição vigente; trocar para a
   outra mostra os números dela.
3. Um código de certificado já emitido continua conferindo na página pública.
