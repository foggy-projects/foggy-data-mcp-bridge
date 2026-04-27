package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR3 · End-to-end {@link ComposeSqlCompiler#compilePlanToSql} verification
 * that {@link PlanColumnRef} columns inside a {@link DerivedQueryPlan} compile
 * to alias-qualified SQL when the G10 flag is on, and to bare-name SQL when
 * the flag is off (M4 baseline preserved).
 *
 * <p>Exercised via {@link DerivedQueryPlan} whose {@code columns()} list
 * contains {@link PlanColumnRef} pointing at the inner base plan — the same
 * shape produced by the chained API path
 * ({@code base.fluentSelect(new PlanColumnRef(base, ...))}).</p>
 *
 * <p>Cross-cuts the full compile pipeline:
 * {@code compileBase} (registers alias) → {@code compileDerived}
 * (renderOuterSelect with alias map) → {@code compileExpression(PlanColumnRef)}
 * (alias-qualified emit). Verifies the wiring through all three steps.</p>
 */
@DisplayName("G10 PR3 · plan-aware lowering end-to-end")
class PlanAwareLoweringTest {

    @AfterEach
    void clearOverride() {
        ComposeFeatureFlags.overrideG10Enabled(null);
    }

    private static ComposedSql compile(QueryPlan plan,
                                       FakeSemanticService svc,
                                       Map<String, ModelBinding> bindings,
                                       String dialect) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect(dialect)
                        .build());
    }

    private static BaseModelPlan factSales() {
        return CompileTestHelpers.base("FactSales", "productId", "salesAmount");
    }

    private static Map<String, ModelBinding> factSalesBindings() {
        return Map.of("FactSales", CompileTestHelpers.emptyBinding());
    }

    /**
     * Build a derived plan whose {@code columns()} include a {@link PlanColumnRef}
     * pointing at the inner base — same shape the chained API produces via
     * {@code base.fluentSelect(new PlanColumnRef(base, "..."))}.
     */
    private static DerivedQueryPlan derivedWithPlanRef(BaseModelPlan source, String column) {
        return DerivedQueryPlan.builder()
                .source(source)
                .columns(List.of(new PlanColumnRef(source, column)))
                .build();
    }

    /**
     * Helper: extract the inner SELECT clause of {@code WITH cte_N AS (...)} —
     * that's where {@code renderOuterSelect} does its work and where the
     * G10 alias-qualification differs from M4. The outermost wrapping
     * SELECT (after the {@code WITH}) is always alias-qualified by
     * {@code appendSelectColumns} regardless of flag, so it doesn't tell
     * us anything about PR3's routing.
     */
    private static String cteInnerSelect(String sql, String cteName) {
        int idx = sql.indexOf("WITH " + cteName + " AS (");
        if (idx < 0) return null;
        int start = idx + ("WITH " + cteName + " AS (").length();
        int depth = 1;
        for (int i = start; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') {
                depth--;
                if (depth == 0) return sql.substring(start, i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // flag=false → M4 byte-for-byte baseline
    // ------------------------------------------------------------------

    @Test
    @DisplayName("flag=false：DerivedQueryPlan + PlanColumnRef → 裸列名（M4 兼容）")
    void flagOffEmitsBareName() {
        ComposeFeatureFlags.overrideG10Enabled(false);
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("FactSales", "SELECT product_key AS productId, sales_amount AS salesAmount FROM fact_sales");

        BaseModelPlan base = factSales();
        DerivedQueryPlan derived = derivedWithPlanRef(base, "productId");
        ComposedSql sql = compile(derived, svc,
                factSalesBindings(), "sqlite");

        // The cte_1 inner SELECT (from renderOuterSelect) references the
        // column without alias prefix under flag=false.
        String inner = cteInnerSelect(sql.getSql(), "cte_1");
        assertNotNull(inner, "cte_1 must exist under sqlite dialect: " + sql.getSql());
        assertFalse(inner.contains("cte_0."),
                "flag=false: cte_1 inner SELECT should not alias-qualify the renderOuterSelect column. inner=" + inner);
    }

    // ------------------------------------------------------------------
    // flag=true → alias-qualified emit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("flag=true：DerivedQueryPlan + PlanColumnRef → cte_0.column 路由")
    void flagOnEmitsAliasQualified() {
        ComposeFeatureFlags.overrideG10Enabled(true);
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("FactSales", "SELECT product_key AS productId, sales_amount AS salesAmount FROM fact_sales");

        BaseModelPlan base = factSales();
        DerivedQueryPlan derived = derivedWithPlanRef(base, "productId");
        ComposedSql sql = compile(derived, svc,
                factSalesBindings(), "sqlite");

        String inner = cteInnerSelect(sql.getSql(), "cte_1");
        assertNotNull(inner, "cte_1 must exist: " + sql.getSql());
        // The renderOuterSelect column is alias-qualified by cte_0 under flag=true.
        assertTrue(inner.contains("cte_0."),
                "flag=true: cte_1 inner SELECT should alias-qualify via cte_0. inner=" + inner);
    }

    @Test
    @DisplayName("flag=true：多个 PlanColumnRef → 每个都路由到同一 alias")
    void flagOnRoutesEveryPlanRef() {
        ComposeFeatureFlags.overrideG10Enabled(true);
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("FactSales", "SELECT product_key AS productId, sales_amount AS salesAmount FROM fact_sales");

        BaseModelPlan base = factSales();
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of(
                        new PlanColumnRef(base, "productId"),
                        new PlanColumnRef(base, "salesAmount")))
                .build();
        ComposedSql sql = compile(derived, svc,
                factSalesBindings(), "sqlite");

        String inner = cteInnerSelect(sql.getSql(), "cte_1");
        assertNotNull(inner);
        assertEquals(2, CompileTestHelpers.countOccurrences(inner, "cte_0."),
                "flag=true: 两个 PlanColumnRef 都应路由 → cte_0. 出现 2 次。inner=" + inner);
    }


    @Test
    @DisplayName("flag=true：混合 PlanColumnRef + 字符串列 → 仅 plan-qualified 部分被路由")
    void flagOnMixedColumnsRouteOnlyPlanRefs() {
        ComposeFeatureFlags.overrideG10Enabled(true);
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("FactSales", "SELECT product_key AS productId, sales_amount AS salesAmount FROM fact_sales");

        BaseModelPlan base = factSales();
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of(
                        "productId",                              // bare string — no alias
                        new PlanColumnRef(base, "salesAmount")))  // plan-qualified — alias
                .build();
        ComposedSql sql = compile(derived, svc,
                factSalesBindings(), "sqlite");

        String inner = cteInnerSelect(sql.getSql(), "cte_1");
        assertNotNull(inner);
        assertEquals(1, CompileTestHelpers.countOccurrences(inner, "cte_0."),
                "flag=true: 仅 PlanColumnRef 路由（1 次 cte_0.），裸字符串保持原状。inner=" + inner);
    }

    // ------------------------------------------------------------------
    // Data-equivalence sanity check (compile-level)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("flag 切换：cte_1 内层 SELECT 的 alias 前缀计数能正确反映")
    void flagFlipChangesAliasPrefixCount() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("FactSales", "SELECT product_key AS productId, sales_amount AS salesAmount FROM fact_sales");

        BaseModelPlan base = factSales();
        DerivedQueryPlan derived = derivedWithPlanRef(base, "productId");

        ComposeFeatureFlags.overrideG10Enabled(false);
        String legacy = compile(derived, svc,
                factSalesBindings(), "sqlite").getSql();

        ComposeFeatureFlags.overrideG10Enabled(true);
        String g10 = compile(derived, svc,
                factSalesBindings(), "sqlite").getSql();

        assertNotEquals(legacy, g10,
                "flag 切换必须产生可观察的 SQL 差异");

        String legacyInner = cteInnerSelect(legacy, "cte_1");
        String g10Inner = cteInnerSelect(g10, "cte_1");
        assertFalse(legacyInner.contains("cte_0."),
                "flag=false: cte_1 inner SELECT 不应包含 cte_0. 前缀。inner=" + legacyInner);
        assertTrue(g10Inner.contains("cte_0."),
                "flag=true: cte_1 inner SELECT 必须包含 cte_0. 前缀。inner=" + g10Inner);
    }
}
