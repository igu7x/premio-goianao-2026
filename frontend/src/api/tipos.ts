/** Espelho dos contratos do backend. Mantido manualmente e de proposito:
 *  sao poucos tipos e revisar cada mudanca a mao evita que o frontend passe a
 *  depender de campos que a API nunca prometeu. */

export type Papel = 'SUPERADMIN' | 'ADMINISTRADOR' | 'MAGISTRADO' | 'SERVIDOR'

export type Selo = 'BRONZE' | 'PRATA' | 'OURO' | 'DIAMANTE'

export type TipoCertificado = 'MAGISTRADO' | 'SERVIDOR'

export type StatusEdicao = 'RASCUNHO' | 'PUBLICADA'

export type Alinhamento = 'ESQUERDA' | 'CENTRO' | 'DIREITA'

export type OrigemServidor = 'EGESP' | 'MANUAL'

/** O e-mail corporativo é a chave da pessoa em todo o sistema (DI-24). */
/**
 * Uma edição como a sessão a enxerga (feature 011). Cada edição tem a sua
 * base: os papéis aqui são os que a pessoa tem naquela edição.
 */
export interface EdicaoDaSessao {
  id: number
  ano: number
  vigente: boolean
  papeis: Papel[]
}

export interface Sessao {
  token: string
  expiraEmSegundos: number
  email: string
  nome: string
  papeis: Papel[]
  edicao: EdicaoDaSessao | null
  edicoesDisponiveis: EdicaoDaSessao[]
}

export interface Identidade {
  email: string
  nome: string
  /** Papéis na edição da sessão — mudam quando a edição muda. */
  papeis: Papel[]
  /** Sobre qual edição (e qual base) o sistema inteiro está agindo. */
  edicao: EdicaoDaSessao | null
  /** As edições em que esta pessoa existe, a vigente primeiro. */
  edicoesDisponiveis: EdicaoDaSessao[]
}

export interface UsuarioMock {
  email: string
  nome: string
  papeis: Papel[]
}

export interface Edicao {
  id: number
  ano: number
  descricao: string | null
  status: StatusEdicao
  vigente: boolean
  emitivel: boolean
  aceitaInclusoes: boolean
  criadoEm: string
  atualizadoEm: string | null
}

export interface AreaTexto {
  x: number
  y: number
  largura: number
  altura: number
  alinhamento: Alinhamento
}

export interface AreaQr {
  x: number
  y: number
  tamanho: number
}

export interface AreaCodigo extends AreaTexto {
  qr: AreaQr | null
}

export interface Layout {
  id: number
  edicaoId: number
  selo: Selo
  tipo: TipoCertificado
  imagemLargura: number
  imagemAltura: number
  imagemUrl: string
  areaNome: AreaTexto
  areaUnidade: AreaTexto
  areaCodigo: AreaCodigo
  atualizadoEm: string | null
}

export interface LayoutsDaEdicao {
  layouts: Layout[]
  pendencias: string[]
  editavel: boolean
  fonteInstitucionalDisponivel: boolean
}

/**
 * Relatório da planilha de magistrados responsáveis (`nome;email;unidade;selo`).
 *
 * Uma linha ruim não derruba o lote: volta em `erros`, com o texto original, e
 * as demais são gravadas.
 */
export interface ImportacaoResponsaveis {
  linhasLidas: number
  designados: number
  usuariosCriados: number
  /** Já existiam sem o papel de magistrado, que a designação exige. */
  papelConcedido: number
  substituidos: number
  jaEram: number
  /** Selos gravados como reconhecimento; repetir a planilha não conta de novo. */
  reconhecimentos: number
  /** Unidades que entraram na fila de semeadura, que roda em segundo plano. */
  listasParaSemear: number
  edicaoAno: number
  erros: ErroDeLinha[]
}

/** Uma pessoa que o RH aponta como lotada na unidade, cruzada com o cadastro. */
export interface LotadoDoRh {
  matricula: number | null
  nome: string
  /** Nulo quando não há e-mail nem no RH nem no AD. */
  email: string | null
  semEmail: boolean
  jaCadastrada: boolean
  /** O usuário existe e já aponta para esta unidade. */
  lotacaoCerta: boolean
}

/** Resumo do cadastro dos lotados de uma unidade. */
export interface LotacaoAplicada {
  lotadosNoRh: number
  criados: number
  atualizados: number
  /** Ficaram de fora: sem e-mail o login não reconheceria a pessoa. */
  semEmail: number
}

/** Resultado do cadastro em lote das unidades que só existiam no RH. */
export interface CadastroEmLote {
  criadas: number
  jaExistiam: number
  /** Já existiam pelo nome e passaram a ter o código do SIEDOS. */
  casadas: number
}

/** Resultado de preencher a edição com as artes padrão do prêmio. */
export interface ArtesPadraoAplicadas {
  criados: number
  jaExistentes: number
}

export type EscopoDaAtualizacao = 'TJGO' | 'COMPLETA'

