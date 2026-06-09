package com.foggyframework.dataset.db.model.impl.model;

/**
 * Query-time pushdown hook for generated aggregate relation query objects.
 */
public interface AggregateRelationQueryObject {

    void clearAggregateRelationPushdowns();

    void setAggregateRelationProjectionPruningEnabled(boolean enabled);

    void markAggregateRelationOutput(AggregateRelationOutputColumn column);

    void markAggregateRelationOutputAlias(String alias);

    boolean pushAggregateRelationCondition(AggregateRelationOutputColumn column, String op, Object value);

    boolean pushAggregateRelationJoinKeyCondition(String leftFieldName, String op, Object value);
}
