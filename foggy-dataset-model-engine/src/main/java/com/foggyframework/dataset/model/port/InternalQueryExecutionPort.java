package com.foggyframework.dataset.model.port;

import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;

/**
 * Internal execution port for callers that must participate in the complete
 * query lifecycle while retaining engine context or generated SQL.
 *
 * <p>This is not part of the stable external query API. Controllers and addons
 * should use {@link com.foggyframework.dataset.model.api.QueryFacade}.</p>
 *
 * @since 9.3.5
 */
public interface InternalQueryExecutionPort {

    DbQueryResult queryModelResult(ModelResultContext context);

    SqlGenerationResult buildSqlOnly(ModelResultContext context);

    /** Implementations must prove result-processing identity before returning Compose SQL. */
    default SqlGenerationResult buildComposeSqlOnly(ModelResultContext context) {
        throw new UnsupportedOperationException("COMPOSE_RESULT_GOVERNANCE_UNPROVEN");
    }
}
