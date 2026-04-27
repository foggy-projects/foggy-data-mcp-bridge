package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G5 Phase 1 (F4) — Column object syntax integration tests.
 *
 * <p>Verifies that {@code dsl({columns: [{field, agg, as}, ...]})} F4 object form
 * produces identical query results to the equivalent native SQL baseline.
 * Compares real query results row-by-row per CLAUDE.md "集成测试规范" rule.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>F4 alias only — {@code {field, as}}</li>
 *   <li>F4 explicit aggregation — {@code {field, agg, as}}</li>
 *   <li>F4 mixed array — F1 string + F3 string + F4 object</li>
 *   <li>F4 count_distinct — verifies engine lowering to {@code COUNT(DISTINCT field)}</li>
 *   <li>F4 error cases — {@code COLUMN_FIELD_REQUIRED} / {@code COLUMN_AGG_NOT_SUPPORTED}
 *       / {@code COLUMN_AS_TYPE_INVALID}</li>
 * </ul>
 */
@DisplayName("G5 F4 column object integration")
class F4ColumnObjectIntegrationTest extends EcommerceTestSupport {

    private static final String SALES_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private DataSource dataSource;

    // --- Successful F4 cases (real SQL data comparison) ---

    @Test
    @DisplayName("F4 field-only (no agg, no as): {field: 'product$id'} equivalent to F1 string passthrough")
    void f4FieldOnlyEquivalentToF1String() {
        // F4 field-only entry — should pass through as-is (equivalent to F1 string).
        // This is the most basic F4 form: object wrapping a bare field name.
        Map<String, Object> f4Entry = Map.of("field", "product$id");
        DataSetResult fromF4 = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(f4Entry, "SUM(salesAmount) AS total"),
                "groupBy", List.of("product$id")));

        DataSetResult fromF1 = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of("product$id", "SUM(salesAmount) AS total"),
                "groupBy", List.of("product$id")));

        // Both forms should produce identical SQL → identical results
        assertRowsEqual(fromF1.toList(), fromF4.toList(), true);
    }

    @Test
    @DisplayName("F4 explicit agg: {field: 'salesAmount', agg: 'sum', as: 'total'} matches native")
    void f4ExplicitAggMatchesNative() {
        Map<String, Object> sumEntry = Map.of(
                "field", "salesAmount", "agg", "sum", "as", "total");
        DataSetResult actual = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of("product$id", sumEntry),
                "groupBy", List.of("product$id")));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS %s,
                       SUM(fs.sales_amount) AS %s
                FROM fact_sales fs
                GROUP BY fs.product_key
                """.formatted(q("product$id"), q("total")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    @Test
    @DisplayName("F4 mixed array: F1 string + F3 string + F4 object all coexist correctly")
    void f4MixedArrayMatchesNative() {
        // F1 string + F3 string + F4 object
        Map<String, Object> f4Entry = Map.of(
                "field", "salesAmount", "agg", "avg", "as", "avgAmt");
        DataSetResult actual = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(
                        "product$id",                                // F1
                        "SUM(salesAmount) AS totalAmt",              // F3
                        f4Entry                                       // F4
                ),
                "groupBy", List.of("product$id")));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS %s,
                       SUM(fs.sales_amount) AS %s,
                       AVG(fs.sales_amount) AS %s
                FROM fact_sales fs
                GROUP BY fs.product_key
                """.formatted(q("product$id"), q("totalAmt"), q("avgAmt")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    @Test
    @DisplayName("F4 count_distinct: lowers to COUNT(DISTINCT field) via AllowedFunctions / SqlFunctionExp")
    void f4CountDistinctLowersToNativeCountDistinct() {
        Map<String, Object> cdEntry = Map.of(
                "field", "product$id", "agg", "count_distinct", "as", "uniqueProducts");
        DataSetResult actual = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(cdEntry)));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT COUNT(DISTINCT fs.product_key) AS %s
                FROM fact_sales fs
                """.formatted(q("uniqueProducts")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    // --- Error code cases (no SQL — pure validation) ---

    @Test
    @DisplayName("F4 missing field key → COLUMN_FIELD_REQUIRED")
    void f4MissingFieldRaisesError() {
        // Use HashMap to allow optional null field if user mistakenly passes null
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("agg", "sum");
        invalid.put("as", "x");
        // No "field" key

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(invalid))));
        assertTrue(ex.getMessage().contains("COLUMN_FIELD_REQUIRED"),
                "Expected COLUMN_FIELD_REQUIRED, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("F4 unknown agg → COLUMN_AGG_NOT_SUPPORTED")
    void f4UnknownAggRaisesError() {
        Map<String, Object> invalid = Map.of(
                "field", "salesAmount", "agg", "median", "as", "x");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(invalid))));
        assertTrue(ex.getMessage().contains("COLUMN_AGG_NOT_SUPPORTED"),
                "Expected COLUMN_AGG_NOT_SUPPORTED, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("F4 non-string as → COLUMN_AS_TYPE_INVALID")
    void f4NonStringAliasRaisesError() {
        Map<String, Object> invalid = Map.of(
                "field", "salesAmount", "agg", "sum", "as", 123);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(invalid))));
        assertTrue(ex.getMessage().contains("COLUMN_AS_TYPE_INVALID"),
                "Expected COLUMN_AS_TYPE_INVALID, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("F5 plan-qualified form fail-loud (Phase 2 not yet supported)")
    void f5PlanQualifiedFailsLoud() {
        Map<String, Object> f5Entry = new HashMap<>();
        f5Entry.put("plan", new Object()); // any non-null plan ref triggers the placeholder
        f5Entry.put("field", "salesAmount");
        f5Entry.put("as", "x");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(f5Entry))));
        assertTrue(ex.getMessage().contains("COLUMN_PLAN_NOT_VISIBLE"),
                "Expected COLUMN_PLAN_NOT_VISIBLE, got: " + ex.getMessage());
    }

    // --- Helpers (mirror ComposedDataSetResultIntegrationTest pattern) ---

    private DataSetResult dsl(Map<String, Object> params) {
        DslQueryFunction function = new DslQueryFunction(
                semanticQueryServiceV3, SemanticRequestContext.empty(), dataSource);
        return (DataSetResult) function.executeFunction(null, params);
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

    private static void assertRowsEqual(List<Map<String, Object>> expected,
                                        List<Map<String, Object>> actual,
                                        boolean requireNonEmpty) {
        if (!requireNonEmpty) {
            assertEquals(canonicalRows(expected), canonicalRows(actual));
            return;
        }
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
