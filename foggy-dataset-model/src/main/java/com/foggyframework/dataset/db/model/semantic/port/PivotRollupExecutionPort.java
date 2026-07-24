package com.foggyframework.dataset.db.model.semantic.port;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

import java.util.List;
import java.util.Map;

/**
 * Governed semantic operations required by Pivot rollup execution.
 */
public interface PivotRollupExecutionPort extends SemanticQueryExecutionPort {

    SemanticSqlGeneration generateRollupSql(String model, SemanticQueryRequest request,
                                             SemanticRequestContext context);

    List<Map<String, Object>> executeRollupSql(String sql, List<Object> params, String routeModel);
}
