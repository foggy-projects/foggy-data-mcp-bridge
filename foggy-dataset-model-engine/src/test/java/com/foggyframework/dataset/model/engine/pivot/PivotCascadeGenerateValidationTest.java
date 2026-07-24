package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.db.dialect.MysqlDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import com.foggyframework.dataset.model.engine.pivot.cascade.PivotCascadeErrorCode;
import com.foggyframework.dataset.model.engine.pivot.cascade.PivotCascadeException;
import com.foggyframework.dataset.model.engine.pivot.cascade.PivotCascadeRules;
import com.foggyframework.dataset.model.engine.pivot.sql.PivotAxisDomainSqlPlanner;
import com.foggyframework.dataset.model.plugins.query_execution.AdditiveKind;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedRelationOptions;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedMetricMetadata;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.TableModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PIVOT-91-C2 cascade Generate validation")
class PivotCascadeGenerateValidationTest {

    @Test
    @DisplayName("missing orderBy on limited cascade level is rejected")
    void testMissingOrderByRejected() {
        PivotRequest pivot = rowsCascade();
        pivot.getRows().get(0).setOrderBy(null);

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> PivotCascadeRules.validateRequestShape(pivot));

        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_ORDER_BY_REQUIRED, ex.getCode());
        assertTrue(ex.getMessage().contains("product$categoryName"));
    }

    @Test
    @DisplayName("tree mode plus cascade is rejected")
    void testTreeModeCascadeRejected() {
        PivotRequest pivot = rowsCascade();
        pivot.getRows().get(0).setHierarchyMode("tree");

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> PivotCascadeRules.validateRequestShape(pivot));

        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_TREE_REJECTED, ex.getCode());
    }

    @Test
    @DisplayName("three-level cascade is outside C2 v1 scope")
    void testThreeLevelCascadeRejected() {
        AxisField region = axis("region");
        AxisField city = limitedAxis("city", "-salesAmount", 2);
        AxisField store = axis("store");

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(region, city, store));
        pivot.setColumns(Collections.emptyList());
        pivot.setMetrics(List.of("salesAmount"));

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> PivotCascadeRules.validateRequestShape(pivot));

        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_SCOPE_UNSUPPORTED, ex.getCode());
    }

    @Test
    @DisplayName("rows cascade plus column TopN is rejected")
    void testRowsCascadePlusColumnLimitRejected() {
        PivotRequest pivot = rowsCascade();
        pivot.setColumns(List.of(limitedAxis("salesDate$month", "-salesAmount", 3)));

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> PivotCascadeRules.validateRequestShape(pivot));

        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_CROSS_AXIS_REJECTED, ex.getCode());
    }

    @Test
    @DisplayName("multi-level having-only cascade is rejected until oracle coverage exists")
    void testHavingOnlyCascadeRejected() {
        PivotRequest pivot = new PivotRequest();
        AxisField category = axis("product$categoryName");
        category.setHaving(List.of(filter("salesAmount", ">", 1000)));
        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setHaving(List.of(filter("salesAmount", ">", 100)));
        pivot.setRows(List.of(category, subCategory));
        pivot.setColumns(Collections.emptyList());
        pivot.setMetrics(List.of("salesAmount"));

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> PivotCascadeRules.validateRequestShape(pivot));

        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_SCOPE_UNSUPPORTED, ex.getCode());
    }

    @Test
    @DisplayName("unsupported cascade shapes fail closed at pipeline pre-validation")
    void testUnsupportedCascadeShapesRejectedAtPipelineBoundary() {
        PivotRequest missingOrderBy = rowsCascade();
        missingOrderBy.getRows().get(0).setOrderBy(null);
        assertPipelineRejectsBeforeExecution(missingOrderBy,
                PivotCascadeErrorCode.PIVOT_CASCADE_ORDER_BY_REQUIRED);

        PivotRequest columnAxisCascade = new PivotRequest();
        columnAxisCascade.setRows(List.of(axis("product$categoryName")));
        columnAxisCascade.setColumns(List.of(
                limitedAxis("salesDate$year", "-salesAmount", 2),
                limitedAxis("salesDate$month", "-salesAmount", 2)));
        columnAxisCascade.setMetrics(List.of("salesAmount"));
        assertPipelineRejectsBeforeExecution(columnAxisCascade,
                PivotCascadeErrorCode.PIVOT_CASCADE_CROSS_AXIS_REJECTED);

        PivotRequest crossAxisCascade = rowsCascade();
        crossAxisCascade.setColumns(List.of(limitedAxis("salesDate$month", "-salesAmount", 3)));
        assertPipelineRejectsBeforeExecution(crossAxisCascade,
                PivotCascadeErrorCode.PIVOT_CASCADE_CROSS_AXIS_REJECTED);

        PivotRequest threeLevelCascade = new PivotRequest();
        threeLevelCascade.setRows(List.of(
                limitedAxis("region", "-salesAmount", 2),
                limitedAxis("city", "-salesAmount", 2),
                axis("store")));
        threeLevelCascade.setColumns(Collections.emptyList());
        threeLevelCascade.setMetrics(List.of("salesAmount"));
        assertPipelineRejectsBeforeExecution(threeLevelCascade,
                PivotCascadeErrorCode.PIVOT_CASCADE_SCOPE_UNSUPPORTED);

        PivotRequest havingOnlyCascade = new PivotRequest();
        AxisField category = axis("product$categoryName");
        category.setHaving(List.of(filter("salesAmount", ">", 1000)));
        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setHaving(List.of(filter("salesAmount", ">", 100)));
        havingOnlyCascade.setRows(List.of(category, subCategory));
        havingOnlyCascade.setColumns(Collections.emptyList());
        havingOnlyCascade.setMetrics(List.of("salesAmount"));
        assertPipelineRejectsBeforeExecution(havingOnlyCascade,
                PivotCascadeErrorCode.PIVOT_CASCADE_SCOPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("non-additive metric in cascade request is rejected")
    void testNonAdditiveCascadeRejected() {
        PivotRequest pivot = rowsCascade("avgPrice");
        QueryModel queryModel = queryModelWithAggregation("avgPrice", DbAggregation.AVG);

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> PivotCascadeRules.validateAdditivity(pivot, queryModel, Collections.emptyList()));

        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_NON_ADDITIVE_REJECTED, ex.getCode());
        assertTrue(ex.getMessage().contains("avgPrice"));
    }

    @Test
    @DisplayName("two-level leaf-only partitioned TopN remains outside C2 cascade gate")
    void testLeafOnlyPartitionedTopNNotC2Cascade() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName"),
                limitedAxis("product$subCategoryName", "-salesAmount", 2)));
        pivot.setColumns(Collections.emptyList());
        pivot.setMetrics(List.of("salesAmount"));

        assertFalse(PivotCascadeRules.isCascadeRequest(pivot));
        assertDoesNotThrow(() -> PivotCascadeRules.validateRequestShape(pivot));
    }

    @Test
    @DisplayName("supported candidate successfully generates staged SQL")
    void testSupportedCandidateSucceedsWithStagedSqlPlanner() {
        ManagedSqlRelation relation = new ManagedSqlRelation(
                "SELECT product$categoryName, product$subCategoryName, SUM(salesAmount) AS salesAmount FROM sales GROUP BY product$categoryName, product$subCategoryName",
                Collections.emptyList(),
                new SqliteDialect(),
                null,
                null,
                true,
                true,
                false,
                List.of(ManagedMetricMetadata.builder()
                        .metricName("salesAmount")
                        .additiveKind(AdditiveKind.ADDITIVE)
                        .aggregationFunction("SUM")
                        .build()));

        assertDoesNotThrow(() -> PivotAxisDomainSqlPlanner.plan(
                relation,
                rowsCascade(),
                List.of("product$categoryName", "product$subCategoryName"),
                Collections.emptyList(),
                List.of("salesAmount")));
    }

    @Test
    @DisplayName("conservative MySQL dialect cascade fails closed at pipeline boundary")
    void testMysql57CascadeFailsClosedWithoutMemoryFallback() {
        AdvancedQueryFacade queryFacade = (AdvancedQueryFacade) Proxy.newProxyInstance(
                AdvancedQueryFacade.class.getClassLoader(),
                new Class[]{AdvancedQueryFacade.class},
                (proxy, method, args) -> {
                    if ("prepareManagedRelation".equals(method.getName())) {
                        assertTrue(args[0] instanceof ModelResultContext);
                        assertTrue(args[1] instanceof ManagedRelationOptions);
                        return new ManagedSqlRelation(
                                "SELECT product$categoryName, product$subCategoryName, SUM(salesAmount) AS salesAmount FROM sales GROUP BY product$categoryName, product$subCategoryName",
                                Collections.emptyList(),
                                new MysqlDialect(),
                                null,
                                null,
                                true,
                                true,
                                false,
                                List.of(ManagedMetricMetadata.builder()
                                        .metricName("salesAmount")
                                        .additiveKind(AdditiveKind.ADDITIVE)
                                        .aggregationFunction("SUM")
                                        .build()));
                    }
                    if ("executeManagedRelation".equals(method.getName())) {
                        return fail("unsupported cascade dialect must fail before final SQL execution");
                    }
                    return defaultValue(method.getReturnType());
                });

        QueryModelLoader queryModelLoader = (QueryModelLoader) Proxy.newProxyInstance(
                QueryModelLoader.class.getClassLoader(),
                new Class[]{QueryModelLoader.class},
                (proxy, method, args) -> {
                    if ("getJdbcQueryModel".equals(method.getName())) {
                        return queryModelWithAggregation("salesAmount", DbAggregation.SUM);
                    }
                    return defaultValue(method.getReturnType());
                });

        PivotPipeline pipeline = new PivotPipeline(null, new CardinalityBreaker(), queryModelLoader, queryFacade);
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(rowsCascade());

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> pipeline.execute("FactSalesQueryModel", request, SemanticRequestContext.empty()));

        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_SQL_REQUIRED, ex.getCode());
        assertTrue(ex.getMessage().contains("Planner failure"));
    }

    private static PivotRequest rowsCascade() {
        return rowsCascade("salesAmount");
    }

    private static PivotRequest rowsCascade(String metric) {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(
                limitedAxis("product$categoryName", "-" + metric, 2),
                limitedAxis("product$subCategoryName", "-" + metric, 2)));
        pivot.setColumns(Collections.emptyList());
        pivot.setMetrics(List.of(metric));
        return pivot;
    }

    private static AxisField axis(String field) {
        AxisField axis = new AxisField();
        axis.setField(field);
        return axis;
    }

    private static AxisField limitedAxis(String field, String orderBy, int limit) {
        AxisField axis = axis(field);
        axis.setOrderBy(List.of(orderBy));
        axis.setLimit(limit);
        return axis;
    }

    private static MetricFilter filter(String metric, String op, Number value) {
        MetricFilter filter = new MetricFilter();
        filter.setMetric(metric);
        filter.setOp(op);
        filter.setValue(value);
        return filter;
    }

    private static void assertPipelineRejectsBeforeExecution(PivotRequest pivot, PivotCascadeErrorCode expectedCode) {
        AdvancedQueryFacade queryFacade = (AdvancedQueryFacade) Proxy.newProxyInstance(
                AdvancedQueryFacade.class.getClassLoader(),
                new Class[]{AdvancedQueryFacade.class},
                (proxy, method, args) -> fail("unsupported cascade shape must fail before AdvancedQueryFacade." + method.getName()));

        QueryModelLoader queryModelLoader = (QueryModelLoader) Proxy.newProxyInstance(
                QueryModelLoader.class.getClassLoader(),
                new Class[]{QueryModelLoader.class},
                (proxy, method, args) -> fail("unsupported cascade shape must fail before QueryModelLoader." + method.getName()));

        PivotPipeline pipeline = new PivotPipeline(null, new CardinalityBreaker(), queryModelLoader, queryFacade);
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        PivotCascadeException ex = assertThrows(
                PivotCascadeException.class,
                () -> pipeline.execute("FactSalesQueryModel", request, SemanticRequestContext.empty()));
        assertEquals(expectedCode, ex.getCode());
    }

    private static QueryModel queryModelWithAggregation(String metric, DbAggregation aggregation) {
        DbMeasure measure = (DbMeasure) Proxy.newProxyInstance(
                DbMeasure.class.getClassLoader(),
                new Class[]{DbMeasure.class},
                (proxy, method, args) -> {
                    if ("getAggregation".equals(method.getName())) return aggregation;
                    if ("getName".equals(method.getName())) return metric;
                    return defaultValue(method.getReturnType());
                });

        TableModel tableModel = (TableModel) Proxy.newProxyInstance(
                TableModel.class.getClassLoader(),
                new Class[]{TableModel.class},
                (proxy, method, args) -> {
                    if ("findJdbcMeasureByName".equals(method.getName()) &&
                            args != null && metric.equals(args[0])) {
                        return measure;
                    }
                    return defaultValue(method.getReturnType());
                });

        return (QueryModel) Proxy.newProxyInstance(
                QueryModel.class.getClassLoader(),
                new Class[]{QueryModel.class},
                (proxy, method, args) -> {
                    if ("getJdbcModel".equals(method.getName())) return tableModel;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