/**
 * Retrato da atualização da base de usuários pelo RH, que roda em segundo plano.
 * A tela dispara e depois só pergunta como está.
 */
export interface SituacaoDaAtualizacao {
  estado: 'NUNCA_EXECUTADA' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'FALHOU'
  escopo: EscopoDaAtualizacao | null
  unidadesTotal: number
  unidadesProcessadas: number
  /** A que está sendo varrida agora: o sinal de vida de uma operação longa. */
  unidadeAtual: string | null
  pessoas: number
  criados: number
  atualizados: number
  /** Ficaram de fora: sem e-mail no RH nem no AD, o login não as reconheceria. */
  semEmail: number
  unidadesComFalha: number
  iniciadaEm: string | null
  terminadaEm: string | null
  mensagem: string | null
}

/** Unidade cadastrada, só com o que um formulário precisa para oferecê-la. */
export interface UnidadeCadastrada {
  id: number
  nome: string
  codigoSiedos: number | null
  comarca: string | null
}

export interface UnidadeEgesp {
  nome: string
  comarca: string
  unidadeId: number | null
  jaCadastrada: boolean
}

export interface Unidade {
  id: number
  nome: string
}

export interface Reconhecimento {
  id: number
  unidadeId: number
  unidadeNome: string
  selo: Selo
}

export interface Magistrado {
  id: number
  email: string
  /** Opcional, só informativo. */
  cpf: string | null
  cpfFormatado: string | null
  nome: string
  reconhecimentos: Reconhecimento[]
}

export interface UnidadeReconhecida {
  unidadeId: number
  nome: string
  selos: Selo[]
  maiorSelo: Selo | null
  magistrados: number
  servidoresHabilitados: number
}

export interface ErroDeLinha {
  linha: number
  conteudo: string
  motivo: string
}

export interface RelatorioImportacao {
  linhasLidas: number
  magistradosCriados: number
  reconhecimentosCriados: number
  criados: string[]
  erros: ErroDeLinha[]
}

export interface ServidorHabilitado {
  id: number
  /** Só vem preenchido para quem pode editar a lista; caso contrário, apenas o mascarado. */
  email: string | null
  emailMascarado: string
  /** Opcional; quando existe, sempre mascarado. */
  cpfMascarado: string | null
  nome: string
  origem: OrigemServidor
  ativo: boolean
  criadoEm: string
  atualizadoEm: string | null
}

export interface ListaHabilitados {
  edicaoId: number
  edicaoAno: number
  unidadeId: number
  unidadeNome: string
  podeEditar: boolean
  podeSemear: boolean
  servidores: ServidorHabilitado[]
}

export interface Semeadura {
  retornadosPeloEgesp: number
  incluidos: number
  jaExistentes: number
  preservadosRemovidos: number
  /** Vieram do EGESP sem e-mail: sem ele a pessoa não seria reconhecida no login. */
  ignoradosSemEmail: number
  totalAtivos: number
}

export interface EdicaoOpcao {
  id: number
  ano: number
  descricao: string | null
  vigente: boolean
}

export interface OpcaoEmissao {
  unidadeId: number
  unidadeNome: string
  selo: Selo
  layoutDisponivel: boolean
  jaEmitido: boolean
  codigoValidacao: string | null
  emitidoEm: string | null
  totalEmissoes: number
}

export interface ResumoEdicao {
  edicaoId: number
  ano: number
  status: StatusEdicao
  vigente: boolean
  layoutsConfigurados: number
  layoutsPendentes: string[]
  magistradosReconhecidos: number
  unidadesReconhecidas: number
  servidoresHabilitados: number
  certificadosEmitidos: number
}

export interface Verificacao {
  valido: boolean
  codigo: string
  nome: string | null
  unidade: string | null
  edicaoAno: number | null
  selo: Selo | null
  tipo: TipoCertificado | null
  emitidoEm: string | null
}

/** Usuário do sistema, como o cadastro do superadministrador o devolve. */
export interface Usuario {
  id: number
  /** Nulo quando o CPF (opcional) não foi informado. */
  cpfMascarado: string | null
  nome: string
  email: string
  unidadeLotacao: string | null
  areaAtuacao: string | null
  papeis: Papel[]
  ativo: boolean
  /** Falso para quem só entrará pelo SSO. */
  temSenha: boolean
  criadoEm: string
}

/** Unidade judiciária no cadastro do superadministrador. */
export interface Unidade {
  id: number
  nome: string
  ativo: boolean
  /** Código no SIEDOS; nulo enquanto a unidade não foi casada com o RH. */
  codigoSiedos: number | null
  comarca: string | null
  /** Quem responde pela unidade; nulo enquanto ninguém foi designado. */
  responsavel: { id: number; nome: string; email: string } | null
  /** Habilitados nesta unidade na edição consultada; nulo fora de uma edição. */
  habilitados: number | null
}

