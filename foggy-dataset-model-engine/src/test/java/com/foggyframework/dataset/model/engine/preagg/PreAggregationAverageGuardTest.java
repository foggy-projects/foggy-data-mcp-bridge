package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.support.AggregationDbColumn;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreAggregationAverageGuardTest {

    @Test
    void detectsAverageThatCannotBeReaggregatedFromOnePhysicalPreAggColumn() {
        JdbcQuery query = queryWith(new AggregationDbColumn(
                null, "averageAmount", "avg(t.amount)", DbColumnType.NUMBER, DbAggregation.AVG));

        assertTrue(PreAggregationInterceptor.containsNonDecomposableAverage(query));
    }

    @Test
    void leavesDecomposableAggregationsEligibleForPreAggregation() {
        JdbcQuery query = queryWith(
                new AggregationDbColumn(
                        null, "amount", "sum(t.amount)", DbColumnType.NUMBER, DbAggregation.SUM),
                new AggregationDbColumn(
                        null, "count", "count(t.id)", DbColumnType.NUMBER, DbAggregation.COUNT),
                new AggregationDbColumn(
                        null, "minimum", "min(t.amount)", DbColumnType.NUMBER, DbAggregation.MIN),
                new AggregationDbColumn(
                        null, "maximum", "max(t.amount)", DbColumnType.NUMBER, DbAggregation.MAX));

        assertFalse(PreAggregationInterceptor.containsNonDecomposableAverage(query));
    }

    @Test
    void detectsCalculatedAverageWithoutChangingCalculatedColumnSpiAggregation() {
        SqlFragment argument = SqlFragment.ofLiteral("t.amount", DbColumnType.NUMBER);
        CalculatedDbColumn calculated = new CalculatedDbColumn(
                "calculatedAverage", "calculatedAverage",
                SqlFragment.function("AVG", List.of(argument)));
        JdbcQuery query = new JdbcQuery();
        query.setSelect(query.new JdbcSelect());
        query.getSelect().setColumns(List.of(calculated));

        assertTrue(PreAggregationInterceptor.containsNonDecomposableAverage(query));
    }

    private JdbcQuery queryWith(AggregationDbColumn... columns) {
        JdbcQuery query = new JdbcQuery();
        query.setSelect(query.new JdbcSelect());
        query.getSelect().setColumns(List.of(columns));
        return query;
    }
}
