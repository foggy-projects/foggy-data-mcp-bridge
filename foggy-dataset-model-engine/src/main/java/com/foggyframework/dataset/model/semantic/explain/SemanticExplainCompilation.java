package com.foggyframework.dataset.model.semantic.explain;

import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;

import java.util.List;

/** Internal evidence returned by the existing semantic compiler in explain mode. */
public record SemanticExplainCompilation(
        DbQueryRequestDef normalizedRequest,
        ManagedSqlRelation managedRelation,
        ModelResultContext modelResultContext,
        SqlEvidence sqlEvidence,
        RoutingEvidence routingEvidence
) {

    public record SqlEvidence(
            String logicalSql,
            String finalPhysicalSql,
            List<Object> parameters,
            String sourceOfTruth,
            SemanticExplainResponse.Confidence confidence
    ) {
        public SqlEvidence {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }
    }

    public record RoutingEvidence(
            SemanticExplainResponse.StageStatus status,
            String route,
            String preAggregation,
            String decision,
            String reasonCode
    ) {
    }
}
