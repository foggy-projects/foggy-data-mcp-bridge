package com.foggyframework.dataset.model.port;

import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedRelationOptions;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;

import java.util.List;

/**
 * Advanced port for preparing a governed relation and executing a planner's
 * final SQL against the same pinned model and lifecycle context.
 *
 * @since 9.3.5
 */
public interface ManagedRelationExecutionPort {

    ManagedSqlRelation prepareManagedRelation(ModelResultContext context, ManagedRelationOptions options);

    DbQueryResult executeManagedRelation(ManagedSqlRelation relation, String finalSql, List<Object> finalParams);
}
