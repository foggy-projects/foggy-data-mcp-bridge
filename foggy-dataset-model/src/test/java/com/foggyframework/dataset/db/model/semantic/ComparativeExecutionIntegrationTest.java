package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Comparative execution (YoY/MoM/WoW) bypassing QueryFacade.
 */
@Slf4j
@DisplayName("Comparative Execution Integration Test")
public class ComparativeExecutionIntegrationTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Test
    @DisplayName("S8.6 YoY execution matches hand-written SQL")
    void testYoYExecutionMatchesSql() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        
        // Group by year and month
        SemanticQueryRequest.GroupByItem yearGroup = new SemanticQueryRequest.GroupByItem();
        yearGroup.setField("salesDate$year");
        
        SemanticQueryRequest.GroupByItem monthGroup = new SemanticQueryRequest.GroupByItem();
        monthGroup.setField("salesDate$month");
        
        request.setGroupBy(List.of(yearGroup, monthGroup));
        
        // Query the timeWindow dynamically generated columns
        request.setColumns(List.of(
                "salesDate$year", 
                "salesDate$month", 
                "salesAmount", 
                "salesAmount__prior", 
                "salesAmount__diff", 
                "salesAmount__ratio"
        ));

        // Define YoY timeWindow
        Map<String, Object> timeWindow = Map.of(
                "field", "salesDate$id",
                "grain", "month",
                "comparison", "yoy",
                "targetMetrics", List.of("salesAmount")
        );
        request.setTimeWindow(timeWindow);
        
        // Ensure some limit
        request.setLimit(50);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty()
        );

        assertNotNull(response);
        assertNotNull(response.getItems());
        
        log.info("Returned rows: {}", response.getItems().size());
        String monthlySql = """
                SELECT d.year AS %s,
                       d.month AS %s,
                       SUM(fs.sales_amount) AS %s
                FROM fact_sales fs
                LEFT JOIN dim_date d ON fs.date_key = d.date_key
                GROUP BY d.year, d.month
                """.formatted(q("salesDate$year"), q("salesDate$month"), q("salesAmount"));
        List<Map<String, Object>> expected = executeQuery("""
                SELECT %s,
                       %s,
                       %s,
                       %s AS %s,
                       %s - %s AS %s,
                       CASE
                           WHEN %s IS NULL OR %s = 0 THEN NULL
                           ELSE (%s - %s) / %s
                       END AS %s
                FROM (
                    %s
                ) cur
                LEFT JOIN (
                    %s
                ) prior
                       ON %s = %s + 1
                      AND %s = %s
                """.formatted(
                ref("cur", "salesDate$year"),
                ref("cur", "salesDate$month"),
                ref("cur", "salesAmount"),
                ref("prior", "salesAmount"),
                q("salesAmount__prior"),
                ref("cur", "salesAmount"),
                ref("prior", "salesAmount"),
                q("salesAmount__diff"),
                ref("prior", "salesAmount"),
                ref("prior", "salesAmount"),
                ref("cur", "salesAmount"),
                ref("prior", "salesAmount"),
                ref("prior", "salesAmount"),
                q("salesAmount__ratio"),
                monthlySql,
                monthlySql,
                ref("cur", "salesDate$year"),
                ref("prior", "salesDate$year"),
                ref("cur", "salesDate$month"),
                ref("prior", "salesDate$month")
        ));

        assertRowsEqual(expected, response.getItems());
    }

    @Test
    @DisplayName("Quarterly YoY execution matches hand-written SQL")
    void testQuarterlyYoYExecutionMatchesSql() {
        SemanticQueryRequest request = new SemanticQueryRequest();

        SemanticQueryRequest.GroupByItem yearGroup = new SemanticQueryRequest.GroupByItem();
        yearGroup.setField("salesDate$year");

        SemanticQueryRequest.GroupByItem quarterGroup = new SemanticQueryRequest.GroupByItem();
        quarterGroup.setField("salesDate$quarter");

        request.setGroupBy(List.of(yearGroup, quarterGroup));
        request.setColumns(List.of(
                "salesDate$year",
                "salesDate$quarter",
                "salesAmount",
                "salesAmount__prior",
                "salesAmount__diff",
                "salesAmount__ratio"
        ));
        request.setTimeWindow(Map.of(
                "field", "salesDate$id",
                "grain", "quarter",
                "comparison", "yoy",
                "targetMetrics", List.of("salesAmount")
        ));
        request.setLimit(50);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty()
        );

        assertNotNull(response);
        assertNotNull(response.getItems());

        String quarterlySql = """
                SELECT d.year AS %s,
                       d.quarter AS %s,
                       SUM(fs.sales_amount) AS %s
                FROM fact_sales fs
                LEFT JOIN dim_date d ON fs.date_key = d.date_key
                GROUP BY d.year, d.quarter
                """.formatted(q("salesDate$year"), q("salesDate$quarter"), q("salesAmount"));
        List<Map<String, Object>> expected = executeQuery("""
                SELECT %s,
                       %s,
                       %s,
                       %s AS %s,
                       %s - %s AS %s,
                       CASE
                           WHEN %s IS NULL OR %s = 0 THEN NULL
                           ELSE (%s - %s) / %s
                       END AS %s
                FROM (
                    %s
                ) cur
                LEFT JOIN (
                    %s
                ) prior
                       ON %s = %s + 1
                      AND %s = %s
                """.formatted(
                ref("cur", "salesDate$year"),
                ref("cur", "salesDate$quarter"),
                ref("cur", "salesAmount"),
                ref("prior", "salesAmount"),
                q("salesAmount__prior"),
                ref("cur", "salesAmount"),
                ref("prior", "salesAmount"),
                q("salesAmount__diff"),
                ref("prior", "salesAmount"),
                ref("prior", "salesAmount"),
                ref("cur", "salesAmount"),
                ref("prior", "salesAmount"),
                ref("prior", "salesAmount"),
                q("salesAmount__ratio"),
                quarterlySql,
                quarterlySql,
                ref("cur", "salesDate$year"),
                ref("prior", "salesDate$year"),
                ref("cur", "salesDate$quarter"),
                ref("prior", "salesDate$quarter")
        ));

        assertRowsEqual(expected, response.getItems());
    }

    private String q(String identifier) {
        String dialect = getDialectKey();
        if (dialect.contains("mysql")) {
            return "`" + identifier + "`";
        }
        if (dialect.contains("sqlserver")) {
            return "[" + identifier + "]";
        }
        return "\"" + identifier + "\"";
    }

    private String ref(String alias, String identifier) {
        return alias + "." + q(identifier);
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
