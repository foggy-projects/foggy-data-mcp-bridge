package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.expression.TotalExpressionNode;
import com.foggyframework.dataset.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.engine.total.TotalDataAggregatePlan;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreAggQueryRewriterAverageTest {
    private JdbcQueryModel queryModel;
    private PreAggregation preAgg;
    private DbColumn averageMeasure;
    private DbColumn yearDimension;
    private PreAggQueryRewriter rewriter;

    @BeforeEach
    void setUp() {
        queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getDialect()).thenReturn(FDialect.MYSQL_DIALECT);

        preAgg = mock(PreAggregation.class);
        when(preAgg.getName()).thenReturn("daily_average_amount");
        when(preAgg.getTableName()).thenReturn("preagg_daily_average_amount");
        when(preAgg.getSchema()).thenReturn(null);
        when(preAgg.getMeasureAggregations()).thenReturn(
                Map.of("averageAmount", DbAggregation.AVG));
        when(preAgg.getMeasureColumnNames()).thenReturn(
                Map.of("averageAmount", "average_amount_avg"));
        when(preAgg.hasDimension("salesDate")).thenReturn(true);
        when(preAgg.hasMaterializedDimensionProperty("salesDate", "year"))
                .thenReturn(true);
        when(preAgg.getDimensionPropertyColumnNames()).thenReturn(
                Map.of("salesDate$year", "sales_year"));
        when(preAgg.getDimensionNames()).thenReturn(Set.of("salesDate"));

        averageMeasure = mock(DbColumn.class);
        when(averageMeasure.isMeasure()).thenReturn(true);
        when(averageMeasure.getName()).thenReturn("averageAmount");
        when(averageMeasure.getAlias()).thenReturn("averageAmount");
        when(averageMeasure.getAggregation()).thenReturn(DbAggregation.AVG);

        yearDimension = mock(DbColumn.class);
        when(yearDimension.isProperty()).thenReturn(true);
        when(yearDimension.getName()).thenReturn("salesDate$year");
        when(yearDimension.getAlias()).thenReturn("salesDate$year");

        when(queryModel.findJdbcColumnForCond(eq("averageAmount"), anyBoolean(), anyBoolean()))
                .thenReturn(averageMeasure);
        when(queryModel.findJdbcColumnForCond(eq("salesDate$year"), anyBoolean(), anyBoolean()))
                .thenReturn(yearDimension);
        rewriter = new PreAggQueryRewriter(queryModel, null);
    }

    @Test
    void mainRollupUsesWeightedAverageStates() {
        JdbcQuery query = queryWith(averageMeasure);

        PreAggRewriteResult result = rewriter.rewrite(
                PreAggregationMatchResult.matched(preAgg, true, 100),
                query, new DbQueryRequestDef(), mock(JdbcModelQueryEngine.class));

        assertTrue(result.isApplied());
        assertTrue(result.getSql().contains(
                "CAST((SUM(pa.average_amount_avg__sum)) AS DECIMAL(65,30))"));
        assertTrue(result.getSql().contains(
                "NULLIF((SUM(pa.average_amount_avg__count)), 0)"));
    }

    @Test
    void sqliteMainAverageWidensIntegerStatesBeforeDivision() {
        when(queryModel.getDialect()).thenReturn(FDialect.SQLITE_DIALECT);
        rewriter = new PreAggQueryRewriter(queryModel, null);

        PreAggRewriteResult result = rewriter.rewrite(
                PreAggregationMatchResult.matched(preAgg, true, 100),
                queryWith(averageMeasure), new DbQueryRequestDef(),
                mock(JdbcModelQueryEngine.class));

        assertTrue(result.isApplied());
        assertTrue(result.getSql().contains(
                "CAST((SUM(pa.average_amount_avg__sum)) AS REAL)"));
        assertTrue(result.getSql().contains(
                "NULLIF((SUM(pa.average_amount_avg__count)), 0)"));
    }

    @Test
    void algebraicTotalGroupsFullFilteredDomainBeforeMergingStates() {
        JdbcQuery query = queryWith(yearDimension, averageMeasure);
        query.setOrder(query.new JdbcOrder(
                new DbQueryOrderColumnImpl(yearDimension, "DESC")));
        TotalExpressionNode average = TotalExpressionNode.aggregate(
                "AVG", BoundSqlExpression.of("fact.amount"));
        TotalDataAggregatePlan.Builder planBuilder = new TotalDataAggregatePlan.Builder();
        planBuilder.addPublicExpression("salesDate$year", null);
        planBuilder.addPublicExpression("averageAmount", average);
        planBuilder.bindLeaves("averageAmount", average, averageMeasure.getType());
        TotalDataAggregatePlan plan = planBuilder.build();

        PreAggQueryRewriter.PreAggAggregateSqlResult result =
                rewriter.buildAlgebraicAggregateSql(
                        preAgg, query, new DbQueryRequestDef(),
                        PreAggregationMatchResult.matched(preAgg, true, 100), plan);

        assertNotNull(result);
        assertTrue(result.getSql().contains("GROUP BY pa.sales_year"));
        assertTrue(result.getSql().contains("SUM(pa.average_amount_avg__sum)"));
        assertTrue(result.getSql().contains("SUM(pa.average_amount_avg__count)"));
        assertTrue(result.getSql().contains("COUNT(*) AS `total`"));
        assertFalse(result.getSql().contains("ORDER BY"));
        assertFalse(result.getSql().contains("LIMIT"));
        assertFalse(result.getSql().contains("OFFSET"));
    }

    private JdbcQuery queryWith(DbColumn... columns) {
        JdbcQuery query = new JdbcQuery();
        query.setSelect(query.new JdbcSelect());
        query.getSelect().setColumns(List.of(columns));
        return query;
    }
}
