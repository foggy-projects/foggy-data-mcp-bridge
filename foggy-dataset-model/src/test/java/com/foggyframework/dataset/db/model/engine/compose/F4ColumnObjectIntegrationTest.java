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
                "columns", List.of(cdEntry),
                "limit", 50000));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT COUNT(DISTINCT fs.product_key) AS %s
                FROM fact_sales fs
                """.formatted(q("uniqueProducts")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    // --- G5 v2-patch-2 · Plain-field alias-only (Option A · synthesize CalculatedFieldDef) ---

    @Test
    @DisplayName("(a) F4 plain alias-only {field, as} — synthesize calc field, real SQL match")
    void f4PlainAliasOnlyMatchesNative() {
        Map<String, Object> aliasEntry = Map.of(
                "field", "salesAmount", "as", "revenue");
        DataSetResult actual = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(aliasEntry),
                "limit", 50000));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.sales_amount AS %s
                FROM fact_sales fs
                """.formatted(q("revenue")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    @Test
    @DisplayName("(e) F2 string \"base AS alias\" and F4 object {field, as} produce identical results")
    void f4PlainAliasEquivalentToF2String() {
        Map<String, Object> aliasEntry = Map.of(
                "field", "salesAmount", "as", "revenue");
        DataSetResult fromF4 = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(aliasEntry),
                "limit", 50000));

        DataSetResult fromF2 = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of("salesAmount AS revenue"),
                "limit", 50000));

        // F2 string and F4 object MUST produce identical SQL & results
        assertRowsEqual(fromF2.toList(), fromF4.toList(), true);
    }

    @Test
    @DisplayName("(g) F4 plain alias + groupBy — base field grouped, alias output")
    void f4PlainAliasGroupByCoordination() {
        // columns has F4 plain alias (synthesized as calc field), plus an aggregation
        Map<String, Object> aliasEntry = Map.of(
                "field", "salesAmount", "as", "revenue");
        DataSetResult actual = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(aliasEntry, "SUM(quantity) AS qty"),
                "groupBy", List.of("salesAmount"),
                "limit", 50000));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.sales_amount AS %s,
                       SUM(fs.quantity) AS %s
                FROM fact_sales fs
                GROUP BY fs.sales_amount
                """.formatted(q("revenue"), q("qty")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    @Test
    @DisplayName("(h) F4 chain rename: {field:'a',as:'x'} then {field:'x',as:'y'} resolves to base 'a'")
    void f4PlainAliasChainRename() {
        // First column synthesizes calc {x → salesAmount}; second column references "x" (not synthesized,
        // since "x" itself isn't a base field but a calc field — the ColumnObjectNormalizer just produces
        // "x AS y" string, which then synthesizes calc {y → x}; resolveBaseColumnReferences chains x → salesAmount.
        Map<String, Object> first = Map.of("field", "salesAmount", "as", "x");
        Map<String, Object> second = Map.of("field", "x", "as", "y");
        DataSetResult actual = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(first, second),
                "limit", 50000));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.sales_amount AS %s, fs.sales_amount AS %s
                FROM fact_sales fs
                """.formatted(q("x"), q("y")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    // --- G5 v2-patch-2 · Naming collision fail-fast (C1 / C2 / C3) ---

    @Test
    @DisplayName("(k) C1: alias collides with existing calc field → COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD")
    void f4PlainAliasCollidesWithCalcField() {
        // QM declares calc field "taxAmount2" via formulaDef (FactSalesModel.tm:216-226).
        Map<String, Object> aliasEntry = Map.of(
                "field", "salesAmount", "as", "taxAmount2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(aliasEntry))));
        assertTrue(ex.getMessage().contains("COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD"),
                "Expected COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("(l) C2: alias collides with QM physical field → COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD")
    void f4PlainAliasCollidesWithPhysicalField() {
        // Alias "costAmount" is a real measure in FactSalesModel.tm:198-202 — using it as alias
        // for salesAmount would silently shadow the real field. Engine must reject.
        Map<String, Object> aliasEntry = Map.of(
                "field", "salesAmount", "as", "costAmount");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(aliasEntry))));
        assertTrue(ex.getMessage().contains("COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD"),
                "Expected COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("(m) C3: duplicate alias in same request → COLUMN_ALIAS_DUPLICATE")
    void f4PlainAliasDuplicateInSameRequest() {
        Map<String, Object> first = Map.of("field", "salesAmount", "as", "x");
        Map<String, Object> second = Map.of("field", "costAmount", "as", "x");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(first, second))));
        assertTrue(ex.getMessage().contains("COLUMN_ALIAS_DUPLICATE"),
                "Expected COLUMN_ALIAS_DUPLICATE, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("(n) F4 dimension-suffix base + alias — user alias passthrough")
    void f4PlainAliasOnDimensionSuffixUsesUserAlias() {
        Map<String, Object> aliasEntry = Map.of(
                "field", "product$id", "as", "productId");

        DataSetResult actual = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(aliasEntry),
                "limit", 50000));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS %s
                FROM fact_sales fs
                """.formatted(q("productId")));

        assertRowsEqual(expected, actual.toList(), true);
    }

    @Test
    @DisplayName("(j) Metadata isolation: synthesized PLAIN_ALIAS calc must not leak into QM predefined calc fields")
    void f4PlainAliasMetadataIsolation() {
        // Snapshot QM predefined calc fields BEFORE any request-level synthesis
        var qmBefore = getQueryModel(SALES_MODEL);
        long countBefore = qmBefore.getPredefinedCalculatedFields().size();

        // Run a plain-alias query that synthesizes calc field "revenue" at request time
        Map<String, Object> aliasEntry = Map.of("field", "salesAmount", "as", "revenue");
        DataSetResult result = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of(aliasEntry)));
        assertFalse(result.toList().isEmpty(), "plain alias query should return rows");

        // Snapshot AFTER — request-level synthesis must not mutate QM-level metadata
        var qmAfter = getQueryModel(SALES_MODEL);
        long countAfter = qmAfter.getPredefinedCalculatedFields().size();
        assertEquals(countBefore, countAfter,
                "QM predefined calc field count must not change after request-level plain-alias synthesis");

        // No calc field named "revenue" should appear in QM-level metadata
        boolean leaked = qmAfter.getPredefinedCalculatedFields().stream()
                .anyMatch(c -> "revenue".equals(c.getName()));
        assertFalse(leaked,
                "Synthesized PLAIN_ALIAS calc field 'revenue' must NOT appear in QM predefinedCalculatedFields");
    }

    @Test
    @DisplayName("(o) F4 base field not found — error message uses alias-perspective wording")
    void f4PlainAliasBaseNotFoundShowsAliasPerspective() {
        Map<String, Object> aliasEntry = Map.of(
                "field", "nonexistent_field", "as", "myAlias");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(aliasEntry))));
        assertTrue(ex.getMessage().contains("COLUMN_FIELD_NOT_FOUND"),
                "Expected COLUMN_FIELD_NOT_FOUND, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("myAlias"),
                "Expected error to mention alias 'myAlias' for traceability, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("nonexistent_field"),
                "Expected error to mention base 'nonexistent_field', got: " + ex.getMessage());
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
        // G5 Phase 2 (F5) is now wired (PR-J1). When the `plan` value is not
        // a QueryPlan instance, normalize fail-loud at parse stage with
        // COLUMN_PLAN_TYPE_INVALID. (The pre-PR-J1 placeholder emitted
        // COLUMN_PLAN_NOT_VISIBLE for any plan key; that placeholder is
        // replaced now that F5 is wired through to the engine. Visibility
        // violations against an actual QueryPlan that's not in the lineage
        // surface as COLUMN_PLAN_NOT_VISIBLE at plan build stage instead —
        // see QueryPlanVisibilityTest for that path.)
        Map<String, Object> f5Entry = new HashMap<>();
        f5Entry.put("plan", new Object()); // not a QueryPlan instance
        f5Entry.put("field", "salesAmount");
        f5Entry.put("as", "x");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dsl(Map.of(
                        "model", SALES_MODEL,
                        "columns", List.of(f5Entry))));
        assertTrue(ex.getMessage().contains("COLUMN_PLAN_TYPE_INVALID"),
                "Expected COLUMN_PLAN_TYPE_INVALID, got: " + ex.getMessage());
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
