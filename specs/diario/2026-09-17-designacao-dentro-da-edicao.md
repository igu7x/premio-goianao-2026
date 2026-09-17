# 2026-09-17 — A designação entrou na edição e passou a semear a lista

> Diário de implementação. A decisão e o porquê estão na DI-27; os efeitos sobre
> a lista de habilitados, na feature 008. Aqui fica o que foi feito e o que
> ficou para depois.

## O pedido

Duas coisas, ditas juntas: mover a tela "Unidades" para dentro de **Edições do
prêmio** e fazer com que **associar um magistrado responsável já povoe a unidade
com os servidores dela**. E a conclusão: com isso, o módulo "Unidades" não
precisa mais existir.

As duas são a mesma coisa vista de dois lados. A designação não valia por si: o
que ela entrega é uma lista de habilitados — e lista de habilitados é de uma
**edição**. Fora de uma edição, o código escolhia a vigente por suposição.

## O que entrou

- **Designar virou um ato só.** `DesignacaoService` grava o responsável e semeia
  a lista da unidade naquela edição. Mora num serviço à parte porque
  `ServidorHabilitadoService` já depende de `UnidadeService`: chamar de volta
  fecharia o ciclo.
- **A guarda da lista abriu uma segunda porta.** Unidade reconhecida **ou** com
  responsável designado. Sem nenhuma das duas, segue 422.
- **A planilha semeia linha a linha.** O relatório ganhou `listasSemeadas` e
  `habilitados`; RH mudo numa linha vira erro só daquela linha.
- **O CSV de magistrados responsáveis** foi para a aba "Magistrados
  reconhecidos", ao lado do cadastro de reconhecidos — é do que ele trata. Fica
  disponível em qualquer edição, inclusive na vigente, porque designar e semear
  não altera certificado já emitido.
- **O item "Unidades" saiu do menu** e a rota `/unidades` deixou de existir. A
  página de uma unidade (`/unidades/{id}`) ficou: é onde se vê a lotação do RH e
  se cadastra todo mundo de uma vez, útil quando alguém fica de fora da
  semeadura por não ter e-mail no RH. É aberta pelo nome da unidade na aba
  "Servidores por unidade", e o "Voltar" volta para de onde se veio.

## Uma correção que apareceu no caminho

`UsuarioAutenticado.ehAdministrador()` olhava só o papel ADMINISTRADOR, embora as
authorities já tratassem SUPERADMIN como administrador. Um superadministrador
puro, portanto, não passava na guarda da semeadura que ele mesmo disparava ao
designar. Passou a considerar os dois papéis.

## Verificação

- `ResponsavelPelaUnidadeIT.designacaoSemeiaALista`: designa numa unidade **não
  reconhecida** na edição, confere que a lista foi semeada, que o designado passa
  a vê-la e que a contagem aparece em `GET /api/unidades?edicaoId=`.
- `CsvDeResponsaveis.test.tsx`: o envio manda `edicaoId` e o relatório diz
  quantas listas foram semeadas e quantos servidores entraram.
- No navegador, com a API local: subir uma planilha de duas linhas na edição
  vigente designou o responsável, criou o magistrado, gravou o selo e semeou a
  lista; a linha com código inexistente voltou no relatório com o motivo.

## Um caminho que não deu certo

A primeira tentativa recriou a tela de unidades como aba da edição, com as 190
linhas e o Designar/Trocar/Retirar. Estava fiel ao que existia e errado como
produto: as unidades entram pela sincronização, e as que interessam são as que a
planilha nomeia. Listar todas para designar uma é ruído. Ficou só o CSV, e a
designação passou a vir sempre dele.

## O que ficou para depois

- **Planilha grande vai demorar.** Cada linha faz uma chamada ao RH; com centenas
  de unidades, a importação passa a ser operação de minutos, no mesmo padrão da
  "Atualizar base de usuários" — que roda em segundo plano. Se a planilha real do
  tribunal vier inteira, vale mover a importação para o mesmo mecanismo.
- **Retirar a designação não apaga a lista.** É deliberado: quem foi habilitado
  continua habilitado, e tirar alguém da lista segue sendo ato explícito. Mas
  ninguém pediu isso de um jeito nem de outro — vale confirmar com o negócio.
