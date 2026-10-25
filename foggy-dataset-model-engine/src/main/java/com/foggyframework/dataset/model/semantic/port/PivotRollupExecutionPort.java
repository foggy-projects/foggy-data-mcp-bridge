package com.foggyframework.dataset.model.semantic.port;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

import java.util.List;
import java.util.Map;

/**
 * Governed semantic operations required by Pivot rollup execution.
 */
public interface PivotRollupExecutionPort extends SemanticQueryExecutionPort {

    /** Raw UNION execution may skip process only with an explicit result-identity proof. */
    default boolean supportsProcessedRollupBatch() {
        return false;
    }

    SemanticSqlGeneration generateRollupSql(String model, SemanticQueryRequest request,
                                             SemanticRequestContext context);

    List<Map<String, Object>> executeRollupSql(String sql, List<Object> params, String routeModel);

    /** Legacy adapters fall back to governed serial queries; never route a batch using ambient state. */
    default List<Map<String, Object>> executeBoundRollupSql(
            String sql,
            List<Object> params,
            String model,
            SemanticRequestContext context,
            com.foggyframework.dataset.model.spi.QueryModel binding) {
        throw new UnsupportedOperationException("Pinned rollup execution unavailable");
    }
}
