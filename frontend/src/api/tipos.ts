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
export interface Sessao {
  token: string
  expiraEmSegundos: number
  email: string
  nome: string
  papeis: Papel[]
}

export interface Identidade {
  email: string
  nome: string
  papeis: Papel[]
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
  /** Quem responde pela unidade; nulo enquanto ninguém foi designado. */
  responsavel: { id: number; nome: string; email: string } | null
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
  matricula: number | null
  nome: string
  /** Nulo quando nem o RH nem o AD têm endereço para a pessoa. A busca não
   *  consulta o AD; só a consulta por matrícula traz o e-mail definitivo. */
  email: string | null
  cpfMascarado: string | null
  /** Sem e-mail ninguém é reconhecido no login nem consegue emitir (DI-24). */
  temEmail: boolean
}
