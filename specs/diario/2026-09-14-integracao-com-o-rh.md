# 2026-09-14 — Integração com o RH (ConnectTJ/SIEDOS)

> Diário de implementação. A decisão e o porquê estão na DI-25; a spec, na
> feature 010. Aqui fica o que foi feito, o que a API respondeu e o que falta.

## O que a API respondeu (lida do Swagger, não deduzida)

`https://connecttj-api-stag.tjgo.jus.br/swagger-ui/index.html` — a especificação
em `/v3/api-docs` respondeu 200 e listou os endpoints. Duas descobertas mudaram
o plano:

1. **Existe `GET /api/v1/servidores/buscar-por-matricula`**, e ele devolve
   `endEmail`. Era a pergunta mais importante: sem ela, os lotados de uma
   unidade chegariam sem e-mail, e e-mail é a chave da pessoa aqui (DI-24). Com
   ela, a semeadura resolve o e-mail de cada matrícula e a lista nasce completa.
2. **Tudo exige Bearer**: sem token, os endpoints respondem 401 até em
   homologação. Então a integração só liga quando o client existir.

A API também tem `buscar-por-cpf`, `buscar-unidades-ativas` e
`unidades/{cod}/subordinadas`, que não foram usados agora mas resolvem casos
vizinhos.

## O que entrou

- **Migração 011:** `codigo_siedos` e `comarca` na unidade, `matricula` no
  servidor habilitado, `matricula` e `login_ad` no usuário. Todas anuláveis.
- **Porta ampliada:** hierarquia, detalhes da unidade, lotados (paginado),
  servidor por matrícula e por login. O `MockEgespClient` responde a todos, com
  códigos e matrículas estáveis — dá para exercitar a tela sem a API.
- **Adaptador real** com cache de token (5 min), renovação no vencimento e uma
  única retentativa no 401. Escolha entre real e mock na subida, com o motivo
  no log.
- **Motor de comparação** e endpoints da tela, exclusivos do superadministrador.
  Comparar é leitura; aplicar é um clique de cada vez.
- **Atualização no login**, assíncrona, por evento publicado no callback do SSO.
- **Importação da unidade inteira** (pedida depois, no mesmo dia): um botão traz
  todos os lotados, cria no cadastro de usuários quem ainda não existe — com
  papel de servidor — e habilita todos na edição. Não altera o papel de quem já
  está cadastrado (o RH sabe onde a pessoa trabalha, não o que ela pode fazer no
  prêmio) e não ressuscita quem foi removido da lista à mão; o resumo separa os
  números para que a diferença não pareça falha.

## Verificação

- API: 206 testes (eram 196). Novos: `SincronizacaoIT` (comparação não grava,
  casamento por nome e depois por código, inclusão pela matrícula com e-mail
  resolvido, órfão manual reconhecível, desvinculação lógica, acesso restrito) e
  `ConnectTjEgespClientTest` (paginação, 401 com renovação única, 404 vira
  vazio).
- **Testado contra a API real em 2026-09-15**, com o client `api-connect` no
  realm `DG-TST`: token emitido (o `/auth` também é obrigatório aqui),
  hierarquia da SGJT, lotados paginados e e-mail por matrícula. O teste vive em
  `ConnectTjRealIT`, desligado por padrão e habilitado só quando as variáveis
  existem no ambiente — nenhuma credencial entra no repositório.
- **Metade dos lotados não tem e-mail no RH.** Numa unidade de oito, cinco
  vieram sem: residentes, comissionados e um estatutário. O caminho de
  recuperação pelo AD (CPF → `samaccountname` → `login@tjgo.jus.br`) levou a
  cobertura a 100% nas duas unidades medidas. Sem ele, a maior parte da unidade
  ficaria sem poder emitir.
- **A API tem mais de um IP e um deles recusou conexão** durante os testes
  (`ECONNREFUSED` em 10.0.10.47, `ETIMEDOUT` em 10.0.10.74). Aconteceu uma vez,
  em chamada solta; vale observar se reaparece em varredura grande.

## Escolher o magistrado, em vez de digitar (2026-09-15)

O e-mail é a chave da pessoa, e o cadastro de reconhecidos pedia que ele fosse
digitado. Um erro de digitação ali só aparece meses depois, quando o magistrado
tenta emitir e não acha nada seu — e a edição já está publicada, onde editar é
proibido. Com a API no ar, o administrador passa a **escolher** a pessoa numa
busca por nome, e o sistema preenche e-mail e nome.

