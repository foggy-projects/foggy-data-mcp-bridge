package com.foggyframework.dataset.db.model.engine.compose.security;

import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR4 · {@link PlanFieldAccessContext} contract.
 */
@DisplayName("G10 PR4 · PlanFieldAccessContext contract")
class PlanFieldAccessContextTest {

    private static QueryPlan stubPlan(String model) {
        return BaseModelPlan.builder().model(model).build();
    }

    @Nested
    @DisplayName("registry behaviour")
    class RegistryBehaviour {

        @Test
        @DisplayName("空 context: 任何 plan 都不在 registry，resolveFieldAccess 返回 null")
        void emptyContextReturnsNullForAnyPlan() {
            PlanFieldAccessContext ctx = PlanFieldAccessContext.empty();
            QueryPlan p = stubPlan("X");
            assertFalse(ctx.containsPlan(p));
            assertNull(ctx.resolveFieldAccess(p));
            assertNull(ctx.bindingOf(p));
            assertEquals(0, ctx.size());
            assertTrue(ctx.isEmpty());
        }

        @Test
        @DisplayName("绑定有 fieldAccess 列表的 plan → resolveFieldAccess 返回该集合")
        void plannedFieldAccessReturned() {
            QueryPlan p = stubPlan("X");
            ModelBinding binding = ModelBinding.builder()
                    .fieldAccess(List.of("orderId", "customerId", "amount"))
                    .build();
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(p, binding).build();

            assertTrue(ctx.containsPlan(p));
            Set<String> fa = ctx.resolveFieldAccess(p);
            assertNotNull(fa);
            assertEquals(Set.of("orderId", "customerId", "amount"), fa);
            assertSame(binding, ctx.bindingOf(p));
        }

        @Test
        @DisplayName("绑定无 fieldAccess (null) 的 plan → containsPlan=true 但 resolveFieldAccess=null")
        void plannedWithoutFieldAccessReturnsNull() {
            QueryPlan p = stubPlan("X");
            ModelBinding binding = ModelBinding.builder().build();
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(p, binding).build();

            assertTrue(ctx.containsPlan(p),
                    "containsPlan 区分'未注册'与'已注册无 fieldAccess'");
            assertNull(ctx.resolveFieldAccess(p),
                    "无 fieldAccess 列表 → resolveFieldAccess 返回 null（caller 视为不限制）");
            assertSame(binding, ctx.bindingOf(p));
        }

        @Test
        @DisplayName("两个 plan 实例同 model 名 → 按 identity 区分（不混淆）")
        void identityKeyedNotEqualityKeyed() {
            QueryPlan p1 = stubPlan("OrderQM");
            QueryPlan p2 = stubPlan("OrderQM");
            assertNotSame(p1, p2);

            ModelBinding b1 = ModelBinding.builder().fieldAccess(List.of("a")).build();
            ModelBinding b2 = ModelBinding.builder().fieldAccess(List.of("b")).build();
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(p1, b1).bind(p2, b2).build();

            assertEquals(2, ctx.size());
            assertEquals(Set.of("a"), ctx.resolveFieldAccess(p1));
            assertEquals(Set.of("b"), ctx.resolveFieldAccess(p2));
        }

        @Test
        @DisplayName("resolveFieldAccess(null) 返回 null（不抛）")
        void nullPlanLookupReturnsNull() {
            PlanFieldAccessContext ctx = PlanFieldAccessContext.empty();
            assertNull(ctx.resolveFieldAccess(null));
            assertFalse(ctx.containsPlan(null));
            assertNull(ctx.bindingOf(null));
        }

        @Test
        @DisplayName("bind(null, _) / bind(_, null) → NPE")
        void nullBindRejected() {
            PlanFieldAccessContext.Builder b = PlanFieldAccessContext.builder();
            assertThrows(NullPointerException.class, () -> b.bind(null,
                    ModelBinding.builder().build()));
            assertThrows(NullPointerException.class, () -> b.bind(stubPlan("X"), null));
        }

        @Test
        @DisplayName("返回的 fieldAccess Set 不可变")
        void resolvedSetIsImmutable() {
            QueryPlan p = stubPlan("X");
            ModelBinding binding = ModelBinding.builder()
                    .fieldAccess(List.of("a", "b")).build();
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(p, binding).build();

            Set<String> fa = ctx.resolveFieldAccess(p);
            assertThrows(UnsupportedOperationException.class, () -> fa.add("c"));
        }
    }
}
