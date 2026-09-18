package br.jus.tjgo.goianao.edicao.base;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Diz ao Hibernate, a cada sessao aberta, sobre qual edicao ela age (011/RF-4).
 *
 * <p>A resposta vem de {@link EdicaoCorrente}: o schema que o filtro da
 * requisicao declarou ou, na falta dele, o da edicao vigente.
 */
public class ResolvedorDeEdicao implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = EdicaoCorrente.schema();
        if (schema == null) {
            // Acontece antes de a base estar pronta na subida. Deixar passar
            // significaria consultar o schema errado sem aviso; falhar aqui
            // aponta o problema no lugar onde ele comeca.
            throw new IllegalStateException(
                    "Nenhuma edição no contexto e nenhuma edição vigente definida. "
                    + "A base por edição (" + AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER
                    + ") não foi inicializada.");
        }
        return schema;
    }

    /**
     * Sim: uma sessao aberta na edicao de 2026 nao pode continuar sendo usada
     * depois que a thread passou a agir sobre 2027. Validar torna isso um erro
     * imediato em vez de uma leitura silenciosa na base errada.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