O nome continua editável: é ele que vai impresso no certificado, e o RH devolve
tudo em maiúsculas. Quem não tem e-mail nem no RH nem no AD não pode ser
cadastrado por aí, e a tela diz o porquê.

## As unidades judiciárias não ficam sob a Presidência (2026-09-15)

Com a integração ligada em homologação, a comparação a partir da Presidência
(`600000009`) trouxe 189 unidades — **todas administrativas**. As varas, que são
o objeto do prêmio, penduram na **comarca**, não na Presidência:

```
203010000  ABADIANIA                  ← comarca
  203010005  ABADIANIA VARA JUDICIAL  ← unidade judiciária
    203010002  ESCRIVANIA ...
```

Varrer comarca por comarca custaria umas 1.100 chamadas — três minutos no ritmo
de 6/s, e ainda estouraria o tempo limite da rota. A saída estava na própria
API: **`estrutura-hierarquica` sem o parâmetro `codigoUnidade` devolve o
organograma inteiro** — 2.215 unidades, 8 níveis, todas com código do pai, em
menos de um segundo e cerca de 1 MB. Entre elas, 759 unidades judiciárias.

Então o `codigo` virou opcional: sem ele, compara-se o tribunal todo. Os atalhos
de Presidência e SGJT saíram da tela — o primeiro enganava, por sugerir que
trazia o tribunal inteiro.

## Pendências

- **Com a infra/equipe da API:** client (id e secret) e o realm que emite o
  token; endereço da API em produção.
- ~~**Código da unidade raiz**~~ — respondido em 2026-09-15: `600000009`
  (Presidência) para a estrutura inteira, `901190605` (SGJT) para a área de
  tecnologia. Viraram atalho na tela, ao lado do campo de código, e a partir de
  13 unidades aparece um filtro por nome, comarca ou código — da Presidência
  vem o tribunal todo, e rolar a lista até achar uma vara não é caminho.
- ~~**`loginAd`**~~ — confirmado no mesmo dia: é sempre o prefixo do e-mail
  corporativo, inclusive para magistrados.
- **Sem agendamento:** a sincronização é manual, pela tela. Se o tribunal quiser
  varredura periódica, é decidir a janela e quem responde pelos conflitos.

## Ajustes de vocabulário e de busca na tela (2026-09-15)

Três correções pedidas depois de usar a tela com a API ligada:

- **"Comparar o tribunal inteiro" virou "Comparar a base completa do RH".** A
  consulta sem código não devolve o tribunal: devolve tudo que existe na base de
  unidades do RH. Chamar aquilo de tribunal dava a entender um recorte que o
  botão não faz.
- **O atalho do TJGO voltou**, agora ao lado do campo de código e rotulado com o
  que ele é: `600000009`, a estrutura administrativa, 189 unidades. Ele preenche
  o campo e não dispara a comparação. A dica embaixo diz que as varas não estão
  nesse ramo — que era exatamente o mal-entendido que levou a tirá-lo antes.
- **O filtro por nome, comarca ou código aparece sempre**, não só acima de 12
  unidades. O ramo do TJGO sozinho já traz 189 linhas, e quem compara está
  quase sempre atrás de uma.

## O carregando que não terminava (2026-09-15)

Em homologação, "Servidores da unidade" ficava com o aviso de erro em cima e o
"Carregando…" girando embaixo, para sempre. A causa era um padrão repetido em
sete telas: a lista começa em `null`, o `null` desenha o carregando, e o `catch`
só gravava a mensagem de erro — nunca tirava a lista do `null`. Quem olhava via
uma tela que parecia estar tentando de novo, e não estava.

O `carregando` passou a depender de não haver erro nas telas de unidades,
usuários, edições, certificados, sincronização e no painel da lista.

E o caso específico da tela do magistrado tinha um segundo problema: o 404 ali
só acontece por um motivo — nenhuma edição marcada como vigente —, e isso não é
falha, é estado do prêmio. Agora tem explicação própria, sem tarja vermelha.

## Cargas em lote: unidades, lotados e responsáveis (2026-09-16)

Com o organograma inteiro na tela — 2.215 unidades —, todo botão que agia numa
linha por vez deixou de resolver. Três ações em lote entraram:

