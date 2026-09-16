import type { Semeadura } from '../../api/tipos'

/**
 * Uma linha com o que a semeadura fez. Compartilhada entre a aba do
 * administrador e a tela do magistrado, para as duas contarem a mesma história.
 */
export function resumoDaSemeadura(dados: Semeadura): string {
  return [
    `${dados.retornadosPeloEgesp} no EGESP`,
    `${dados.incluidos} incluído(s)`,
    `${dados.jaExistentes} já constavam`,
    dados.preservadosRemovidos > 0
      ? `${dados.preservadosRemovidos} removido(s) preservados fora da lista`
      : null,
    dados.ignoradosSemEmail > 0
      ? `${dados.ignoradosSemEmail} sem e-mail no EGESP, não incluído(s)`
      : null,
    `total ativo: ${dados.totalAtivos}`,
  ]
    .filter(Boolean)
    .join(' · ')
}
