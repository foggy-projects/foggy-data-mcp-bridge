package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 BaseModelPlan 不变量 — 跨仓对齐 Python test_base_model_plan.py。
 */
@DisplayName("M2 BaseModelPlan")
class BaseModelPlanTest {

    @Nested
    @DisplayName("构造")
    class Construction {

        @Test
        @DisplayName("最小构造：其他字段默认 ()/null/false")
        void minimalValidConstruction() {
            BaseModelPlan p = BaseModelPlan.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("id", "name"))
                    .build();
            assertEquals("SaleOrderQM", p.model());
            assertEquals(List.of("id", "name"), p.columns());
            assertTrue(p.slice().isEmpty());
            assertTrue(p.groupBy().isEmpty());
            assertTrue(p.orderBy().isEmpty());
            assertNull(p.limit());
            assertNull(p.start());
            assertFalse(p.distinct());
        }

        @Test
        @DisplayName("model 必填且非空")
        void modelRequiredNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model("").columns(List.of("id")).build());
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model(null).columns(List.of("id")).build());
        }

        @Test
        @DisplayName("columns 必填且非空")
        void columnsRequiredNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model("X").columns(List.of()).build());
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model("X").columns(null).build());
        }

        @Test
        @DisplayName("columns 条目必须非空字符串")
        void columnsEntriesMustBeNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model("X")
                            .columns(Arrays.asList("id", "")).build());
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model("X")
                            .columns(Arrays.asList("id", (String) null)).build());
        }

        @Test
        @DisplayName("limit 必须非负或 null")
        void limitMustBeNonNegativeOrNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model("X").columns(List.of("id")).limit(-1).build());

            // null and 0 both legal
            assertNull(BaseModelPlan.builder().model("X").columns(List.of("id"))
                    .limit(null).build().limit());
            assertEquals(0, BaseModelPlan.builder().model("X").columns(List.of("id"))
                    .limit(0).build().limit());
        }

        @Test
        @DisplayName("start 必须非负或 null")
        void startMustBeNonNegativeOrNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> BaseModelPlan.builder().model("X").columns(List.of("id")).start(-1).build());
            assertEquals(0, BaseModelPlan.builder().model("X").columns(List.of("id"))
                    .start(0).build().start());
        }
    }

    @Nested
    @DisplayName("不可变性 + 等值")
    class Immutability {

        @Test
        @DisplayName("columns 返回不可变副本")
        void columnsUnmodifiable() {
            BaseModelPlan p = BaseModelPlan.builder()
                    .model("X").columns(List.of("id", "name")).build();
            assertThrows(UnsupportedOperationException.class, () -> p.columns().add("x"));
        }

        @Test
        @DisplayName("值相等：相同字段的两个 BaseModelPlan 等价 + hash 一致")
        void valueEquality() {
            BaseModelPlan a = BaseModelPlan.builder()
                    .model("X").columns(List.of("id", "name")).build();
            BaseModelPlan b = BaseModelPlan.builder()
                    .model("X").columns(List.of("id", "name")).build();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("可作为 Set 元素（M6 子树去重所需）")
        void hashableForSubtreeDedup() {
            BaseModelPlan a = BaseModelPlan.builder().model("X").columns(List.of("id")).build();
            BaseModelPlan b = BaseModelPlan.builder().model("X").columns(List.of("id")).build();
            // HashSet used (not Set.of) — Set.of rejects duplicates at construction;
            // we want to verify a.equals(b) ⇒ HashSet folds them.
            assertEquals(1, new java.util.HashSet<>(java.util.Arrays.asList(a, b)).size());
        }
    }

    @Nested
    @DisplayName("base_model_plans 树遍历")
    class TreeWalk {

        @Test
        @DisplayName("叶子节点返回自身列表")
        void baseModelPlansReturnsSelfOnly() {
            BaseModelPlan p = BaseModelPlan.builder().model("X").columns(List.of("id")).build();
            assertEquals(List.of(p), p.baseModelPlans());
        }
    }

    @Nested
    @DisplayName("execute / toSql 占位")
    class ExecuteAndToSqlDeferred {

        @Test
        @DisplayName("execute() 抛 UnsupportedInM2Exception")
        void executeRaises() {
            BaseModelPlan p = BaseModelPlan.builder().model("X").columns(List.of("id")).build();
            assertThrows(UnsupportedInM2Exception.class, p::execute);
        }

        @Test
        @DisplayName("toSql() 抛 UnsupportedInM2Exception")
        void toSqlRaises() {
            BaseModelPlan p = BaseModelPlan.builder().model("X").columns(List.of("id")).build();
            assertThrows(UnsupportedInM2Exception.class, p::toSql);
        }
    }

    @Nested
    @DisplayName("QueryPlan 多态")
    class IsQueryPlan {

        @Test
        @DisplayName("BaseModelPlan 是 QueryPlan 的子类")
        void baseModelPlanIsAQueryPlan() {
            BaseModelPlan p = BaseModelPlan.builder().model("X").columns(List.of("id")).build();
            assertTrue(p instanceof QueryPlan);
        }
    }
}