/** Como vai a semeadura em lote disparada pela planilha de responsáveis. */
export interface SituacaoDaSemeadura {
  estado: 'NUNCA_EXECUTADA' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'FALHOU'
  edicaoAno: number | null
  unidadesTotal: number
  unidadesProcessadas: number
  /** A que está sendo semeada agora; é o sinal de vida da operação. */
  unidadeAtual: string | null
  incluidos: number
  jaExistentes: number
  semEmail: number
  unidadesComFalha: number
  ultimaFalha: string | null
  iniciadaEm: string | null
  terminadaEm: string | null
  mensagem: string | null
}

/**
 * Resultado de designar quem responde pela unidade.
 *
 * Designar semeia a lista de habilitados no mesmo ato; `semeadura` é nula
 * quando ela não aconteceu, e aí `aviso` diz por quê — a designação em si foi
 * gravada de todo jeito.
 */
export interface Designacao {
  unidade: Unidade
  semeadura: Semeadura | null
  aviso: string | null
}

/* ------------------------------------------------------------------ */
/* Sincronização com o RH (feature 010)                                */
/* ------------------------------------------------------------------ */

/** Como cada linha se compara com o RH. Cada situação pede uma ação diferente
 *  — nenhuma, corrigir, criar ou desvincular. */
export type ItemSincronizacao = 'SINCRONIZADO' | 'DESATUALIZADO' | 'SO_NA_API' | 'ORFAO'

/** Só diz ligada ou desligada: endereço e credencial da API corporativa não
 *  passam pelo navegador. */
export interface SituacaoIntegracao {
  ligada: boolean
  origemDosDados: string
}

export interface UnidadeComparada {
  situacao: ItemSincronizacao
  codigo: number | null
  /** Nulo quando a unidade só existe no RH. */
  unidadeId: number | null
  /** O nome gravado aqui — é ele que sai impresso no certificado. */
  nomeNoSistema: string | null
  nomeNaApi: string | null
  comarca: string | null
  /** Superior imediato no organograma. Nulo nos órfãos, que não vieram do RH. */
  codigoPai: number | null
  nomePai: string | null
  /** Profundidade informada pelo RH (1 = raiz). Espelhado porque o backend o
   *  envia; a tela hoje não o usa. */
  nivel: number | null
}

export interface ServidorComparado {
  situacao: ItemSincronizacao
  matricula: number | null
  nome: string
  /** Inteiro: a tela é exclusiva do superadministrador, que já pode editar a lista (DI-10). */
  email: string | null
  /** O que a desvinculação usa — dado pessoal não vai na URL. */
  servidorHabilitadoId: number | null
  /** Nulo quando a pessoa ainda não está na lista da edição. */
  origem: OrigemServidor | null
  /** Sem e-mail no RH ninguém é reconhecido no login (DI-24). */
  semEmailNaApi: boolean
}

export interface ComparacaoServidores {
  unidadeId: number
  unidadeNome: string
  codigo: number | null
  responsavelSugerido: string | null
  servidores: ServidorComparado[]
}

/**
 * Resultado da importação da unidade inteira.
 *
 * Os números são o que permite conferir a conta depois: `preservadosRemovidos`
 * e `semEmail` explicam por que o total de habilitados não bate com o de
 * lotados no RH.
 */
export interface ImportacaoDaUnidade {
  lotadosNoRh: number
  /** Não existiam no cadastro de usuários e foram criados com papel de servidor. */
  usuariosCriados: number
  /** Já existiam; receberam os dados do RH — o papel deles não muda. */
  usuariosAtualizados: number
  habilitadosIncluidos: number
  jaHabilitados: number
  /** Removidos à mão antes: a importação não os ressuscita. */
  preservadosRemovidos: number
  /** Sem e-mail corporativo no RH — não seriam reconhecidos no login (DI-24). */
  semEmail: number
  totalAtivos: number
}

/* ------------------------------------------------------------------ */
/* Pessoas do RH (escolha no cadastro de reconhecidos)                 */
/* ------------------------------------------------------------------ */

/** Falso quando a integração com o RH não está configurada e o sistema roda
 *  com dados de demonstração — aí o e-mail volta a ser digitado. */
export interface SituacaoDoRh {
  disponivel: boolean
}

export interface PessoaDoRh {
  /** De onde a pessoa veio na busca. Quem já está no sistema tem o e-mail certo
   *  e dispensa a consulta ao RH na escolha. Ausente na consulta por matrícula. */
  origem?: 'SISTEMA' | 'RH'
  matricula: number | null
  nome: string
  /** Nulo quando nem o RH nem o AD têm endereço para a pessoa. A busca não
   *  consulta o AD; só a consulta por matrícula traz o e-mail definitivo. */
  email: string | null
  cpfMascarado: string | null
  /** Sem e-mail ninguém é reconhecido no login nem consegue emitir (DI-24). */
  temEmail: boolean
}

/** Busca de pessoas no sistema e no RH, numa resposta só. */
export interface ResultadoDaBusca {
  pessoas: PessoaDoRh[]
  /** Falso quando o RH não respondeu: a lista traz só quem está no sistema. */
  rhRespondeu: boolean
}
