package com.foggyframework.dataset.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR1 · {@link PlanId} identity contract.
 *
 * <p>Verifies the strict equality contract spelled out in G10 spec v2 §4.3:
 * {@code equals} compares by referent identity, {@code hashCode} returns the
 * captured {@code identityHashCode}, and {@code resolve()} surfaces GC.</p>
 */
@DisplayName("G10 PR1 · PlanId identity contract")
class PlanIdTest {

    /** Tiny stub plan — we only need an identity-hashable, non-singleton object. */
    private static QueryPlan stubPlan() {
        return com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan.builder()
                .model("Model_" + System.nanoTime())
                .build();
    }

    @Nested
    @DisplayName("equals: 严格按 referent identity")
    class Equality {

        @Test
        @DisplayName("同一 plan 引用 → equals true")
        void samePlanReferent() {
            QueryPlan plan = stubPlan();
            PlanId id1 = PlanId.of(plan);
            PlanId id2 = PlanId.of(plan);
            assertEquals(id1, id2, "PlanId of the same plan referent must equal");
            assertEquals(id1.hashCode(), id2.hashCode(),
                    "Same referent must produce same identityHash");
        }

        @Test
        @DisplayName("不同 plan 引用 → equals false")
        void differentPlanReferents() {
            PlanId id1 = PlanId.of(stubPlan());
            PlanId id2 = PlanId.of(stubPlan());
            assertNotEquals(id1, id2,
                    "Two PlanId backed by distinct plan instances must not equal");
        }

        @Test
        @DisplayName("同模型不同实例 → equals false（按对象身份，非 model 名）")
        void sameModelDifferentInstancesNotEqual() {
            // G10 spec §5.1: 等价判定按对象身份，不按 model 名称。
            QueryPlan a1 = com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan.builder()
                    .model("X").build();
            QueryPlan a2 = com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan.builder()
                    .model("X").build();
            assertNotSame(a1, a2);
            assertNotEquals(PlanId.of(a1), PlanId.of(a2),
                    "Same model name in different instances must yield distinct PlanIds");
        }

        @Test
        @DisplayName("equals 自反 / 对称")
        void reflexiveAndSymmetric() {
            QueryPlan plan = stubPlan();
            PlanId id1 = PlanId.of(plan);
            PlanId id2 = PlanId.of(plan);
            // 自反
            assertEquals(id1, id1);
            // 对称
            assertEquals(id1, id2);
            assertEquals(id2, id1);
        }

        @Test
        @DisplayName("equals(null) / equals(Object) → false")
        void notEqualToNullOrUnrelated() {
            PlanId id = PlanId.of(stubPlan());
            assertNotEquals(null, id);
            assertNotEquals("not a PlanId", id);
            assertNotEquals(id, "not a PlanId");
        }

        @Test
        @DisplayName("of(null) → NPE")
        void ofNullThrows() {
            assertThrows(NullPointerException.class, () -> PlanId.of(null));
        }
    }

    @Nested
    @DisplayName("hashCode: 仅用 identityHash，不依赖 referent 状态")
    class HashContract {

        @Test
        @DisplayName("hashCode 在 referent 存活期间稳定")
        void hashCodeStable() {
            QueryPlan plan = stubPlan();
            PlanId id = PlanId.of(plan);
            int h1 = id.hashCode();
            int h2 = id.hashCode();
            assertEquals(h1, h2, "hashCode must be stable across calls");
            assertEquals(System.identityHashCode(plan), h1,
                    "hashCode should be the cached System.identityHashCode");
        }

        @Test
        @DisplayName("hash 在集合中按 referent 区分（即使 hash 一致仍能正确 equals）")
        void worksAsHashKey() {
            QueryPlan p1 = stubPlan();
            QueryPlan p2 = stubPlan();
            Set<PlanId> set = new HashSet<>();
            set.add(PlanId.of(p1));
            set.add(PlanId.of(p2));
            assertEquals(2, set.size(), "Distinct plans must remain distinct in a set");
            assertTrue(set.contains(PlanId.of(p1)));
            assertTrue(set.contains(PlanId.of(p2)));
        }

        @Test
        @DisplayName("Map<PlanId, X> 用 PlanId 做 key 行为正确")
        void worksAsMapKey() {
            QueryPlan p1 = stubPlan();
            QueryPlan p2 = stubPlan();
            Map<PlanId, String> map = new HashMap<>();
            map.put(PlanId.of(p1), "first");
            map.put(PlanId.of(p2), "second");
            assertEquals("first", map.get(PlanId.of(p1)));
            assertEquals("second", map.get(PlanId.of(p2)));
            assertNull(map.get(PlanId.of(stubPlan())),
                    "An unrelated plan must miss the map");
        }
    }

    @Nested
    @DisplayName("resolve: GC 后返回 null")
    class GcBehavior {

        @Test
        @DisplayName("referent 存活时 resolve() == 原 plan")
        void resolveReturnsLivingReferent() {
            QueryPlan plan = stubPlan();
            PlanId id = PlanId.of(plan);
            assertSame(plan, id.resolve(),
                    "resolve() must return the same plan object while alive");
        }

        @Test
        @DisplayName("强引用范围内 resolve 仍可用（不会因短期 GC 失效）")
        void weakReferenceDoesNotPrematurelyExpire() {
            // 即使我们触发 GC，只要本地变量 plan 仍然 strongly-referenced，
            // PlanId.resolve() 必须仍返回该对象。
            QueryPlan plan = stubPlan();
            PlanId id = PlanId.of(plan);
            System.gc();
            Thread.yield();
            assertSame(plan, id.resolve(),
                    "WeakReference must not collect a strongly-referenced plan");
        }

        @Test
        @DisplayName("toString 含 hash + referent 类名（GC 后显示 <gc>）")
        void toStringShape() {
            PlanId id = PlanId.of(stubPlan());
            String s = id.toString();
            assertTrue(s.startsWith("PlanId{hash="), "toString prefix: " + s);
            assertTrue(s.contains("referent="), "toString includes referent: " + s);
        }
    }

    @Nested
    @DisplayName("transient 语义文档化（非自动测试）")
    class TransientSemanticsDoc {

        @Test
        @DisplayName("PlanId 不实现 Serializable")
        void notSerializable() {
            assertFalse(java.io.Serializable.class.isAssignableFrom(PlanId.class),
                    "PlanId must not be Serializable — transient identity key only");
        }

        @Test
        @DisplayName("PlanId 不暴露原始 referent 字段以防被持久化")
        void noPublicRefField() {
            for (var f : PlanId.class.getDeclaredFields()) {
                assertTrue(java.lang.reflect.Modifier.isPrivate(f.getModifiers())
                                && java.lang.reflect.Modifier.isFinal(f.getModifiers()),
                        "PlanId fields must be private final, found: " + f);
            }
        }

        @Test
        @DisplayName("WeakReference 字段类型契约")
        void weakReferenceFieldType() throws Exception {
            var refField = PlanId.class.getDeclaredField("ref");
            assertEquals(WeakReference.class, refField.getType(),
                    "ref field must be WeakReference to avoid heap pinning");
        }
    }
}
