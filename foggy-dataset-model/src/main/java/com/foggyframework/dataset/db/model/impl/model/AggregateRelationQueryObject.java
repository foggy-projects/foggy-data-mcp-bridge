package com.foggyframework.dataset.db.model.impl.model;

import java.util.List;

/**
 * Query-time pushdown hook for generated aggregate relation query objects.
 */
public interface AggregateRelationQueryObject {

    String REASON_OR_CONDITION_OUTER_ONLY = "OR_CONDITION_OUTER_ONLY";
    String REASON_NULL_CHECK_OUTER_ONLY = "NULL_CHECK_OUTER_ONLY";
    String REASON_UNSUPPORTED_OPERATOR = "UNSUPPORTED_OPERATOR";
    String REASON_EMPTY_IN_VALUES = "EMPTY_IN_VALUES";
    String REASON_INVALID_RANGE_VALUE = "INVALID_RANGE_VALUE";
    String REASON_NULL_VALUE_UNSUPPORTED = "NULL_VALUE_UNSUPPORTED";
    String REASON_NO_AGGREGATE_EXPRESSION = "NO_AGGREGATE_EXPRESSION";
    String REASON_NO_JOIN_KEY_MAPPING = "NO_JOIN_KEY_MAPPING";

    void clearAggregateRelationPushdowns();

    void resetAggregateRelationProjection();

    void setAggregateRelationProjectionPruningEnabled(boolean enabled);

    void markAggregateRelationOutput(AggregateRelationOutputColumn column);

    void markAggregateRelationOutputAlias(String alias);

    boolean pushAggregateRelationCondition(AggregateRelationOutputColumn column, String op, Object value);

    boolean pushAggregateRelationJoinKeyCondition(String leftFieldName, String op, Object value);

    void recordAggregateRelationRetainedCondition(String fieldName, String op, String reasonCode);

    List<AggregateRelationDiagnostic> getAggregateRelationDiagnostics();
}
