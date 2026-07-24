package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.plan.expr.BinaryExpr;
import com.foggyframework.dataset.model.engine.compose.plan.expr.ColumnExpr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR3 · {@link ComposePlanner#compileExpression(Object, String, Map)}
 * plan-aware overload contract.
 *
 * <p>Three regimes:
 * <ul>
 *   <li>2-arg legacy form / empty map → bare-name rendering, identical to M4
 *       behaviour (independent of flag state).</li>
 *   <li>3-arg form, flag=false → behaves like the 2-arg form even when an
 *       alias map is provided. Required so PR3 can ship without flipping
 *       the production flag.</li>
 *   <li>3-arg form, flag=true → {@link PlanColumnRef} routes through the
 *       map; unknown plans still fall back to bare-name (e.g. PlanColumnRef
 *       constructed outside the compile state).</li>
 * </ul>
 */
@DisplayName("G10 PR3 · ComposePlanner.compileExpression plan-aware overload")
class PlanAwareCompileExpressionTest {

    @AfterEach
    void clearOverride() {
        ComposeFeatureFlags.overrideG10Enabled(null);
    }

    private static QueryPlan stubPlan(String model) {
        return BaseModelPlan.builder().model(model).build();
    }

    // ------------------------------------------------------------------
    // 2-arg legacy form / empty map
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2-arg legacy / empty alias map → bare-name rendering")
    class LegacyOrEmptyMap {

        @Test
        @DisplayName("PlanColumnRef 走旧 2-arg 形式 → 裸列名")
        void twoArgFormProducesBareName() {
            PlanColumnRef ref = new PlanColumnRef(stubPlan("OrderQM"), "name");
            assertEquals("name", ComposePlanner.compileExpression(ref, "sqlite"));
        }

        @Test
        @DisplayName("flag=true 但 alias map 空 → 仍裸列名")
        void emptyMapFallsBackEvenWithFlagOn() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            PlanColumnRef ref = new PlanColumnRef(stubPlan("OrderQM"), "name");
            assertEquals("name",
                    ComposePlanner.compileExpression(ref, "sqlite",
                            Collections.emptyMap()));
        }

        @Test
        @DisplayName("非 PlanColumnRef 表达式不受 alias map 影响")
        void nonPlanColumnRefUnaffected() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            // ColumnExpr operands → no plan attached, alias map should not change output
            BinaryExpr expr = new BinaryExpr(
                    new ColumnExpr("a"), "+", new ColumnExpr("b"));
            String legacy = ComposePlanner.compileExpression(expr, "sqlite");
            String withMap = ComposePlanner.compileExpression(expr, "sqlite",
                    Map.of(stubPlan("X"), "cte_99"));
            assertEquals(legacy, withMap);
        }
    }

    // ------------------------------------------------------------------
    // 3-arg form, flag=false → bare-name (alias map ignored)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("flag=false：alias map 被忽略，行为同 M4")
    class FlagOffIgnoresMap {

        @Test
        @DisplayName("即使 plan 在 alias map 中，flag=false 仍裸列名")
        void mapIgnoredWhenFlagOff() {
            ComposeFeatureFlags.overrideG10Enabled(false);
            QueryPlan plan = stubPlan("OrderQM");
            Map<QueryPlan, String> aliasMap = new IdentityHashMap<>();
            aliasMap.put(plan, "cte_0");
            PlanColumnRef ref = new PlanColumnRef(plan, "name");
            assertEquals("name",
                    ComposePlanner.compileExpression(ref, "sqlite", aliasMap));
        }
    }

    // ------------------------------------------------------------------
    // 3-arg form, flag=true → alias-qualified
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("flag=true + alias map：plan-qualified")
    class FlagOnAliasQualified {

        @Test
        @DisplayName("PlanColumnRef plan 在 map 中 → cte_N.column")
        void planInMapEmitsAliasDot() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan orderPlan = stubPlan("OrderQM");
            QueryPlan customerPlan = stubPlan("CustomerQM");
            Map<QueryPlan, String> aliasMap = new IdentityHashMap<>();
            aliasMap.put(orderPlan, "cte_0");
            aliasMap.put(customerPlan, "cte_1");

            PlanColumnRef orderName = new PlanColumnRef(orderPlan, "name");
            PlanColumnRef customerName = new PlanColumnRef(customerPlan, "name");

            assertEquals("cte_0.name",
                    ComposePlanner.compileExpression(orderName, "sqlite", aliasMap));
            assertEquals("cte_1.name",
                    ComposePlanner.compileExpression(customerName, "sqlite", aliasMap));
        }

        @Test
        @DisplayName("alias map 缺该 plan → fallback 裸列名（不抛异常）")
        void unknownPlanFallsBackToBare() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan known = stubPlan("OrderQM");
            QueryPlan unknown = stubPlan("LegacyQM");
            Map<QueryPlan, String> aliasMap = new IdentityHashMap<>();
            aliasMap.put(known, "cte_0");

            PlanColumnRef refUnknown = new PlanColumnRef(unknown, "name");
            assertEquals("name",
                    ComposePlanner.compileExpression(refUnknown, "sqlite", aliasMap));
        }

        @Test
        @DisplayName("PlanColumnRef.plan() 为 null → fallback 裸列名")
        void nullPlanFallsBackToBare() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            Map<QueryPlan, String> aliasMap = new IdentityHashMap<>();
            aliasMap.put(stubPlan("X"), "cte_0");

            PlanColumnRef refNullPlan = new PlanColumnRef(null, "name");
            assertEquals("name",
                    ComposePlanner.compileExpression(refNullPlan, "sqlite", aliasMap));
        }

        @Test
        @DisplayName("需要 quote 的列名 → cte_N.\"snakeCase\" 等正确处理")
        void columnQuotingPreservedUnderAlias() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan plan = stubPlan("X");
            Map<QueryPlan, String> aliasMap = Map.of(plan, "cte_0");

            // camelCase identifier triggers quoting in PostgreSQL
            PlanColumnRef refCamel = new PlanColumnRef(plan, "orderId");
            String pg = ComposePlanner.compileExpression(refCamel, "postgres", aliasMap);
            assertTrue(pg.startsWith("cte_0."),
                    "alias prefix preserved on quoted column: " + pg);
            assertTrue(pg.contains("orderId"),
                    "column body retained: " + pg);
        }

        @Test
        @DisplayName("两个 plan 实例 model 相同但身份不同 → alias 不混淆")
        void identityKeyedNotEqualityKeyed() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan p1 = stubPlan("OrderQM");
            QueryPlan p2 = stubPlan("OrderQM");   // 不同实例
            assertNotSame(p1, p2);
            Map<QueryPlan, String> aliasMap = new IdentityHashMap<>();
            aliasMap.put(p1, "cte_0");
            aliasMap.put(p2, "cte_1");

            assertEquals("cte_0.amount",
                    ComposePlanner.compileExpression(
                            new PlanColumnRef(p1, "amount"), "sqlite", aliasMap));
            assertEquals("cte_1.amount",
                    ComposePlanner.compileExpression(
                            new PlanColumnRef(p2, "amount"), "sqlite", aliasMap));
        }

        @Test
        @DisplayName("BinaryExpr 嵌套 PlanColumnRef → 两侧都被路由")
        void binaryExprWithPlanRefsBothRouted() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan plan = stubPlan("X");
            Map<QueryPlan, String> aliasMap = Map.of(plan, "cte_0");

            BinaryExpr expr = new BinaryExpr(
                    new PlanColumnRef(plan, "amount"),
                    "+",
                    new PlanColumnRef(plan, "tax"));
            assertEquals("(cte_0.amount + cte_0.tax)",
                    ComposePlanner.compileExpression(expr, "sqlite", aliasMap));
        }
    }
}
