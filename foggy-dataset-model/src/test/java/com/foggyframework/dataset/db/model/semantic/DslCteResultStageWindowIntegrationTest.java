package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@DisplayName("DSL_CTE result-stage window real-SQL parity")
class DslCteResultStageWindowIntegrationTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("cumulative contribution result-stage window SQL executes and matches hand-written SQL")
    void cumulativeContributionResultStageWindowSqlMatchesHandWrittenSql() {
        if (!supportsWindowFunctions()) {
            log.info("cumulative contribution result-stage window parity not executed on {}", getDialectKey());
            return;
        }

        SemanticQueryRequest request = dslCtePlan(cumulativeContributionPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                TEST_MODEL, request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_base"), generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_window"), generated.getSql());
        assertTrue(generated.getSql().contains("RANK() OVER"), generated.getSql());
        assertTrue(generated.getSql().contains("\"cumulativeShare\" <= ?"), generated.getSql());

        List<Map<String, Object>> actual = jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0]));
        List<Map<String, Object>> expected = executeQuery("""
                WITH product_sales AS (
                    SELECT dp.sub_category_name AS "product$subCategoryName",
                           SUM(fs.sales_amount) AS "totalSalesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                    GROUP BY dp.sub_category_name
                ),
                product_window AS (
                    SELECT "product$subCategoryName",
                           "totalSalesAmount",
                           RANK() OVER (ORDER BY "totalSalesAmount" DESC) AS "salesRank",
                           (1.0 * SUM("totalSalesAmount") OVER (
                               ORDER BY "totalSalesAmount" DESC,
                                        "product$subCategoryName" ASC
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                           ) / NULLIF(SUM("totalSalesAmount") OVER (), 0)) AS "cumulativeShare"
                    FROM product_sales
                )
                SELECT "product$subCategoryName", "totalSalesAmount", "salesRank", "cumulativeShare"
                FROM product_window
                WHERE "cumulativeShare" <= 0.8
                ORDER BY "totalSalesAmount" DESC, "product$subCategoryName" ASC
                """);

        assertRowsEqual(expected, actual, generated.getSql());
    }

    private static SemanticQueryRequest dslCtePlan(Map<String, Object> ctePlan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL_CTE");
        request.setExecutablePlan(m("cte_plan", ctePlan));
        return request;
    }

    private static Map<String, Object> cumulativeContributionPlan() {
        return m(
                "stages", List.of(
                        stage("product_sales", "aggregate",
                                "input", m("model", TEST_MODEL),
                                "groupBy", List.of("product$subCategoryName"),
                                "metrics", List.of(m("name", "totalSalesAmount", "expr", "sum(salesAmount)"))),
                        stage("product_rank_contribution", "window_derive",
                                "inputs", List.of("product_sales"),
                                "window", m("partitionBy", List.of(),
                                        "orderBy", List.of(m("field", "totalSalesAmount", "dir", "DESC"))),
                                "derived", List.of(
                                        m("name", "salesRank", "expr", "rank()"),
                                        m("name", "cumulativeShare",
                                                "expr", "sum(totalSalesAmount) over order / sum(totalSalesAmount) over all"))),
                        stage("top_contribution_products", "postSlice",
                                "inputs", List.of("product_rank_contribution"),
                                "filters", List.of(m("field", "cumulativeShare", "op", "<=", "value", 0.8)))
                ),
                "output", List.of("product$subCategoryName", "totalSalesAmount", "salesRank", "cumulativeShare")
        );
    }

    private static Map<String, Object> stage(String name, String type, Object... rest) {
        Map<String, Object> result = m(rest);
        result.put("name", name);
        result.put("type", type);
        return result;
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            result.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return result;
    }

    private static void assertRowsEqual(
            List<Map<String, Object>> expected,
            List<Map<String, Object>> actual,
            String sql) {
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
                    + "\nSQL:\n" + sql;
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
