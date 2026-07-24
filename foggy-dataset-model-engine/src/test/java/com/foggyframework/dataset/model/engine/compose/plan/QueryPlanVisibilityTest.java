package com.foggyframework.dataset.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G5 Phase 2 (F5) · {@link QueryPlan#collectVisiblePlans()} contract +
 * F5 plan visibility validation at plan build time per spec §5.1.
 *
 * <p>Two regimes:
 * <ul>
 *   <li><b>collectVisiblePlans</b> · structural correctness — the four
 *       plan-node types each contribute the right subtree.</li>
 *   <li><b>F5 visibility validation</b> · build-time check that any
 *       {@link PlanColumnRef} in {@code columns} (whether from chained API
 *       or F5 Map normalize) references a plan in the lineage.</li>
 * </ul>
 *
 * <p><b>Identity-keyed</b> per spec §5.1 warning — same model name
 * referenced via two distinct {@code dsl()} calls produces two distinct
 * plan instances that are NOT interchangeable.</p>
 */
@DisplayName("G5 F5 · QueryPlan visibility (collectVisiblePlans + build-time check)")
class QueryPlanVisibilityTest {

    private static BaseModelPlan basePlan(String model) {
        return BaseModelPlan.builder().model(model).columns(List.of("id")).build();
    }

    private static Map<String, Object> f5Map(QueryPlan plan, String field) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", plan);
        m.put("field", field);
        return m;
    }

    // ------------------------------------------------------------------
    // collectVisiblePlans structural contract
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("collectVisiblePlans · 4 plan-node types")
    class CollectVisible {

        @Test
        @DisplayName("BaseModelPlan · {self}")
        void baseLeaf() {
            BaseModelPlan base = basePlan("X");
            Set<QueryPlan> visible = base.collectVisiblePlans();

            assertEquals(1, visible.size());
            assertTrue(visible.contains(base));
        }

        @Test
        @DisplayName("DerivedQueryPlan · {self} ∪ source.visible")
        void derivedAddsSelfPlusSourceLineage() {
            BaseModelPlan base = basePlan("X");
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(base)
                    .columns(List.of("id"))
                    .build();

            Set<QueryPlan> visible = derived.collectVisiblePlans();
            assertEquals(2, visible.size());
            assertTrue(visible.contains(derived));
            assertTrue(visible.contains(base));
        }

        @Test
        @DisplayName("JoinPlan · {self} ∪ left.visible ∪ right.visible")
        void joinAddsSelfPlusBothBranches() {
            BaseModelPlan a = basePlan("A");
            BaseModelPlan b = basePlan("B");
            JoinPlan join = JoinPlan.builder()
                    .left(a).right(b).type(JoinType.INNER)
                    .on(List.of(JoinOn.of("id", "=", "id")))
                    .build();

            Set<QueryPlan> visible = join.collectVisiblePlans();
            assertEquals(3, visible.size());
            assertTrue(visible.contains(join));
            assertTrue(visible.contains(a));
            assertTrue(visible.contains(b));
        }

        @Test
        @DisplayName("UnionPlan · {self} ∪ left.visible ∪ right.visible")
        void unionAddsSelfPlusBothBranches() {
            BaseModelPlan a = basePlan("A");
            BaseModelPlan b = basePlan("B");
            UnionPlan union = UnionPlan.builder().left(a).right(b).all(true).build();

            Set<QueryPlan> visible = union.collectVisiblePlans();
            assertEquals(3, visible.size());
            assertTrue(visible.contains(union));
            assertTrue(visible.contains(a));
            assertTrue(visible.contains(b));
        }

        @Test
        @DisplayName("Deeply nested · derived(join(a, b)) sees {derived, join, a, b}")
        void deeplyNestedDerivedSeesAllSubtree() {
            BaseModelPlan a = basePlan("A");
            BaseModelPlan b = basePlan("B");
            JoinPlan join = JoinPlan.builder()
                    .left(a).right(b).type(JoinType.INNER)
                    .on(List.of(JoinOn.of("id", "=", "id"))).build();
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(join)
                    .columns(List.of("id"))
                    .build();

            Set<QueryPlan> visible = derived.collectVisiblePlans();
            assertEquals(4, visible.size(),
                    "derived + its source-join + join's left/right");
            assertTrue(visible.contains(derived));
            assertTrue(visible.contains(join));
            assertTrue(visible.contains(a));
            assertTrue(visible.contains(b));
        }

        @Test
        @DisplayName("Identity-keyed · same model name, two instances → two distinct entries")
        void identityKeyedSetSeesBothInstances() {
            BaseModelPlan a1 = basePlan("X");
            BaseModelPlan a2 = basePlan("X");  // same model, distinct instance
            assertEquals(a1, a2, "value-equal under dataclass equals");
            assertNotSame(a1, a2, "distinct object identity");

            JoinPlan join = JoinPlan.builder()
                    .left(a1).right(a2).type(JoinType.INNER)
                    .on(List.of(JoinOn.of("id", "=", "id"))).build();

            Set<QueryPlan> visible = join.collectVisiblePlans();
            // join + a1 + a2 (NOT join + a1 — equals would fold them)
            assertEquals(3, visible.size(),
                    "identity-keyed set keeps both same-model instances distinct");
        }
    }

    // ------------------------------------------------------------------
    // F5 visibility validation at plan build time
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Build-time visibility · DerivedQueryPlan")
    class DerivedVisibilityCheck {

        @Test
        @DisplayName("self-reference (plan === source) → allowed (spec §5.2)")
        void selfReferenceAllowed() {
            BaseModelPlan base = basePlan("X");
            // {plan: base, field: "id"} where source == base
            Object f5 = ColumnObjectNormalizer.normalize(f5Map(base, "id"), 0);

            // build should not throw
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(base)
                    .columns(List.of(f5))
                    .build();

            assertNotNull(derived);
        }

        @Test
        @DisplayName("plan in lineage (join.left.field on join-derived) → allowed")
        void planInLineageAllowed() {
            BaseModelPlan a = basePlan("A");
            BaseModelPlan b = basePlan("B");
            JoinPlan join = JoinPlan.builder()
                    .left(a).right(b).type(JoinType.INNER)
                    .on(List.of(JoinOn.of("id", "=", "id"))).build();

            Object f5LeftRef = ColumnObjectNormalizer.normalize(f5Map(a, "id"), 0);
            Object f5RightRef = ColumnObjectNormalizer.normalize(f5Map(b, "id"), 1);

            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(join)
                    .columns(List.of(f5LeftRef, f5RightRef))
                    .build();
            assertNotNull(derived);
        }

        @Test
        @DisplayName("plan NOT in lineage → COLUMN_PLAN_NOT_VISIBLE")
        void crossPlanReferenceRejected() {
            BaseModelPlan base = basePlan("X");
            BaseModelPlan unrelated = basePlan("Y");
            Object f5Bad = ColumnObjectNormalizer.normalize(f5Map(unrelated, "id"), 0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> DerivedQueryPlan.builder()
                            .source(base)
                            .columns(List.of(f5Bad))
                            .build());

            assertTrue(ex.getMessage().startsWith("COLUMN_PLAN_NOT_VISIBLE:"),
                    "got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("DerivedQueryPlan.columns"),
                    "field name in error message");
        }

        @Test
        @DisplayName("Identity-keyed · same-model two instances NOT interchangeable")
        void sameModelTwoInstancesNotInterchangeable() {
            BaseModelPlan a1 = basePlan("X");
            BaseModelPlan a2 = basePlan("X");  // value-equal, distinct identity
            // {plan: a2, field: "id"} but source=a1 (a2 not in a1's lineage)
            Object f5 = ColumnObjectNormalizer.normalize(f5Map(a2, "id"), 0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> DerivedQueryPlan.builder()
                            .source(a1)
                            .columns(List.of(f5))
                            .build());
            assertTrue(ex.getMessage().startsWith("COLUMN_PLAN_NOT_VISIBLE:"),
                    "spec §5.1 — identity, not equals");
        }

        @Test
        @DisplayName("F4 string columns mixed with F5 — only F5 entries checked")
        void f4StringMixedWithF5() {
            BaseModelPlan base = basePlan("X");
            Object f5 = ColumnObjectNormalizer.normalize(f5Map(base, "id"), 1);

            // F1 string + F4 string + F5 PlanColumnRef — only F5 needs visibility
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(base)
                    .columns(List.of("name", "amount AS a", f5))
                    .build();
            assertNotNull(derived);
        }

        @Test
        @DisplayName("Chained API · myBase.amount.sum() in derived works (same code path as F5)")
        void chainedApiPlanColumnRefAllowed() {
            BaseModelPlan base = basePlan("X");
            // chained: base.amount.sum() → AggregateColumn(PlanColumnRef(base, "amount"), "SUM")
            PlanColumnRef chained = new PlanColumnRef(base, "amount");
            AggregateColumn agg = chained.sum();

            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(base)
                    .columns(List.of(agg))
                    .build();
            assertNotNull(derived,
                    "chained API uses the same PlanColumnRef-based shape as F5; "
                    + "must coexist with the new visibility check");
        }
    }

    @Nested
    @DisplayName("Build-time visibility · BaseModelPlan")
    class BaseVisibilityCheck {

        @Test
        @DisplayName("BaseModelPlan with PlanColumnRef in columns → COLUMN_PLAN_NOT_VISIBLE")
        void basePlanWithPlanRefRejected() {
            BaseModelPlan myBase = basePlan("X");
            // Try to construct a SECOND base whose columns reference myBase
            Object f5 = ColumnObjectNormalizer.normalize(f5Map(myBase, "id"), 0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder()
                            .model("Y")
                            .columns(List.of(f5))
                            .build());
            assertTrue(ex.getMessage().startsWith("COLUMN_PLAN_NOT_VISIBLE:"),
                    "Base plans are leaves with no lineage; any PlanColumnRef → fail-loud");
            assertTrue(ex.getMessage().contains("BaseModelPlan.columns"),
                    "field name in error message");
        }

        @Test
        @DisplayName("BaseModelPlan with only F1-F4 strings still works")
        void basePlanWithStringsOnlyAllowed() {
            BaseModelPlan base = BaseModelPlan.builder()
                    .model("X")
                    .columns(List.of("id", "name AS n"))
                    .build();
            assertNotNull(base);
        }
    }
}
