package com.foggyframework.dataset.db.model.impl.model;

/**
 * Query-time pushdown hook for generated aggregate relation query objects.
 */
public interface AggregateRelationQueryObject {

    void clearAggregateRelationPushdowns();

    boolean pushAggregateRelationCondition(AggregateRelationOutputColumn column, String op, Object value);

    boolean pushAggregateRelationJoinKeyCondition(String leftFieldName, String op, Object value);
}
