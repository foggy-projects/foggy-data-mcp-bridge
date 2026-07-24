package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.plan.expr.ColumnExpr;
import com.foggyframework.dataset.model.engine.compose.plan.expr.LiteralExpr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardian tests for the {@code columns} API contract — see
 * {@code docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-需求.md}.
 *
 * <p>These tests freeze the post-收口 API surface so the dual-field
 * {@code columnsObj} pattern cannot silently re-enter the codebase via a
 * future PR. Two of the assertions are reflective on purpose — they catch
 * regressions that pure compile-time checks would miss (e.g. someone adding
 * a new {@code Builder.columnsObj(...)} as a "convenience").</p>
 *
 * <p>Heterogeneous list semantics are also asserted: {@code String} and
 * {@code PlanExpression} elements coexist in one list; non-conforming
 * element types fail-closed at {@code build()} time.</p>
 *
 * @since 8.3.0.beta
 */
class ColumnsApiContractTest {

    // ------------------------------------------------------------------
    // BaseModelPlan
    // ------------------------------------------------------------------

    @Test
    @DisplayName("BaseModelPlan.columns accepts mixed String + PlanExpression")
    void baseModelPlanAcceptsMixedColumns() {
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of(
                        "rawCol",
                        new ColumnExpr("astCol"),
                        new LiteralExpr(42)))
                .build();
        assertEquals(3, plan.columns().size());
        assertEquals("rawCol", plan.columns().get(0));
        assertTrue(plan.columns().get(1) instanceof ColumnExpr);
        assertTrue(plan.columns().get(2) instanceof LiteralExpr);
    }

    @Test
    @DisplayName("Query.col creates PlanExpression usable in columns")
    void queryFactoryColCreatesPlanExpressionForColumns() {
        Object ref = QueryFactory.INSTANCE.invoke(null, "col", new Object[] {"amountTotal"});
        assertTrue(ref instanceof PlanColumnRef);

        ProjectedColumn total = ((PlanColumnRef) ref).sum().as("total");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("partnerId", total))
                .build();

        assertEquals(2, plan.columns().size());
        assertEquals("SUM(amountTotal) AS total", plan.columns().get(1).toString());
    }

    @Test
    @DisplayName("BaseModelPlan.columns rejects non-String/PlanExpression elements")
    void baseModelPlanRejectsIllegalElement() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                BaseModelPlan.builder()
                        .model("SaleOrderQM")
                        .columns(Arrays.asList("ok", 42))
                        .build());
        assertTrue(ex.getMessage().contains("BaseModelPlan.columns[1]"),
                "error must point to the offending index, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Integer"),
                "error must mention the actual type, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("BaseModelPlan.columns rejects null and empty-string elements")
    void baseModelPlanRejectsNullAndEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                BaseModelPlan.builder()
                        .model("SaleOrderQM")
                        .columns(Arrays.asList("ok", null))
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                BaseModelPlan.builder()
                        .model("SaleOrderQM")
                        .columns(Arrays.asList("ok", ""))
                        .build());
    }

    // ------------------------------------------------------------------
    // DerivedQueryPlan
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DerivedQueryPlan.columns accepts mixed elements; empty allowed for fluent intermediate")
    void derivedQueryPlanAcceptsMixedAndEmpty() {
        BaseModelPlan source = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("a"))
                .build();

        DerivedQueryPlan mixed = DerivedQueryPlan.builder()
                .source(source)
                .columns(List.of("alias", new ColumnExpr("inner")))
                .build();
        assertEquals(2, mixed.columns().size());

        DerivedQueryPlan emptyOk = DerivedQueryPlan.builder()
                .source(source)
                .build();
        assertTrue(emptyOk.columns().isEmpty(),
                "intermediate fluent stages may have empty columns");
    }

    // ------------------------------------------------------------------
    // Reflective guards — these block the dual API from coming back.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("No public columnsObj surface on plan / options builders")
    void columnsObjMustNotResurface() throws Exception {
        Class<?>[] guarded = new Class<?>[] {
                BaseModelPlan.class,
                BaseModelPlan.Builder.class,
                DerivedQueryPlan.class,
                DerivedQueryPlan.Builder.class,
                Dsl.FromOptions.class,
                Dsl.FromOptions.Builder.class,
                QueryOptions.class,
                QueryOptions.Builder.class,
        };
        for (Class<?> cls : guarded) {
            for (Method m : cls.getDeclaredMethods()) {
                assertFalse(m.getName().equals("columnsObj"),
                        cls.getName() + " must not expose columnsObj() — see "
                                + "docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-需求.md");
            }
            assertFalse(hasField(cls, "columnsObj"),
                    cls.getName() + " must not declare a columnsObj field");
        }
    }

    @Test
    @DisplayName("Builder.columns accepts wildcard List<?>")
    void buildersExposeWildcardSetter() throws Exception {
        assertWildcardColumnsSetter(BaseModelPlan.Builder.class);
        assertWildcardColumnsSetter(DerivedQueryPlan.Builder.class);
        assertWildcardColumnsSetter(Dsl.FromOptions.Builder.class);
        assertWildcardColumnsSetter(QueryOptions.Builder.class);
    }

    private static void assertWildcardColumnsSetter(Class<?> builder) throws Exception {
        Method setter = null;
        for (Method m : builder.getDeclaredMethods()) {
            if ("columns".equals(m.getName()) && m.getParameterCount() == 1) {
                setter = m;
                break;
            }
        }
        if (setter == null) {
            throw new AssertionError(builder.getName() + " missing columns(List<?>) setter");
        }
        assertEquals(java.util.List.class, setter.getParameterTypes()[0],
                builder.getName() + ".columns must accept java.util.List");
        // Generic signature should be List<?> (i.e. List<? extends Object>) to keep
        // calls like .columns(List.of("a","b")) wide-open.
        java.lang.reflect.Type paramType = setter.getGenericParameterTypes()[0];
        String typeStr = paramType.toString();
        assertTrue(typeStr.contains("?") || typeStr.contains("? extends"),
                builder.getName() + ".columns must use wildcard List<?>; got: " + typeStr);
    }

    private static boolean hasField(Class<?> cls, String name) {
        for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
            if (f.getName().equals(name)) return true;
        }
        return false;
    }
}