- **Cadastrar as unidades que só existem no RH.** Usa o escopo da comparação que
  está na tela, e não o que o filtro deixou visível: filtro é lupa, não seleção.
  Só cria. Não renomeia unidade existente nem apaga órfã — isso muda o nome
  impresso em certificado e continua sendo decisão linha a linha. A única coisa
  que faz numa unidade existente é gravar o código do SIEDOS quando faltava, que
  é o que tira do limbo a unidade digitada à mão.
- **Cadastrar os lotados de uma unidade como usuários**, com a lotação gravada,
  sem edição nenhuma no meio. A pergunta aqui é "quem trabalha aqui e já existe
  no sistema", e não "quem pode emitir nesta edição" — confundir as duas era o
  que obrigava a escolher uma edição só para ver a lotação de uma unidade.
- **Associar os responsáveis a partir do RH.** Quem responde por cada unidade já
  está lá; digitar isso unidade por unidade não é caminho.

**Duas decisões que o lote dos responsáveis obrigou a tomar.** A designação
exige o papel de magistrado — é a tela dele que ela destrava (008) —, então quem
o RH aponta e ainda não o tem **ganha o papel**. É concessão de acesso, e o
resumo diz quantas foram; concessão silenciosa não existe. E designação já feita
**não é trocada**: foi ato de um superadministrador, e o RH não desfaz decisão
humana.

**Rodadas, e não uma varredura só.** O responsável custa uma chamada ao RH por
unidade, com teto de 6/s: o tribunal inteiro daria minutos numa requisição só, e
a rota cairia antes. A tela chama em rodadas de 50, seguindo um cursor por id. O
cursor não é detalhe de paginação: sem ele, as unidades que o RH não sabe
responder continuariam sem responsável e seriam sorteadas de novo a cada rodada,
para sempre.

**No mock.** O `MockEgespClient` não respondia `responsavelDaUnidade` — a rotina
passaria nos testes sem nunca ter designado ninguém. Agora o primeiro lotado
responde pela unidade: arbitrário, mas determinístico.

## A planilha de responsáveis, e a página de cada unidade (2026-09-16)

Duas coisas entraram, e uma saiu.

**Saiu** a designação de responsáveis a partir do RH, escrita algumas horas
antes. A fonte está errada: quem responde por uma unidade no organograma
corporativo não é necessariamente quem responde por ela **no prêmio** — e o
selo, o RH não tem de jeito nenhum. Quem premia é a comissão, e o sistema nunca
calcula vencedor (constituição, princípio 2).

**Entrou** a planilha `nome;email;unidade;selo`, na tela de Unidades. A unidade
vem pelo **código do SIEDOS** (o nome exato também é aceito) e o selo entre
bronze, prata, ouro e diamante. Cada linha faz três coisas de uma vez, porque as
três vêm juntas no arquivo: garante o usuário magistrado, designa quem responde
pela unidade e grava o reconhecimento com o selo na edição.

- **Linha ruim não derruba o lote.** Ela volta no relatório com o texto original
  e o motivo; as demais são gravadas. Planilha de tribunal chega com unidade
  extinta e linha em branco no meio, e recusar tudo obrigaria a refazer o arquivo
  para corrigir um nome.
- **Reenviar não duplica.** Quem já respondia pela unidade conta como "já era", e
  o selo que já estava lá não é gravado de novo.
- **Duas consequências ficam visíveis no resumo**, em vez de silenciosas: quem já
  existia sem o papel de magistrado ganha o papel — que a designação exige, por
  ser a tela dele que ela destrava — e o responsável anterior, quando havia, é
  substituído. A planilha é ato humano; ela troca o que estava lá.
- **Modelo de teste gerado da própria base:** um botão baixa um CSV com os
  códigos reais das unidades da tela e nomes fictícios em `.example`. Exemplo com
  código inventado falharia em todas as linhas e não provaria nada.

**Entrou também a página de cada unidade** (`/unidades/:id`), alcançada clicando
no nome na lista. Ela responde, sem edição escolhida, a pergunta que a tela de
sincronização só respondia dentro de uma edição: *quem trabalha aqui, e quem
desses já existe no sistema?* Puxar do RH é leitura; cadastrar cria os que faltam
com papel de servidor e grava a lotação — e **não** habilita ninguém a emitir,
que continua sendo a lista datada de uma edição (princípio 3b).

**O parsing de CSV virou um `LeitorCsv` compartilhado.** Separador, BOM e aspas
eram os mesmos nas duas planilhas; só o significado das colunas difere.
