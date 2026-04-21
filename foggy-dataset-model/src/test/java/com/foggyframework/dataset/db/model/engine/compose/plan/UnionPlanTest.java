package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 UnionPlan 不变量 — 跨仓对齐 Python test_union_plan.py。
 */
@DisplayName("M2 UnionPlan")
class UnionPlanTest {

    private BaseModelPlan base(String model) {
        return BaseModelPlan.builder()
                .model(model).columns(List.of("id", "val")).build();
    }

    @Nested
    @DisplayName("构造")
    class Construction {

        @Test
        @DisplayName("最小构造")
        void minimalValid() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            UnionPlan u = UnionPlan.builder().left(a).right(b).build();
            assertSame(a, u.left());
            assertSame(b, u.right());
            assertFalse(u.all());
        }

        @Test
        @DisplayName("all 默认 false")
        void allDefaultsFalse() {
            UnionPlan u = UnionPlan.builder().left(base("A")).right(base("B")).build();
            assertFalse(u.all());

            UnionPlan u2 = UnionPlan.builder().left(base("A")).right(base("B")).all(true).build();
            assertTrue(u2.all());
        }

        @Test
        @DisplayName("left 必须是 QueryPlan（不能 null）")
        void leftRequired() {
            assertThrows(IllegalArgumentException.class,
                    () -> UnionPlan.builder().left(null).right(base("B")).build());
        }

        @Test
        @DisplayName("right 必须是 QueryPlan（不能 null）")
        void rightRequired() {
            assertThrows(IllegalArgumentException.class,
                    () -> UnionPlan.builder().left(base("A")).right(null).build());
        }
    }

    @Nested
    @DisplayName("链式糖")
    class Chain {
        @Test
        @DisplayName("plan.union(other) 返回 UnionPlan")
        void planUnionReturnsUnionPlan() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            UnionPlan u = a.union(b);
            assertInstanceOf(UnionPlan.class, u);
            assertSame(a, u.left());
            assertSame(b, u.right());
            assertFalse(u.all());
        }

        @Test
        @DisplayName("plan.union(other, all=true) 生成 UNION ALL")
        void planUnionAllTrue() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            UnionPlan u = a.union(b, true);
            assertTrue(u.all());
        }

        @Test
        @DisplayName("plan.union(null) 被拒绝")
        void planUnionRejectsNullRight() {
            BaseModelPlan a = base("A");
            assertThrows(IllegalArgumentException.class, () -> a.union(null));
        }
    }

    @Nested
    @DisplayName("base_model_plans 树遍历")
    class TreeWalk {

        @Test
        @DisplayName("左-右 preorder")
        void preorderLeftThenRight() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            UnionPlan u = a.union(b);
            assertEquals(List.of(a, b), u.baseModelPlans());
        }

        @Test
        @DisplayName("三层 union 链保持顺序")
        void threeLevelChainPreservesOrder() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            BaseModelPlan c = base("C");
            UnionPlan u = a.union(b).union(c);
            assertEquals(List.of(a, b, c), u.baseModelPlans());
        }
    }

    @Nested
    @DisplayName("值相等 + hashCode")
    class Equality {
        @Test
        @DisplayName("结构相等的两个 UnionPlan 等价")
        void structuralEquality() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            UnionPlan u1 = a.union(b, true);
            UnionPlan u2 = a.union(b, true);
            assertEquals(u1, u2);
            assertEquals(u1.hashCode(), u2.hashCode());
        }
    }

    @Nested
    @DisplayName("QueryPlan 多态")
    class IsInstance {
        @Test
        @DisplayName("UnionPlan 是 QueryPlan 的子类")
        void unionIsQueryPlan() {
            UnionPlan u = UnionPlan.builder().left(base("A")).right(base("B")).build();
            assertTrue(u instanceof QueryPlan);
        }
    }
}
