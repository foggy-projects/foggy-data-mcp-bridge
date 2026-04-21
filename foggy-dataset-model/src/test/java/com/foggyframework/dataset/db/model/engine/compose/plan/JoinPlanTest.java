package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 JoinPlan + JoinOn 不变量 — 跨仓对齐 Python test_join_plan.py。
 */
@DisplayName("M2 JoinPlan & JoinOn")
class JoinPlanTest {

    private BaseModelPlan base(String model) {
        return BaseModelPlan.builder()
                .model(model).columns(List.of("id", "partnerId")).build();
    }

    @Nested
    @DisplayName("JoinOn")
    class JoinOnTests {

        @Test
        @DisplayName("最小构造")
        void minimalValid() {
            JoinOn j = JoinOn.of("partnerId", "=", "partnerId");
            assertEquals("partnerId", j.left());
            assertEquals("=", j.op());
            assertEquals("partnerId", j.right());
        }

        @Test
        @DisplayName("left/right 必填非空")
        void leftRightRequiredNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> JoinOn.of("", "=", "x"));
            assertThrows(IllegalArgumentException.class,
                    () -> JoinOn.of("x", "=", ""));
        }

        @Test
        @DisplayName("op 白名单 {=, !=, <, >, <=, >=}")
        void opWhitelist() {
            for (String op : List.of("=", "!=", "<", ">", "<=", ">=")) {
                assertEquals(op, JoinOn.of("a", op, "b").op());
            }
            for (String bad : List.of("in", "IN", "between", "like", "is null", "")) {
                assertThrows(IllegalArgumentException.class,
                        () -> JoinOn.of("a", bad, "b"),
                        "op '" + bad + "' 应被拒绝");
            }
        }

        @Test
        @DisplayName("ALLOWED_OPS 暴露 6 个成员")
        void allowedOpsExposesSix() {
            assertEquals(6, JoinOn.ALLOWED_OPS.size());
        }

        @Test
        @DisplayName("fromMap 正常转换")
        void fromMapSuccess() {
            JoinOn j = JoinOn.fromMap(Map.of("left", "x", "op", "=", "right", "y"));
            assertEquals("x", j.left());
            assertEquals("y", j.right());
        }

        @Test
        @DisplayName("fromMap 缺少 key 被拒绝")
        void fromMapMissingKeyRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> JoinOn.fromMap(Map.of("left", "x", "op", "=")));
        }

        @Test
        @DisplayName("值相等 + hash 一致")
        void valueEquality() {
            JoinOn a = JoinOn.of("x", "=", "y");
            JoinOn b = JoinOn.of("x", "=", "y");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    @Nested
    @DisplayName("JoinPlan 构造")
    class Construction {

        @Test
        @DisplayName("最小构造")
        void minimalValid() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            JoinPlan jp = JoinPlan.builder()
                    .left(a).right(b).type(JoinType.LEFT)
                    .on(List.of(JoinOn.of("partnerId", "=", "partnerId")))
                    .build();
            assertSame(a, jp.left());
            assertSame(b, jp.right());
            assertEquals(JoinType.LEFT, jp.type());
            assertEquals(1, jp.on().size());
        }

        @Test
        @DisplayName("type 白名单（enum 直接 + 字符串归一）")
        void typeWhitelist() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            List<JoinOn> on = List.of(JoinOn.of("id", "=", "id"));
            for (JoinType t : JoinType.values()) {
                JoinPlan jp = JoinPlan.builder().left(a).right(b).type(t).on(on).build();
                assertEquals(t, jp.type());
            }
            // String overload normalises
            for (String raw : List.of("inner", "LEFT", " Right ", "FULL")) {
                JoinPlan jp = JoinPlan.builder().left(a).right(b).type(raw).on(on).build();
                assertNotNull(jp.type());
            }
            // Unknown type rejected
            assertThrows(IllegalArgumentException.class,
                    () -> JoinPlan.builder().left(a).right(b).type("cross").on(on).build());
        }

        @Test
        @DisplayName("on 必填非空")
        void onMustBeNonEmpty() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            assertThrows(IllegalArgumentException.class,
                    () -> JoinPlan.builder().left(a).right(b).type(JoinType.LEFT)
                            .on(List.of()).build());
            assertThrows(IllegalArgumentException.class,
                    () -> JoinPlan.builder().left(a).right(b).type(JoinType.LEFT)
                            .on(null).build());
        }

        @Test
        @DisplayName("on 条目不能 null")
        void onEntryNotNull() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            List<JoinOn> badList = new java.util.ArrayList<>();
            badList.add(null);
            assertThrows(IllegalArgumentException.class,
                    () -> JoinPlan.builder().left(a).right(b).type(JoinType.LEFT)
                            .on(badList).build());
        }

        @Test
        @DisplayName("type 不能 null")
        void typeNotNull() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            assertThrows(IllegalArgumentException.class,
                    () -> JoinPlan.builder().left(a).right(b).type((JoinType) null)
                            .on(List.of(JoinOn.of("id", "=", "id"))).build());
        }
    }

    @Nested
    @DisplayName("plan.join(...) 链式糖")
    class Chain {

        @Test
        @DisplayName("JoinOn 列表入参")
        void joinAcceptsJoinOnList() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            JoinPlan jp = a.join(b, "left",
                    List.of(JoinOn.of("partnerId", "=", "partnerId")));
            assertInstanceOf(JoinPlan.class, jp);
            assertEquals(JoinType.LEFT, jp.type());
        }

        @Test
        @DisplayName("Map 入参会被 coerce 为 JoinOn")
        void joinCoercesMapEntries() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            JoinPlan jp = a.join(b, JoinType.INNER,
                    List.of(Map.of("left", "partnerId", "op", "=", "right", "partnerId")));
            assertEquals(JoinOn.of("partnerId", "=", "partnerId"), jp.on().get(0));
        }

        @Test
        @DisplayName("type 大小写不敏感（字符串入参）")
        void joinCaseInsensitiveType() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            JoinPlan jp = a.join(b, "LEFT",
                    List.of(JoinOn.of("id", "=", "id")));
            assertEquals(JoinType.LEFT, jp.type());
        }

        @Test
        @DisplayName("空 on 被拒绝")
        void joinRejectsEmptyOn() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            assertThrows(IllegalArgumentException.class,
                    () -> a.join(b, "left", List.of()));
        }

        @Test
        @DisplayName("null right 被拒绝")
        void joinRejectsNullRight() {
            BaseModelPlan a = base("A");
            assertThrows(IllegalArgumentException.class,
                    () -> a.join(null, "left", List.of(JoinOn.of("id", "=", "id"))));
        }

        @Test
        @DisplayName("Map 缺 key 被包装为 IllegalArgumentException")
        void joinRejectsBadMapMissingKey() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            assertThrows(IllegalArgumentException.class,
                    () -> a.join(b, "left",
                            List.of(Map.of("left", "id", "op", "="))));
        }

        @Test
        @DisplayName("非 JoinOn/Map 入参被拒绝")
        void joinRejectsWrongType() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            assertThrows(IllegalArgumentException.class,
                    () -> a.join(b, "left", List.of("not a JoinOn")));
        }
    }

    @Nested
    @DisplayName("base_model_plans 树遍历")
    class TreeWalk {
        @Test
        @DisplayName("左-右 preorder")
        void leftThenRight() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            JoinPlan jp = a.join(b, "left", List.of(JoinOn.of("id", "=", "id")));
            assertEquals(List.of(a, b), jp.baseModelPlans());
        }
    }

    @Nested
    @DisplayName("QueryPlan 多态")
    class IsInstance {
        @Test
        @DisplayName("JoinPlan 是 QueryPlan 的子类")
        void joinIsQueryPlan() {
            BaseModelPlan a = base("A");
            BaseModelPlan b = base("B");
            JoinPlan jp = a.join(b, "left", List.of(JoinOn.of("id", "=", "id")));
            assertTrue(jp instanceof QueryPlan);
        }
    }
}
