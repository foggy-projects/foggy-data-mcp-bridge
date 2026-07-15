package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbDimensionColumn;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.DbPropertyColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreAggQueryRequirementBuilderTest {

    private final PreAggQueryRequirementBuilder builder = new PreAggQueryRequirementBuilder();

    @Test
    void groupPropertyWrapperIsResolvedButAggregateOverPropertyFailsClosed() {
        QueryObject queryObject = mock(QueryObject.class);
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        DbColumn property = propertyColumn("product$categoryName");
        when(queryModel.findJdbcColumnForCond("product$categoryName", false, true)).thenReturn(property);

        PreAggQueryRequirement grouped = builder.build(groupedRequest(),
                queryWith(queryObject, new AggregationDbColumn(
                        queryObject, "product$categoryName", "d2.category_name",
                        DbColumnType.STRING, DbAggregation.NONE)),
                queryModel);

        assertFalse(grouped.isHasCustomSqlConditions());
        assertTrue(grouped.getDimensionNames().contains("product"));
        assertTrue(grouped.getDimensionProperties().get("product").contains("categoryName"));

        PreAggQueryRequirement aggregateOverProperty = builder.build(groupedRequest(),
                queryWith(queryObject, new AggregationDbColumn(
                        queryObject, "product$categoryName", "MAX(d2.category_name)",
                        DbColumnType.STRING, DbAggregation.MAX)),
                queryModel);

        assertTrue(aggregateOverProperty.isHasCustomSqlConditions(),
                "an aggregate wrapper over a non-measure must refuse pre-aggregation");
    }

    @Test
    void requestSliceCannotMaskQueryScriptPredicateProvenance() {
        QueryObject queryObject = mock(QueryObject.class);
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        DbColumn measure = mock(DbColumn.class);
        when(measure.getName()).thenReturn("salesAmount");
        when(measure.isMeasure()).thenReturn(true);
        when(measure.getAggregation()).thenReturn(DbAggregation.SUM);
        when(queryModel.findJdbcColumnForCond("salesAmount", false, true)).thenReturn(measure);

        JdbcQuery jdbcQuery = queryWith(queryObject, new AggregationDbColumn(
                queryObject, "salesAmount", "SUM(t1.sales_amount)",
                DbColumnType.MONEY, DbAggregation.SUM));
        jdbcQuery.getWhere().and("t1.order_status = ?", "COMPLETED");
        assertTrue(jdbcQuery.isNonSliceWhereConditionAdded(),
                "direct JdbcWhere mutations must record non-slice provenance");
        assertTrue(jdbcQuery.isRawSqlConditionAdded(),
                "direct SQL fragments must also disable raw-SQL-sensitive optimizations");

        DbQueryRequestDef request = groupedRequest();
        request.setSlice(List.of(new SliceRequestDef("salesDate$id", ">=", 20930101)));

        PreAggQueryRequirement requirement = builder.build(request, jdbcQuery, queryModel);

        assertTrue(requirement.isHasWhereConditions());
        assertTrue(requirement.isHasCustomSqlConditions(),
                "a query-script/access predicate must not be mistaken for a compiled request slice");
    }

    @Test
    void requestOrCompiledHavingAlwaysFailsClosed() {
        QueryObject queryObject = mock(QueryObject.class);
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        DbColumn measure = mock(DbColumn.class);
        when(measure.getName()).thenReturn("salesAmount");
        when(measure.isMeasure()).thenReturn(true);
        when(measure.getAggregation()).thenReturn(DbAggregation.SUM);
        when(queryModel.findJdbcColumnForCond("salesAmount", false, true)).thenReturn(measure);

        JdbcQuery requestHavingQuery = queryWith(queryObject, new AggregationDbColumn(
                queryObject, "salesAmount", "SUM(t1.sales_amount)",
                DbColumnType.MONEY, DbAggregation.SUM));
        DbQueryRequestDef requestHaving = groupedRequest();
        requestHaving.setHaving(List.of(new SliceRequestDef("salesAmount", ">", 100)));

        assertTrue(builder.build(requestHaving, requestHavingQuery, queryModel)
                        .isHasCustomSqlConditions(),
                "main pre-aggregation must not drop request.having");
        assertTrue(builder.buildFinalStage(requestHaving, requestHavingQuery, queryModel)
                        .isHasCustomSqlConditions(),
                "final-stage pre-aggregation must not drop request.having");

        JdbcQuery compiledHavingQuery = queryWith(queryObject, new AggregationDbColumn(
                queryObject, "salesAmount", "SUM(t1.sales_amount)",
                DbColumnType.MONEY, DbAggregation.SUM));
        compiledHavingQuery.getHaving().and("SUM(t1.sales_amount) > ?", 100);

        assertTrue(builder.build(groupedRequest(), compiledHavingQuery, queryModel)
                        .isHasCustomSqlConditions(),
                "aggregate slices lifted into JdbcQuery.having must fail closed");
        assertTrue(builder.buildFinalStage(groupedRequest(), compiledHavingQuery, queryModel)
                        .isHasCustomSqlConditions(),
                "final-stage rewrite must inspect compiled JdbcQuery.having");
    }

    @Test
    void sliceTimeGrainUsesSemanticDimensionAndKeepsFinestRequirement() {
        QueryObject queryObject = mock(QueryObject.class);
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        DbColumn measure = mock(DbColumn.class);
        when(measure.getName()).thenReturn("salesAmount");
        when(measure.isMeasure()).thenReturn(true);
        when(measure.getAggregation()).thenReturn(DbAggregation.SUM);
        when(queryModel.findJdbcColumnForCond("salesAmount", false, true)).thenReturn(measure);

        DbDimension salesDate = mock(DbDimension.class);
        when(salesDate.getTimeRole()).thenReturn("business_date");
        DbDimensionColumn salesDateColumn = mock(DbDimensionColumn.class);
        when(salesDateColumn.getDimension()).thenReturn(salesDate);
        stubDimensionField(queryModel, "salesDate$caption", salesDateColumn);
        stubDimensionField(queryModel, "salesDate$month", salesDateColumn);

        DbDimension product = mock(DbDimension.class);
        DbDimensionColumn productColumn = mock(DbDimensionColumn.class);
        when(productColumn.getDimension()).thenReturn(product);
        stubDimensionField(queryModel, "product$month", productColumn);

        JdbcQuery jdbcQuery = queryWith(queryObject, new AggregationDbColumn(
                queryObject, "salesAmount", "SUM(t1.sales_amount)",
                DbColumnType.MONEY, DbAggregation.SUM));
        DbQueryRequestDef monthThenDay = groupedRequest();
        monthThenDay.setSlice(List.of(
                new SliceRequestDef("salesDate$month", "=", 1),
                new SliceRequestDef("salesDate$caption", ">=", "2024-01-01"),
                new SliceRequestDef("product$month", "=", "launch")));

        PreAggQueryRequirement first = builder.build(monthThenDay, jdbcQuery, queryModel);
        assertEquals(TimeGranularity.DAY, first.getQueryGranularities().get("salesDate"),
                "DAY must win when MONTH is encountered first");
        assertFalse(first.getQueryGranularities().containsKey("product"),
                "a regular dimension's month property must not become a time grain");

        DbQueryRequestDef dayThenMonth = groupedRequest();
        dayThenMonth.setSlice(List.of(
                new SliceRequestDef("salesDate$caption", ">=", "2024-01-01"),
                new SliceRequestDef("salesDate$month", "=", 1)));
        PreAggQueryRequirement second = builder.build(dayThenMonth, jdbcQuery, queryModel);
        assertEquals(TimeGranularity.DAY, second.getQueryGranularities().get("salesDate"),
                "DAY must remain when MONTH is encountered later");

        SliceRequestDef fieldReference = new SliceRequestDef();
        fieldReference.setField("product$month");
        fieldReference.setOp("=");
        fieldReference.setValue(Map.of(CondRequestDef.FIELD_REFERENCE_KEY, "salesDate$caption"));
        DbQueryRequestDef rhsFieldRequest = groupedRequest();
        rhsFieldRequest.setSlice(List.of(fieldReference));
        PreAggQueryRequirement rhsMain = builder.build(rhsFieldRequest, jdbcQuery, queryModel);
        assertTrue(rhsMain.isHasCustomSqlConditions(),
                "main rewrite must fail closed for $field until it uses the strict predicate prover");
        assertEquals(TimeGranularity.DAY,
                rhsMain
                        .getQueryGranularities().get("salesDate"),
                "$field RHS must contribute its temporal grain");
        assertFalse(builder.buildFinalStage(rhsFieldRequest, jdbcQuery, queryModel)
                        .isHasCustomSqlConditions(),
                "strict final-stage predicate proof remains eligible");

        SliceRequestDef expression = new SliceRequestDef();
        expression.setExpr("product$month = salesDate$caption");
        DbQueryRequestDef expressionRequest = groupedRequest();
        expressionRequest.setSlice(List.of(expression));
        PreAggQueryRequirement expressionMain =
                builder.build(expressionRequest, jdbcQuery, queryModel);
        assertTrue(expressionMain.isHasCustomSqlConditions(),
                "main rewrite must fail closed for $expr until it uses the strict predicate prover");
        assertEquals(TimeGranularity.DAY,
                expressionMain
                        .getQueryGranularities().get("salesDate"),
                "$expr semantic tokens must contribute temporal grain");
        assertFalse(builder.buildFinalStage(expressionRequest, jdbcQuery, queryModel)
                        .isHasCustomSqlConditions(),
                "strict final-stage $expr proof remains eligible");
    }

    private JdbcQuery queryWith(QueryObject queryObject, DbColumn column) {
        when(queryObject.getAlias()).thenReturn("t1");
        JdbcQuery jdbcQuery = new JdbcQuery();
        jdbcQuery.from(queryObject);
        jdbcQuery.select(column);
        return jdbcQuery;
    }

    private DbQueryRequestDef groupedRequest() {
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setGroupBy(List.of(group));
        return request;
    }

    private DbColumn propertyColumn(String name) {
        DbColumn property = mock(DbColumn.class);
        DbPropertyColumn propertyColumn = mock(DbPropertyColumn.class);
        when(propertyColumn.getProperty()).thenReturn(mock(DbProperty.class));
        when(property.getName()).thenReturn(name);
        when(property.isProperty()).thenReturn(true);
        when(property.getDecorate(DbPropertyColumn.class)).thenReturn(propertyColumn);
        return property;
    }

    private void stubDimensionField(JdbcQueryModel queryModel,
                                    String field,
                                    DbDimensionColumn dimensionColumn) {
        DbColumn semanticColumn = mock(DbColumn.class);
        when(semanticColumn.getDecorate(DbDimensionColumn.class)).thenReturn(dimensionColumn);
        when(queryModel.findJdbcColumnForCond(field, false, true)).thenReturn(semanticColumn);
    }
}
