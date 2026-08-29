package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.expression.TotalExpressionNode;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.engine.total.TotalDataAggregatePlan;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreAggregationInterceptorAverageTest {

    @Test
    void algebraicTotalSelectsAverageStateRewriteThroughInterceptor() {
        DbColumn averageMeasure = mock(DbColumn.class);
        when(averageMeasure.isMeasure()).thenReturn(true);
        when(averageMeasure.getName()).thenReturn("averageAmount");
        when(averageMeasure.getAlias()).thenReturn("averageAmount");
        when(averageMeasure.getAggregation()).thenReturn(DbAggregation.AVG);

        JdbcQuery jdbcQuery = new JdbcQuery();
        jdbcQuery.setSelect(jdbcQuery.new JdbcSelect());
        jdbcQuery.getSelect().setColumns(List.of(averageMeasure));

        PreAggregation preAgg = mock(PreAggregation.class);
        when(preAgg.isEnabled()).thenReturn(true);
        when(preAgg.getName()).thenReturn("daily_average_amount");
        when(preAgg.getTableName()).thenReturn("preagg_daily_average_amount");
        when(preAgg.getFilters()).thenReturn(List.of());
        when(preAgg.getDimensionNames()).thenReturn(Set.of());
        when(preAgg.getMeasureAggregations()).thenReturn(
                Map.of("averageAmount", DbAggregation.AVG));
        when(preAgg.getMeasureColumnNames()).thenReturn(
                Map.of("averageAmount", "average_amount_avg"));
        when(preAgg.hasMeasure("averageAmount")).thenReturn(true);

        TableModel tableModel = mock(TableModel.class);
        when(tableModel.getPreAggregations()).thenReturn(List.of(preAgg));

        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getJdbcModel()).thenReturn(tableModel);
        when(queryModel.getDialect()).thenReturn(FDialect.MYSQL_DIALECT);
        when(queryModel.findJdbcColumnForCond(
                eq("averageAmount"), anyBoolean(), anyBoolean()))
                .thenReturn(averageMeasure);

        TotalExpressionNode average = TotalExpressionNode.aggregate(
                "AVG", BoundSqlExpression.of("fact.amount"));
        TotalDataAggregatePlan.Builder planBuilder = new TotalDataAggregatePlan.Builder();
        planBuilder.addPublicExpression("averageAmount", average);
        planBuilder.bindLeaves("averageAmount", average, averageMeasure.getType());

        JdbcModelQueryEngine queryEngine = mock(JdbcModelQueryEngine.class);
        when(queryEngine.getJdbcQuery()).thenReturn(jdbcQuery);
        when(queryEngine.isAlgebraicAggSql()).thenReturn(true);
        when(queryEngine.getTotalDataAggregatePlan()).thenReturn(planBuilder.build());

        PreAggQueryRewriter.PreAggAggregateSqlResult result =
                new PreAggregationInterceptor(null).tryBuildAggregateSql(
                        queryEngine, queryModel, new DbQueryRequestDef());

        assertNotNull(result);
        assertTrue(result.getSql().contains("SUM(pa.average_amount_avg__sum)"));
        assertTrue(result.getSql().contains("SUM(pa.average_amount_avg__count)"));
    }
}
