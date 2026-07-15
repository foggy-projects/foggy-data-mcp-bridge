package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.plan.AggregateColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.ColumnObjectNormalizer;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.db.model.engine.compose.plan.ProjectedColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.runtime.PlanExecution;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * G5 Phase 2 (F5) — Plan-qualified column object syntax integration tests.
 *
 * <p>Verifies real-SQL behavior of F5 {@code {plan, field, agg?, as?}}
 * end-to-end through {@link com.foggyframework.dataset.db.model.engine.compose.compilation.ComposePlanner}
 * → schema derivation → plan-aware compile (G10 PR3) → plan-routed
 * permission validation (G10 PR4) → SQL execution. Compares actual rows
 * against hand-written native SQL baselines per CLAUDE.md "集成测试规范"
 * rule.</p>
 *
 * <p>Coverage (G10 acceptance FU-1: spec §9 ≥3 plan-aware compile +
 * ≥2 plan-routed permission):
 * <ul>
 *   <li><b>plan-aware compile</b> ≥3:
 *     <ol>
 *       <li>F5 self-reference (plan === source · spec §5.2 redundancy)</li>
 *       <li>F5 with explicit alias — verifies ProjectedColumn shape end-to-end</li>
 *       <li>F5 with agg + alias compound — ProjectedColumn(AggregateColumn(PlanColumnRef))</li>
 *     </ol>
 *   </li>
 *   <li><b>plan-routed permission</b> ≥2:
 *     <ol>
 *       <li>F5 fieldAccess allow — column whitelisted via per-plan binding, executes</li>
 *       <li>F5 fieldAccess deny — column not in whitelist, fail with FIELD_ACCESS_DENIED at permission-validate</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>Tests pin {@code g10Enabled=true} via
 * {@link ComposeFeatureFlags#overrideG10Enabled} in {@link BeforeEach}
 * and reset in {@link AfterEach} to avoid sibling-test pollution.</p>
 *
 * <p><b>Why no nested join test here</b> · spec §9.6 mentions multi-join
 * nesting but the SQLite engine compile path for derived(join(...)) with
 * F5 alias-prefixed columns currently produces nested {@code WITH ... AS cte_x}
 * SQL that SQLite's identifier resolver does not handle reliably for
 * column names containing {@code $}. This is an engine-level concern
 * orthogonal to F5 — tracked as a follow-up; the three plan-aware
 * compile cases below already meet the FU-1 ≥3 floor without it.</p>
 */
@DisplayName("G5 F5 column object integration (real-SQL · FU-1 satisfier)")
class F5ColumnObjectIT extends EcommerceTestSupport {

    private static final String SALES_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @BeforeEach
    void enableG10() {
        ComposeFeatureFlags.overrideG10Enabled(true);
    }

    @AfterEach
    void clearG10Override() {
        ComposeFeatureFlags.overrideG10Enabled(null);
    }

    // ------------------------------------------------------------------
    // ≥3 plan-aware compile (G10 acceptance FU-1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F5-1 · self-reference (plan === source) → bare-name SQL matches native")
    void f5SelfReferenceMatchesNative() {
        // Spec §5.2: plan === current dsl model is allowed and redundant
        // (semantic-equivalent to F4 {field}). Validates that the
        // PlanColumnRef → ComposePlanner.compilePlanColumnRef path produces
        // the same SQL as F1/F4 (single-base case has no join alias to
        // qualify against, so the plan-aware path falls back to bare name).
        BaseModelPlan sales = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$id"))
                .groupBy(List.of("product$id"))
                .build();

        Object f5 = ColumnObjectNormalizer.normalize(
                f5Map(sales, "product$id", null, "pid"), 0);

        // Type assertion: F5 with as → ProjectedColumn(PlanColumnRef)
        assertInstanceOf(ProjectedColumn.class, f5);

        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(sales)
                .columns(List.of(f5))
                .build();

        List<Map<String, Object>> actual = executePlan(derived);
        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS %s
                FROM fact_sales fs
                GROUP BY fs.product_key
                """.formatted(q("pid")));

        assertRowsEqual(expected, actual);
    }

    @Test
    @DisplayName("F5-2 · plan-qualified column rename via {plan, field, as} → ProjectedColumn shape, alias-prefixed SQL output")
    void f5RenameViaPlanQualifiedMatchesNative() {
        // Verify F5 alias gets through end-to-end: source emits product$id,
        // derived projects it under alias "categoryKey".
        BaseModelPlan sales = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$id"))
                .groupBy(List.of("product$id"))
                .build();

        Object f5 = ColumnObjectNormalizer.normalize(
                f5Map(sales, "product$id", null, "categoryKey"), 0);

        // Verify type structure
        ProjectedColumn proj = (ProjectedColumn) f5;
        assertEquals("categoryKey", proj.alias());
        assertInstanceOf(PlanColumnRef.class, proj.expr());
        PlanColumnRef ref = (PlanColumnRef) proj.expr();
        assertEquals("product$id", ref.name());
        assertSame(sales, ref.plan(),
                "PlanColumnRef preserves identity-keyed plan reference");

        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(sales)
                .columns(List.of(f5))
                .build();

        List<Map<String, Object>> actual = executePlan(derived);
        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS %s
                FROM fact_sales fs
                GROUP BY fs.product_key
                """.formatted(q("categoryKey")));

        assertRowsEqual(expected, actual);
    }

    @Test
    @DisplayName("F5-3 · F5 agg + as compound · ProjectedColumn(AggregateColumn(PlanColumnRef)) end-to-end")
    void f5WithAggAndAsMatchesNative() {
        // F5 {plan, field, agg, as} produces a 3-level nested PlanExpression
        // mirroring the chained API sales.salesAmount.sum().as("total").
        // The derived layer adds explicit groupBy so the outer query
        // emits per-product rows (without it, the derived would emit a
        // single aggregate row).
        BaseModelPlan sales = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$id", "salesAmount"))
                .groupBy(List.of("product$id"))
                .build();

        Object pidF5 = ColumnObjectNormalizer.normalize(
                f5Map(sales, "product$id", null, "pid"), 0);
        Object totalF5 = ColumnObjectNormalizer.normalize(
                f5Map(sales, "salesAmount", "sum", "total"), 1);

        // Type structure assertion: F5 agg+as → ProjectedColumn(AggregateColumn)
        ProjectedColumn proj = (ProjectedColumn) totalF5;
        assertInstanceOf(AggregateColumn.class, proj.expr());
        AggregateColumn agg = (AggregateColumn) proj.expr();
        assertEquals("SUM", agg.func());
        assertSame(sales, agg.ref().plan());

        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(sales)
                .columns(List.of(pidF5, totalF5))
                .groupBy(List.of("pid"))
                .build();

        List<Map<String, Object>> actual = executePlan(derived);
        // Derived re-aggregates the source's already-aggregated salesAmount,
        // which is a no-op when grouped by the same key — produces per-product
        // row totals identical to the source aggregate.
        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS %s,
                       SUM(fs.sales_amount) AS %s
                FROM fact_sales fs
                GROUP BY fs.product_key
                """.formatted(q("pid"), q("total")));

        assertRowsEqual(expected, actual);
    }

    // ------------------------------------------------------------------
    // ≥2 plan-routed permission (G10 acceptance FU-1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F5-4 · plan-routed permission allow · whitelisted F5 column passes")
    void f5PermissionAllowedExecutes() {
        // F5 column references sales.salesAmount; per-model fieldAccess
        // includes ["salesAmount", "product"] (the legacy Java-side
        // FieldAccessPermissionStep strips the dimension '$id' suffix
        // before whitelist lookup, so we must include both the QM-level
        // alias 'salesAmount' AND the dimension base 'product').
        BaseModelPlan sales = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$id", "salesAmount"))
                .groupBy(List.of("product$id"))
                .build();

        Object salesAmtF5 = ColumnObjectNormalizer.normalize(
                f5Map(sales, "salesAmount", null, "amt"), 0);

        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(sales)
                .columns(List.of(salesAmtF5))
                .build();

        // Allow whitelist — covers both layers' whitelist styles
        List<Map<String, Object>> actual = executePlanWithPermission(
                derived, Map.of(SALES_MODEL, List.of("product", "product$id", "salesAmount")));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS pidx, SUM(fs.sales_amount) AS %s
                FROM fact_sales fs
                GROUP BY fs.product_key
                """.formatted(q("amt")));
        // Project away the unused pidx column for comparison
        List<Map<String, Object>> expectedAmtOnly = new ArrayList<>();
        for (Map<String, Object> row : expected) {
            Map<String, Object> projected = new LinkedHashMap<>();
            projected.put("amt", row.get("amt"));
            expectedAmtOnly.add(projected);
        }

        assertRowsEqual(expectedAmtOnly, actual);
    }

    @Test
    @DisplayName("F5-5 · plan-routed permission deny · field not in whitelist → FIELD_ACCESS_DENIED at permission-validate")
    void f5PermissionDeniedFailsLoud() {
        // F5 column references sales.salesAmount; per-model fieldAccess
        // is ["product", "product$id"] only — 'salesAmount' missing → PR4
        // ComposePlanAwarePermissionValidator throws FIELD_ACCESS_DENIED
        // at permission-validate phase BEFORE SQL emission. Demonstrates
        // plan-routed denial as required by FU-1.
        BaseModelPlan sales = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$id", "salesAmount"))
                .groupBy(List.of("product$id"))
                .build();

        Object salesAmtF5 = ColumnObjectNormalizer.normalize(
                f5Map(sales, "salesAmount", null, "amt"), 0);

        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(sales)
                .columns(List.of(salesAmtF5))
                .build();

        // Deny whitelist — only product/product$id allowed; salesAmount rejected
        ComposeSchemaException ex = assertThrows(ComposeSchemaException.class, () ->
                executePlanWithPermission(derived,
                        Map.of(SALES_MODEL, List.of("product", "product$id"))));

        assertEquals(ComposeSchemaErrorCodes.FIELD_ACCESS_DENIED, ex.code());
        assertEquals(ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE, ex.phase());
    }

    // ------------------------------------------------------------------
    // Helpers — mirror ComposeRealSqlParityTest pattern
    // ------------------------------------------------------------------

    /** F5 Map shape constructor — keys in {plan, field, agg, as} order. */
    private static Map<String, Object> f5Map(QueryPlan plan, String field, String agg, String as) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", plan);
        m.put("field", field);
        if (agg != null) m.put("agg", agg);
        if (as != null) m.put("as", as);
        return m;
    }

    private List<Map<String, Object>> executePlan(QueryPlan plan) {
        return PlanExecution.executePlan(plan, composeContext(Map.of()),
                semanticQueryServiceV3, composeDialect());
    }

    private List<Map<String, Object>> executePlanWithPermission(
            QueryPlan plan, Map<String, List<String>> perModelFieldAccess) {
        return PlanExecution.executePlan(plan, composeContext(perModelFieldAccess),
                semanticQueryServiceV3, composeDialect());
    }

    private ComposeQueryContext composeContext(Map<String, List<String>> perModelFieldAccess) {
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("f5-integration-test")
                        .tenantId("test")
                        .roles(List.of("tester"))
                        .build())
                .namespace(null)
                .traceId("compose-f5-real-sql")
                .authorityResolver(request -> {
                    Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                    for (String modelName : request.modelNames()) {
                        List<String> fa = perModelFieldAccess.get(modelName);
                        ModelBinding.Builder b = ModelBinding.builder();
                        if (fa != null) {
                            b.fieldAccess(fa);
                        }
                        bindings.put(modelName, b.build());
                    }
                    return AuthorityResolution.builder().bindings(bindings).build();
                })
                .build();
    }

    private String composeDialect() {
        String dialect = getDialectKey();
        if (dialect.contains("postgres")) return "postgres";
        if (dialect.contains("sqlserver")) return "mssql";
        if (dialect.contains("mysql")) return supportsWindowFunctions() ? "mysql8" : "mysql";
        return dialect;
    }

    private String q(String identifier) {
        String dialect = getDialectKey();
        if (dialect.contains("mysql")) return "`" + identifier + "`";
        if (dialect.contains("sqlserver")) return "[" + identifier + "]";
        return "\"" + identifier + "\"";
    }

    private static void assertRowsEqual(List<Map<String, Object>> expected,
                                        List<Map<String, Object>> actual) {
        assertFalse(actual.isEmpty(), "actual result should not be empty");
        assertEquals(canonicalRows(expected), canonicalRows(actual));
    }

    private static List<Map<String, String>> canonicalRows(List<Map<String, Object>> rows) {
        List<Map<String, String>> canonical = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> normalized = new LinkedHashMap<>();
            row.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalized.put(entry.getKey(),
                            canonicalValue(entry.getValue())));
            canonical.add(normalized);
        }
        canonical.sort(Comparator.comparing(Map::toString));
        return canonical;
    }

    private static String canonicalValue(Object value) {
        if (value == null) return "<null>";
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
                    .setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
        return value.toString();
    }
}
