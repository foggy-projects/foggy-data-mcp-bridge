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
        List<Map<String, Object>> expected = executeQuery("""
                WITH monthly AS (
                    SELECT d.year AS "salesDate$year",
                           d.month AS "salesDate$month",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    GROUP BY d.year, d.month
                )
                SELECT cur."salesDate$year",
                       cur."salesDate$month",
                       cur."salesAmount",
                       prior."salesAmount" AS "salesAmount__prior",
                       cur."salesAmount" - prior."salesAmount" AS "salesAmount__diff",
                       CASE
                           WHEN prior."salesAmount" IS NULL OR prior."salesAmount" = 0 THEN NULL
                           ELSE (cur."salesAmount" - prior."salesAmount") / prior."salesAmount"
                       END AS "salesAmount__ratio"
                FROM monthly cur
                LEFT JOIN monthly prior
                       ON cur."salesDate$year" = prior."salesDate$year" + 1
                      AND cur."salesDate$month" = prior."salesDate$month"
                """);

        assertRowsEqual(expected, response.getItems());
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
