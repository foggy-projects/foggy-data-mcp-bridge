package com.foggyframework.dataset.model.impl.model;

import com.foggyframework.dataset.model.spi.DbColumn;

/**
 * Metadata exposed by generated aggregate relation columns.
 */
public interface AggregateRelationOutputColumn {

    boolean isAggregateRelationGroupKey();

    boolean isAggregateRelationMeasure();

    DbColumn getAggregateRelationSourceColumn();

    String getAggregateRelationSourceExpression();

    String getAggregateRelationAggregateExpression();

    boolean pushAggregateRelationCondition(String op, Object value);
}
