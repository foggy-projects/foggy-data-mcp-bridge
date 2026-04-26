package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TimeWindow execution real-SQL parity")
class TimeWindowExecutionIntegrationTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("rolling_7d daily execution matches hand-written SQL")
    void rolling7dDailyExecutionMatchesSql() {
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
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT d.date_key AS "salesDate$id",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    GROUP BY d.date_key
                )
                SELECT "salesDate$id",
                       "salesAmount",
                       SUM("salesAmount") OVER (
                           ORDER BY "salesDate$id"
                           ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
                       ) AS "salesAmount__rolling_7d"
                FROM daily
                """);

        assertRowsEqual(expected, response.getItems());
    }

    @Test
    @DisplayName("rolling_7d SQL preview matches hand-written SQL")
    void rolling7dGenerateSqlMatchesSql() {
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
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT d.date_key AS "salesDate$id",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    GROUP BY d.date_key
                )
                SELECT "salesDate$id",
                       "salesAmount",
                       SUM("salesAmount") OVER (
                           ORDER BY "salesDate$id"
                           ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
                       ) AS "salesAmount__rolling_7d"
                FROM daily
                """);

        assertRowsEqual(expected, actual);
    }

    @Test
    @DisplayName("MTD daily execution matches hand-written SQL")
    void mtdDailyExecutionMatchesSql() {
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
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT d.year AS "salesDate$year",
                           d.month AS "salesDate$month",
                           d.date_key AS "salesDate$id",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    GROUP BY d.year, d.month, d.date_key
                )
                SELECT "salesDate$year",
                       "salesDate$month",
                       "salesDate$id",
                       "salesAmount",
                       SUM("salesAmount") OVER (
                           PARTITION BY "salesDate$year", "salesDate$month"
                           ORDER BY "salesDate$id"
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       ) AS "salesAmount__mtd"
                FROM daily
                """);

        assertRowsEqual(expected, response.getItems());
    }

    @Test
    @DisplayName("YTD daily execution matches hand-written SQL")
    void ytdDailyExecutionMatchesSql() {
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
        List<Map<String, Object>> expected = executeQuery("""
                WITH daily AS (
                    SELECT d.year AS "salesDate$year",
                           d.date_key AS "salesDate$id",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    GROUP BY d.year, d.date_key
                )
                SELECT "salesDate$year",
                       "salesDate$id",
                       "salesAmount",
                       SUM("salesAmount") OVER (
                           PARTITION BY "salesDate$year"
                           ORDER BY "salesDate$id"
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       ) AS "salesAmount__ytd"
                FROM daily
                """);

        assertRowsEqual(expected, response.getItems());
    }

    private SemanticQueryResponse execute(SemanticQueryRequest request) {
        request.setLimit(100);
        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty());
        assertNotNull(response);
        assertNotNull(response.getItems());
        return response;
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

    private static void assertRowsEqual(List<Map<String, Object>> expected, List<Map<String, Object>> actual) {
        assertFalse(actual.isEmpty(), "actual result should not be empty");
        assertEquals(canonicalRows(expected), canonicalRows(actual));
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
