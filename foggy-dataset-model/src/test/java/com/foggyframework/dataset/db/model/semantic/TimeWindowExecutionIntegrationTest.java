package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@DisplayName("TimeWindow execution real-SQL parity")
class TimeWindowExecutionIntegrationTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("rolling_7d daily execution matches hand-written SQL")
    void rolling7dDailyExecutionMatchesSql() {
        if (skipWhenWindowFunctionsUnsupported("rolling_7d daily execution")) {
            return;
        }

        SemanticQueryRequest request = request(
                List.of("salesDate$id", "salesAmount", "salesAmount__rolling_7d"),
                List.of("salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "rolling_7d",
                        "targetMetrics", List.of("salesAmount")
                ));

        SemanticQueryResponse response = execute(request);
        String salesDateId = quoteIdentifier("salesDate$id");
        String salesAmount = quoteIdentifier("salesAmount");
        String rolling7d = quoteIdentifier("salesAmount__rolling_7d");
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT fs.date_key AS %s,
                           SUM(fs.sales_amount) AS %s
                    FROM fact_sales fs
                    GROUP BY fs.date_key
                )
                SELECT %s,
                       %s,
                       SUM(%s) OVER (
                           ORDER BY %s
                           ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
                       ) AS %s
                FROM daily
                """.formatted(
                        salesDateId, salesAmount, salesDateId, salesAmount,
                        salesAmount, salesDateId, rolling7d));

        assertRowsEqual(expected, response.getItems());
    }

    @Test
    @DisplayName("rolling_7d SQL preview matches hand-written SQL")
    void rolling7dGenerateSqlMatchesSql() {
        if (skipWhenWindowFunctionsUnsupported("rolling_7d SQL preview")) {
            return;
        }

        SemanticQueryRequest request = request(
                List.of("salesDate$id", "salesAmount", "salesAmount__rolling_7d"),
                List.of("salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "rolling_7d",
                        "targetMetrics", List.of("salesAmount")
                ));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                TEST_MODEL, request, SemanticRequestContext.empty());
        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("OVER"), generated.getSql());
        assertTrue(generated.getSql().contains("salesAmount__rolling_7d"), generated.getSql());

        List<Map<String, Object>> actual = jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0]));
        String salesDateId = quoteIdentifier("salesDate$id");
        String salesAmount = quoteIdentifier("salesAmount");
        String rolling7d = quoteIdentifier("salesAmount__rolling_7d");
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT fs.date_key AS %s,
                           SUM(fs.sales_amount) AS %s
                    FROM fact_sales fs
                    GROUP BY fs.date_key
                )
                SELECT %s,
                       %s,
                       SUM(%s) OVER (
                           ORDER BY %s
                           ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
                       ) AS %s
                FROM daily
                """.formatted(
                        salesDateId, salesAmount, salesDateId, salesAmount,
                        salesAmount, salesDateId, rolling7d));

        assertRowsEqual(expected, actual, generated.getSql());
    }

    @Test
    @DisplayName("MTD daily execution matches hand-written SQL")
    void mtdDailyExecutionMatchesSql() {
        if (skipWhenWindowFunctionsUnsupported("MTD daily execution")) {
            return;
        }

        SemanticQueryRequest request = request(
                List.of("salesDate$year", "salesDate$month", "salesDate$id", "salesAmount", "salesAmount__mtd"),
                List.of("salesDate$year", "salesDate$month", "salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "mtd",
                        "targetMetrics", List.of("salesAmount")
                ));

        SemanticQueryResponse response = execute(request);
        String salesYear = quoteIdentifier("salesDate$year");
        String salesMonth = quoteIdentifier("salesDate$month");
        String salesDateId = quoteIdentifier("salesDate$id");
        String salesAmount = quoteIdentifier("salesAmount");
        String mtd = quoteIdentifier("salesAmount__mtd");
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT d.year AS %s,
                           d.month AS %s,
                           fs.date_key AS %s,
                           SUM(fs.sales_amount) AS %s
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    GROUP BY d.year, d.month, fs.date_key
                )
                SELECT %s,
                       %s,
                       %s,
                       %s,
                       SUM(%s) OVER (
                           PARTITION BY %s, %s
                           ORDER BY %s
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       ) AS %s
                FROM daily
                """.formatted(
                        salesYear, salesMonth, salesDateId, salesAmount,
                        salesYear, salesMonth, salesDateId, salesAmount,
                        salesAmount, salesYear, salesMonth, salesDateId, mtd));

        assertRowsEqual(expected, response.getItems());
    }

    @Test
    @DisplayName("YTD daily execution matches hand-written SQL")
    void ytdDailyExecutionMatchesSql() {
        if (skipWhenWindowFunctionsUnsupported("YTD daily execution")) {
            return;
        }

        SemanticQueryRequest request = request(
                List.of("salesDate$year", "salesDate$id", "salesAmount", "salesAmount__ytd"),
                List.of("salesDate$year", "salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "ytd",
                        "targetMetrics", List.of("salesAmount")
                ));

        SemanticQueryResponse response = execute(request);
        String salesYear = quoteIdentifier("salesDate$year");
        String salesDateId = quoteIdentifier("salesDate$id");
        String salesAmount = quoteIdentifier("salesAmount");
        String ytd = quoteIdentifier("salesAmount__ytd");
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT d.year AS %s,
                           fs.date_key AS %s,
                           SUM(fs.sales_amount) AS %s
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    GROUP BY d.year, fs.date_key
                )
                SELECT %s,
                       %s,
                       %s,
                       SUM(%s) OVER (
                           PARTITION BY %s
                           ORDER BY %s
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       ) AS %s
                FROM daily
                """.formatted(
                        salesYear, salesDateId, salesAmount,
                        salesYear, salesDateId, salesAmount,
                        salesAmount, salesYear, salesDateId, ytd));

        assertRowsEqual(expected, response.getItems());
    }

    @Test
    @DisplayName("timeWindow post-calc CALCULATE is rejected by full semantic pipeline")
    void timeWindowPostCalcCalculateRejectedBySemanticPipeline() {
        if (skipWhenWindowFunctionsUnsupported("timeWindow post-calc CALCULATE rejection")) {
            return;
        }

        SemanticQueryRequest request = request(
                List.of("salesDate$year", "salesDate$month", "salesAmount", "salesAmount__prior", "totalShare"),
                List.of("salesDate$year", "salesDate$month"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "yoy",
                        "range", "[)",
                        "value", List.of("2024-01-01", "2025-01-01"),
                        "targetMetrics", List.of("salesAmount")
                ));
        request.setCalculatedFields(List.of(
                new CalculatedFieldDef(
                        "totalShare",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(salesDate$month)), 0)"
                )));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                semanticQueryServiceV3.generateSql(TEST_MODEL, request, SemanticRequestContext.empty()));
        assertExceptionContains(ex, "CALCULATE_TIMEWINDOW_POST_CALC_UNSUPPORTED");
    }

    @Test
    @DisplayName("timeWindow post-calc sibling alias dependency is rejected by full semantic pipeline")
    void timeWindowPostCalcDependencyRejectedBySemanticPipeline() {
        SemanticQueryRequest request = request(
                List.of("salesDate$year", "salesDate$month", "salesAmount", "salesAmount__ratio",
                        "growthPercent", "growthPercentTwice"),
                List.of("salesDate$year", "salesDate$month"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "yoy",
                        "range", "[)",
                        "value", List.of("2024-01-01", "2025-01-01"),
                        "targetMetrics", List.of("salesAmount")
                ));
        request.setCalculatedFields(List.of(
                new CalculatedFieldDef("growthPercent", "salesAmount__ratio * 100"),
                new CalculatedFieldDef("growthPercentTwice", "growthPercent * 2")
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                semanticQueryServiceV3.generateSql(TEST_MODEL, request, SemanticRequestContext.empty()));
        assertExceptionContains(ex, "TIMEWINDOW_POST_CALCULATED_FIELD_DEPENDENCY_UNSUPPORTED");
    }

    @Test
    @DisplayName("timeWindow rejects top-level result-stage controls")
    void timeWindowTopLevelResultStageRejectedBySemanticPipeline() {
        SemanticQueryRequest postAggregateRequest = request(
                List.of("salesDate$month", "salesAmount", "salesShare"),
                List.of("salesDate$month"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "yoy",
                        "targetMetrics", List.of("salesAmount")
                ));
        postAggregateRequest.setPostAggregateCalculations(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "salesAmount", "grandTotal", "ratio"
        )));

        RuntimeException postAggregateException = assertThrows(RuntimeException.class, () ->
                semanticQueryServiceV3.queryModel(TEST_MODEL, postAggregateRequest, "execute", SemanticRequestContext.empty()));
        assertExceptionContains(postAggregateException, "TIME_WINDOW_RESULT_STAGE_UNSUPPORTED");
        assertExceptionContains(postAggregateException, "postAggregateCalculations");

        SemanticQueryRequest postSliceRequest = request(
                List.of("salesDate$month", "salesAmount", "salesAmount__prior"),
                List.of("salesDate$month"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "yoy",
                        "targetMetrics", List.of("salesAmount")
                ));
        postSliceRequest.setPostSlice(List.of(slice("salesAmount__prior", ">", 0)));

        RuntimeException postSliceException = assertThrows(RuntimeException.class, () ->
                semanticQueryServiceV3.generateSql(TEST_MODEL, postSliceRequest, SemanticRequestContext.empty()));
        assertExceptionContains(postSliceException, "TIME_WINDOW_RESULT_STAGE_UNSUPPORTED");
        assertExceptionContains(postSliceException, "postSlice");
    }

    @Test
    @DisplayName("timeWindow applies final orderBy and limit")
    void timeWindowFinalOrderByAndLimitAppliedBySemanticPipeline() {
        if (skipWhenWindowFunctionsUnsupported("timeWindow final orderBy and limit")) {
            return;
        }

        SemanticQueryRequest request = request(
                List.of("salesDate$id", "salesAmount", "salesAmount__rolling_7d"),
                List.of("salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "rolling_7d",
                        "targetMetrics", List.of("salesAmount")
                ));
        request.setOrderBy(List.of(order("salesAmount__rolling_7d", "desc")));
        request.setLimit(2);

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                TEST_MODEL, request, SemanticRequestContext.empty());
        assertNotNull(generated);
        assertTrue(generated.getSql().contains("ORDER BY"), generated.getSql());
        assertTrue(generated.getSql().contains("salesAmount__rolling_7d"), generated.getSql());
        assertTrue(generated.getSql().contains("LIMIT 2"), generated.getSql());

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty());
        assertEquals(2, response.getItems().size());
        assertTrue(numberValue(response.getItems().get(0).get("salesAmount__rolling_7d"))
                        .compareTo(numberValue(response.getItems().get(1).get("salesAmount__rolling_7d"))) >= 0,
                response.getItems().toString());
    }

    @Test
    @DisplayName("timeWindow rejects ambiguous top-level having")
    void timeWindowTopLevelHavingRejectedBySemanticPipeline() {
        SemanticQueryRequest request = request(
                List.of("salesDate$month", "salesAmount", "salesAmount__prior"),
                List.of("salesDate$month"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "yoy",
                        "targetMetrics", List.of("salesAmount")
                ));
        request.setHaving(List.of(slice("salesAmount", ">", 0)));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                semanticQueryServiceV3.generateSql(TEST_MODEL, request, SemanticRequestContext.empty()));
        assertExceptionContains(ex, "TIME_WINDOW_HAVING_UNSUPPORTED");
    }

    private SemanticQueryResponse execute(SemanticQueryRequest request) {
        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty());
        assertNotNull(response);
        assertNotNull(response.getItems());
        return response;
    }

    private boolean skipWhenWindowFunctionsUnsupported(String scenario) {
        if (supportsWindowFunctions()) {
            return false;
        }
        log.info("{} not executed on {} because this database does not support window functions",
                scenario, getDialectKey());
        return true;
    }

    private static void assertExceptionContains(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(expected)) {
                return;
            }
            current = current.getCause();
        }
        throw new AssertionError("Expected exception chain to contain " + expected, throwable);
    }

    private String quoteIdentifier(String identifier) {
        String dialect = getDialectKey();
        if (dialect.contains("mysql")) {
            return "`" + identifier + "`";
        }
        if (dialect.contains("sqlserver")) {
            return "[" + identifier + "]";
        }
        return "\"" + identifier + "\"";
    }

    private static SemanticQueryRequest request(
            List<String> columns,
            List<String> groupByFields,
            Map<String, Object> timeWindow) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(columns);
        request.setGroupBy(groupByFields.stream()
                .map(field -> {
                    SemanticQueryRequest.GroupByItem item = new SemanticQueryRequest.GroupByItem();
                    item.setField(field);
                    return item;
                })
                .toList());
        request.setTimeWindow(timeWindow);
        return request;
    }

    private static SemanticQueryRequest.SliceItem slice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp(op);
        item.setValue(value);
        return item;
    }

    private static SemanticQueryRequest.OrderItem order(String field, String dir) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        item.setField(field);
        item.setDir(dir);
        return item;
    }

    private static BigDecimal numberValue(Object value) {
        assertNotNull(value);
        return new BigDecimal(value.toString());
    }

    private static void assertRowsEqual(List<Map<String, Object>> expected, List<Map<String, Object>> actual) {
        assertRowsEqual(expected, actual, null);
    }

    private static void assertRowsEqual(
            List<Map<String, Object>> expected,
            List<Map<String, Object>> actual,
            String message) {
        assertFalse(actual.isEmpty(), "actual result should not be empty");
        List<Map<String, String>> expectedRows = canonicalRows(expected);
        List<Map<String, String>> actualRows = canonicalRows(actual);
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
                    + (message == null ? "" : "\nSQL:\n" + message);
        });
    }

    private static int firstDiff(List<Map<String, String>> expectedRows, List<Map<String, String>> actualRows) {
        int size = Math.min(expectedRows.size(), actualRows.size());
        for (int i = 0; i < size; i++) {
            if (!expectedRows.get(i).equals(actualRows.get(i))) {
                return i;
            }
        }
        return expectedRows.size() == actualRows.size() ? -1 : size;
    }

    private static List<Map<String, String>> canonicalRows(List<Map<String, Object>> rows) {
        List<Map<String, String>> canonical = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> normalized = new LinkedHashMap<>();
            row.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalized.put(entry.getKey(), canonicalValue(entry.getValue())));
            canonical.add(normalized);
        }
        canonical.sort(Comparator.comparing(Map::toString));
        return canonical;
    }

    private static String canonicalValue(Object value) {
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
}
