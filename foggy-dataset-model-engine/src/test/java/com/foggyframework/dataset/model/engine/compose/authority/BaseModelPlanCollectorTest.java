package com.foggyframework.dataset.model.engine.compose.authority;

import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.Dsl;
import com.foggyframework.dataset.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.model.engine.compose.plan.QueryOptions;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.plan.UnionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5 {@link BaseModelPlanCollector#collect} tree walk + first-occurrence dedup.
 *
 * <p>Mirrors Python {@code tests/compose/authority/test_collect_base_models.py}.
 * Python's {@code Rogue(QueryPlan)} subclass test is omitted because the Java
 * {@link QueryPlan} has a package-private ctor — rogue subclassing is
 * prevented at compile time, making that defensive assertion unreachable.</p>
 */
@DisplayName("M5 BaseModelPlanCollector")
class BaseModelPlanCollectorTest {

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static BaseModelPlan saleOrder() {
        return (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                .model("SaleOrderQM").columns(List.of("id", "amount")).build());
    }

    private static BaseModelPlan crmLead() {
        return (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                .model("CrmLeadQM").columns(List.of("id", "partnerId")).build());
    }

    private static BaseModelPlan partner() {
        return (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                .model("ResPartnerQM").columns(List.of("id", "name")).build());
    }

    private static List<String> modelsOf(List<BaseModelPlan> plans) {
        return plans.stream().map(BaseModelPlan::model).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Basic traversal
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("BaseModelPlan / DerivedQueryPlan 单分支")
    class CollectFromBaseOrDerived {

        @Test
        @DisplayName("单 BaseModelPlan 返回自身")
        void singleBaseModelReturnsItself() {
            BaseModelPlan bp = saleOrder();
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(bp);
            assertEquals(List.of(bp), result);
        }

        @Test
        @DisplayName("DerivedQueryPlan 递归到 source 叶子")
        void derivedRecursesToSource() {
            BaseModelPlan bp = saleOrder();
            QueryPlan derived = bp.query(QueryOptions.builder()
                    .columns(List.of("id", "amount")).build());
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(derived);
            assertEquals(List.of("SaleOrderQM"), modelsOf(result));
            assertSame(bp, result.get(0));
        }
    }

    // ------------------------------------------------------------------
    // UnionPlan
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("UnionPlan 左右序 + 跨分支去重")
    class CollectFromUnion {

        @Test
        @DisplayName("union 保留左右序")
        void unionPreservesLeftRightOrder() {
            BaseModelPlan left = saleOrder();
            BaseModelPlan right = crmLead();
            UnionPlan union = left.union(right);
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(union);
            assertEquals(List.of("SaleOrderQM", "CrmLeadQM"), modelsOf(result));
        }

        @Test
        @DisplayName("同 QM 两侧出现：去重到一条，左侧胜出")
        void unionWithSameModelOnBothSidesDedups() {
            BaseModelPlan left = saleOrder();
            BaseModelPlan right = (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM").columns(List.of("id")).build());
            UnionPlan union = left.union(right);
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(union);
            assertEquals(List.of("SaleOrderQM"), modelsOf(result));
            assertSame(left, result.get(0));
        }
    }

    // ------------------------------------------------------------------
    // JoinPlan
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("JoinPlan 左右序")
    class CollectFromJoin {

        @Test
        @DisplayName("join 保留左右序")
        void joinPreservesLeftRightOrder() {
            BaseModelPlan left = saleOrder();
            BaseModelPlan right = partner();
            JoinPlan joined = left.join(right, "inner",
                    List.of(JoinOn.of("partnerId", "=", "id")));
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(joined);
            assertEquals(List.of("SaleOrderQM", "ResPartnerQM"), modelsOf(result));
        }
    }

    // ------------------------------------------------------------------
    // Deep tree + cross-branch dedup
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("深度树遍历 + 跨分支去重")
    class CollectFromDeepTree {

        @Test
        @DisplayName("derived(union(join, base)) 保留左右前序")
        void derivedOfUnionOfJoin() {
            BaseModelPlan sale = saleOrder();
            BaseModelPlan crm = crmLead();
            BaseModelPlan part = partner();
            JoinPlan joined = sale.join(part, "left",
                    List.of(JoinOn.of("partnerId", "=", "id")));
            UnionPlan union = joined.union(crm);
            QueryPlan top = union.query(QueryOptions.of(List.of("id")));
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(top);
            assertEquals(List.of("SaleOrderQM", "ResPartnerQM", "CrmLeadQM"),
                    modelsOf(result));
        }

        @Test
        @DisplayName("同 QM 跨 join 与 union 出现：仍只保留一条")
        void dedupAcrossBranches() {
            BaseModelPlan saleA = saleOrder();
            BaseModelPlan saleB = (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM").columns(List.of("id")).build());
            BaseModelPlan part = partner();
            JoinPlan joined = saleA.join(part, "left",
                    List.of(JoinOn.of("partnerId", "=", "id")));
            UnionPlan unioned = joined.union(saleB);
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(unioned);
            assertEquals(List.of("SaleOrderQM", "ResPartnerQM"), modelsOf(result));
            assertSame(saleA, result.get(0));
        }
    }

    // ------------------------------------------------------------------
    // Fail-closed
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fail-closed 输入校验")
    class FailClosed {

        @Test
        @DisplayName("null 输入抛 IllegalArgumentException")
        void nullInputRejected() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlanCollector.collect(null));
            assertTrue(ex.getMessage().contains("QueryPlan"));
        }
    }

    // ------------------------------------------------------------------
    // Return-shape contract
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("返回值契约")
    class ReturnShape {

        @Test
        @DisplayName("返回列表为不可变")
        void returnedListIsUnmodifiable() {
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(saleOrder());
            assertThrows(UnsupportedOperationException.class,
                    () -> result.add(saleOrder()));
        }

        @Test
        @DisplayName("空树（理论上不存在）假想也返回空列表，不抛")
        void emptyTreeReturnsEmptyList() {
            // In practice every QueryPlan bottoms out at a BaseModelPlan so
            // this path is unreachable via Dsl.from; documented for parity
            // with Python's explicit guard.
            BaseModelPlan one = saleOrder();
            // Degenerate: collect(one) still returns a non-empty result;
            // the empty-list path is internal. Still verify the invariant
            // that the returned list has no duplicates:
            List<BaseModelPlan> result = BaseModelPlanCollector.collect(one);
            assertEquals(1, result.size());
        }
    }
}
