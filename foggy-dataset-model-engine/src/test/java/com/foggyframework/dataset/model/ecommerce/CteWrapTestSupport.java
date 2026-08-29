package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

abstract class CteWrapTestSupport extends EcommerceTestSupport {
    @Resource
    protected SqlFormulaService sqlFormulaService;

    @Resource
    protected SystemBundlesContext systemBundlesContext;

    protected JdbcModelQueryEngine buildRankWindowEngine() {
        return analyze(buildRankWindowRequest());
    }

    protected DbQueryRequestDef buildRankWindowRequest() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef rank = new CalculatedFieldDef();
        rank.setName("salesRank");
        rank.setCaption("销售排名");
        rank.setExpression("RANK()");
        rank.setPartitionBy(Arrays.asList("product$categoryName"));
        rank.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesAmount", "desc")
        ));
        request.setCalculatedFields(new ArrayList<>(List.of(rank)));
        request.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));
        return request;
    }

    protected JdbcModelQueryEngine buildLagWindowEngine() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef lag = new CalculatedFieldDef();
        lag.setName("prevAmount");
        lag.setCaption("上期销售额");
        lag.setExpression("LAG(salesAmount, 1)");
        lag.setPartitionBy(Arrays.asList("product$caption"));
        lag.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        request.setCalculatedFields(new ArrayList<>(List.of(lag)));
        request.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "prevAmount"
        ));
        return analyze(request);
    }

    protected JdbcModelQueryEngine buildMovingAverageEngine() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef ma = new CalculatedFieldDef();
        ma.setName("ma3");
        ma.setCaption("3行移动平均");
        ma.setExpression("AVG(salesAmount)");
        ma.setPartitionBy(Arrays.asList("product$caption"));
        ma.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        ma.setWindowFrame("ROWS BETWEEN 2 PRECEDING AND CURRENT ROW");
        request.setCalculatedFields(new ArrayList<>(List.of(ma)));
        request.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "ma3"
        ));
        return analyze(request);
    }

    protected DbQueryRequestDef buildPostAggregateSalesShareRequest() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of(
                "product$categoryName",
                "sum(salesAmount) as teamSales",
                "salesShare"
        )));
        request.setGroupBy(buildGroupBy("product$categoryName"));
        request.setPostAggregateCalculations(new ArrayList<>(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "teamSales", "grandTotal", "ratio"
        ))));
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesShare", ">", 0.2))));
        return request;
    }

    protected JdbcModelQueryEngine analyze(DbQueryRequestDef request) {
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, request);
        return engine;
    }

    protected AnalysisResult analyzeWithContext(DbQueryRequestDef request) {
        return analyzeWithContext(request, null);
    }

    protected AnalysisResult analyzeWithContext(DbQueryRequestDef request, FDialect dialect) {
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        if (dialect != null) {
            queryModel = spy(queryModel);
            doReturn(dialect).when(queryModel).getDialect();
        }
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        ModelResultContext context = new ModelResultContext(PagingRequest.buildPagingRequest(request, 100), null);
        engine.analysisQueryRequest(systemBundlesContext, context);
        return new AnalysisResult(engine, context);
    }

    protected AnalysisFailure analyzeFailureWithContext(DbQueryRequestDef request, FDialect dialect) {
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        if (dialect != null) {
            queryModel = spy(queryModel);
            doReturn(dialect).when(queryModel).getDialect();
        }
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        ModelResultContext context = new ModelResultContext(PagingRequest.buildPagingRequest(request, 100), null);
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> engine.analysisQueryRequest(systemBundlesContext, context));
        return new AnalysisFailure(exception, context);
    }

    @SuppressWarnings("unchecked")

    protected Map<String, Object> queryStagePlan(ModelResultContext context) {
        Object plan = context.getExtData().get(QueryStagePlan.EXT_DATA_KEY);
        assertNotNull(plan, "queryStagePlan diagnostics should be attached to context.extData");
        assertTrue(plan instanceof Map<?, ?>, "queryStagePlan diagnostics should be a map");
        return (Map<String, Object>) plan;
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> stages(Map<String, Object> plan) {
        Object stages = plan.get("stages");
        assertTrue(stages instanceof List<?>, "queryStagePlan.stages should be a list");
        return (List<Map<String, Object>>) stages;
    }

    protected List<String> stageIds(Map<String, Object> plan) {
        return stages(plan).stream()
                .map(stage -> (String) stage.get("id"))
                .toList();
    }

    protected Map<String, Object> stage(Map<String, Object> plan, String id) {
        return stages(plan).stream()
                .filter(stage -> id.equals(stage.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing stage '" + id + "' in " + plan));
    }

    @SuppressWarnings("unchecked")
    protected List<String> listValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        assertTrue(value instanceof List<?>, key + " should be a list");
        return (List<String>) value;
    }

    protected String expectedMultiStageRenderStrategy() {
        return supportsCommonTableExpressions() ? "cte" : "derived";
    }

    protected void assertPostAggregateRenderingMatchesPlan(JdbcModelQueryEngine engine, Map<String, Object> plan) {
        String strategy = (String) plan.get("renderStrategy");
        String sql = engine.getSql();
        if ("cte".equals(strategy)) {
            assertTrue(engine.isCteWrapped(), "CTE rendering should expose structured CTE stages");
            assertTrue(sql.contains("post_stage AS"),
                    "CTE post-aggregate SQL wrapping should remain active: " + sql);
            return;
        }
        assertEquals("derived", strategy, "Post-aggregate strategy should be CTE or derived");
        assertFalse(engine.isCteWrapped(), "Derived fallback should not expose structured CTE stages");
        assertFalse(sql.toUpperCase().contains("WITH "), "Derived fallback must not emit WITH: " + sql);
        assertTrue(sql.contains("post_stage"), "Derived fallback should still expose the post stage alias: " + sql);
        assertTrue(sql.contains("FROM (\nSELECT"), "Derived fallback should nest the planned stage SQL: " + sql);
    }

    protected void assertFinalTotalMatchesRows(JdbcModelQueryEngine engine) {
        Object[] params = engine.getValues().toArray(new Object[0]);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(engine.getSql(), params);
        assertFalse(rows.isEmpty(), "Main query should execute against the fixture");

        Map<String, Object> totalData = jdbcTemplate.queryForMap(engine.getAggSql(), params);
        Object total = totalData.get("total");
        if (total == null) {
            total = totalData.get("TOTAL");
        }
        assertNotNull(total, "Agg SQL should expose a total column: " + engine.getAggSql());
        assertEquals(rows.size(), ((Number) total).intValue(),
                "returnTotal should count the final semantic result set");
    }

    protected static class NoCteSqliteDialect extends SqliteDialect {
        @Override
        public boolean supportsCte() {
            return false;
        }
    }

    protected static class NoWindowSqliteDialect extends SqliteDialect {
        @Override
        public boolean supportsWindowFunctions() {
            return false;
        }
    }

    protected static class NoCteWindowSqliteDialect extends SqliteDialect {
        @Override
        public boolean supportsCte() {
            return false;
        }

        @Override
        public boolean supportsWindowFunctions() {
            return true;
        }
    }


    protected List<GroupRequestDef> buildGroupBy(String... fields) {
        List<GroupRequestDef> groups = new ArrayList<>();
        for (String field : fields) {
            GroupRequestDef g = new GroupRequestDef();
            g.setField(field);
            groups.add(g);
        }
        return groups;
    }

    protected OrderRequestDef orderAsc(String field) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir("ASC");
        return order;
    }

    protected OrderRequestDef orderDesc(String field) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir("DESC");
        return order;
    }

    protected String extractOuterSelect(String sql) {
        int fromStage1 = sql.toUpperCase().indexOf("FROM STAGE1");
        if (fromStage1 < 0) return sql;
        // Find the SELECT before FROM stage1
        int outerSelect = sql.toUpperCase().lastIndexOf("SELECT ", fromStage1);
        return outerSelect >= 0 ? sql.substring(outerSelect, fromStage1) : sql;
    }

    protected String handWrittenRunningSumSql(int threshold) {
        String orderId = quoteIdentifier("orderId");
        String salesAmount = quoteIdentifier("salesAmount");
        String runningSalesAmount = quoteIdentifier("runningSalesAmount");
        return """
                WITH order_sales AS (
                    SELECT fs.order_id AS %s,
                           SUM(fs.sales_amount) AS %s
                    FROM fact_sales fs
                    WHERE fs.order_status = 'COMPLETED'
                    GROUP BY fs.order_id
                ),
                order_sales_window AS (
                    SELECT %s,
                           %s,
                           SUM(%s) OVER (
                               ORDER BY %s DESC, %s ASC
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                           ) AS %s
                    FROM order_sales
                )
                SELECT %s, %s, %s
                FROM order_sales_window
                WHERE %s <= %d
                ORDER BY %s DESC, %s ASC
                """.formatted(
                orderId, salesAmount,
                orderId, salesAmount, salesAmount, salesAmount, orderId, runningSalesAmount,
                orderId, salesAmount, runningSalesAmount,
                runningSalesAmount, threshold,
                salesAmount, orderId);
    }

    protected int runningSumPostSliceThreshold() {
        // Keep parity stable when Maven reuses the shared SQLite fixture across repeated executions.
        Number maxOrderSales = jdbcTemplate.queryForObject("""
                SELECT MAX(order_sales.salesAmount)
                FROM (
                    SELECT SUM(fs.sales_amount) AS salesAmount
                    FROM fact_sales fs
                    WHERE fs.order_status = 'COMPLETED'
                    GROUP BY fs.order_id
                ) order_sales
                """, Number.class);
        assertNotNull(maxOrderSales, "COMPLETED order sales baseline should exist");
        return BigDecimal.valueOf(maxOrderSales.doubleValue())
                .setScale(0, RoundingMode.CEILING)
                .intValue();
    }

    protected String quoteIdentifier(String identifier) {
        String dialect = getDialectKey();
        if (dialect.contains("mysql")) {
            return "`" + identifier + "`";
        }
        if (dialect.contains("sqlserver")) {
            return "[" + identifier + "]";
        }
        return "\"" + identifier + "\"";
    }

    protected static void assertRowsEqualInOrder(
            List<Map<String, Object>> expected,
            List<Map<String, Object>> actual,
            String sql) {
        assertFalse(actual.isEmpty(), "actual result should not be empty");
        List<Map<String, String>> expectedRows = canonicalRowsInOrder(expected);
        List<Map<String, String>> actualRows = canonicalRowsInOrder(actual);
        assertTrue(expectedRows.equals(actualRows), () -> {
            int firstDiff = firstDiff(expectedRows, actualRows);
            String diff = firstDiff < 0
                    ? "no row diff"
                    : "firstDiff=" + firstDiff
                    + ", expected=" + expectedRows.get(firstDiff)
                    + ", actual=" + actualRows.get(firstDiff);
            return "expectedSize=" + expectedRows.size()
                    + ", actualSize=" + actualRows.size()
                    + ", " + diff
                    + "\nSQL:\n" + sql;
        });
    }

    protected static int firstDiff(List<Map<String, String>> expectedRows, List<Map<String, String>> actualRows) {
        int size = Math.min(expectedRows.size(), actualRows.size());
        for (int i = 0; i < size; i++) {
            if (!expectedRows.get(i).equals(actualRows.get(i))) {
                return i;
            }
        }
        return expectedRows.size() == actualRows.size() ? -1 : size;
    }

    protected static List<Map<String, String>> canonicalRowsInOrder(List<Map<String, Object>> rows) {
        List<Map<String, String>> canonical = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> normalized = new LinkedHashMap<>();
            row.entrySet().stream()
                    .filter(entry -> !entry.getKey().startsWith("_"))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalized.put(entry.getKey(), canonicalValue(entry.getValue())));
            canonical.add(normalized);
        }
        return canonical;
    }

    protected static String canonicalValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
                    .setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
        return value.toString();
    }


    protected JdbcModelQueryEngine analyze(JdbcModelQueryEngine engine) {
        return engine;
    }

    protected record AnalysisResult(JdbcModelQueryEngine engine, ModelResultContext context) {
    }

    protected record AnalysisFailure(RuntimeException exception, ModelResultContext context) {
    }
}
