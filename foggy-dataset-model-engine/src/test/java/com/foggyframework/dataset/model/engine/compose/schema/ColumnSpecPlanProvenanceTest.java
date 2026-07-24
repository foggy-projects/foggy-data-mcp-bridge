package com.foggyframework.dataset.model.engine.compose.schema;

import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.PlanId;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR1 · {@link ColumnSpec} new fields {@code planProvenance} +
 * {@code isAmbiguous}.
 *
 * <p>Verifies that the new fields are exposed via builder + accessor, and
 * that PR1's <b>真零行为变化</b> guarantee holds — the new fields do
 * <em>not</em> participate in {@link ColumnSpec#equals} or
 * {@link ColumnSpec#hashCode}, so existing equality-based tests continue
 * to pass unchanged.</p>
 */
@DisplayName("G10 PR1 · ColumnSpec planProvenance + isAmbiguous")
class ColumnSpecPlanProvenanceTest {

    private static QueryPlan stubPlan(String model) {
        return BaseModelPlan.builder().model(model).build();
    }

    @Nested
    @DisplayName("默认值（PR1 真零行为）")
    class Defaults {

        @Test
        @DisplayName("旧式 of(name, expression) → planProvenance=null + isAmbiguous=false")
        void shorthandConstructorDefaults() {
            ColumnSpec c = ColumnSpec.of("orderId", "orderId");
            assertNull(c.planProvenance(), "default planProvenance must be null");
            assertFalse(c.isAmbiguous(), "default isAmbiguous must be false");
        }

        @Test
        @DisplayName("builder 不显式设置 → 同样的默认")
        void builderDefaultsWhenUnset() {
            ColumnSpec c = ColumnSpec.builder()
                    .name("x")
                    .expression("x")
                    .build();
            assertNull(c.planProvenance());
            assertFalse(c.isAmbiguous());
        }
    }

    @Nested
    @DisplayName("Builder 接受新字段")
    class BuilderAcceptsNewFields {

        @Test
        @DisplayName("planProvenance 可设置 + accessor 返回相同 PlanId")
        void planProvenanceRoundtrip() {
            PlanId pid = PlanId.of(stubPlan("CustomerQM"));
            ColumnSpec c = ColumnSpec.builder()
                    .name("name")
                    .expression("name")
                    .planProvenance(pid)
                    .build();
            assertSame(pid, c.planProvenance(),
                    "planProvenance accessor must return the exact PlanId set on builder");
        }

        @Test
        @DisplayName("isAmbiguous 可设置 true + accessor 返回 true")
        void isAmbiguousRoundtrip() {
            ColumnSpec c = ColumnSpec.builder()
                    .name("name")
                    .expression("name")
                    .isAmbiguous(true)
                    .build();
            assertTrue(c.isAmbiguous());
        }

        @Test
        @DisplayName("两字段并存 + 与现有字段并行设置")
        void coexistWithExistingFields() {
            PlanId pid = PlanId.of(stubPlan("OrderQM"));
            ColumnSpec c = ColumnSpec.builder()
                    .name("orderName")
                    .expression("name AS orderName")
                    .sourceModel("OrderQM")
                    .hasExplicitAlias(true)
                    .planProvenance(pid)
                    .isAmbiguous(true)
                    .build();
            assertEquals("orderName", c.name());
            assertEquals("OrderQM", c.sourceModel());
            assertTrue(c.hasExplicitAlias());
            assertSame(pid, c.planProvenance());
            assertTrue(c.isAmbiguous());
        }
    }

    @Nested
    @DisplayName("PR1 真零行为：新字段不参与 equals / hashCode")
    class EqualityUnchanged {

        @Test
        @DisplayName("两个 ColumnSpec 仅 planProvenance 不同仍 equals true（PR1 contract）")
        void differentProvenanceStillEqual() {
            PlanId p1 = PlanId.of(stubPlan("M1"));
            PlanId p2 = PlanId.of(stubPlan("M2"));
            ColumnSpec a = ColumnSpec.builder()
                    .name("x").expression("x").planProvenance(p1).build();
            ColumnSpec b = ColumnSpec.builder()
                    .name("x").expression("x").planProvenance(p2).build();
            assertEquals(a, b,
                    "PR1 真零行为: planProvenance not in equals — existing tests must not break");
            assertEquals(a.hashCode(), b.hashCode(),
                    "PR1 真零行为: hashCode also unaffected by planProvenance");
        }

        @Test
        @DisplayName("两个 ColumnSpec 仅 isAmbiguous 不同仍 equals true（PR1 contract）")
        void differentAmbiguityStillEqual() {
            ColumnSpec a = ColumnSpec.builder()
                    .name("x").expression("x").isAmbiguous(false).build();
            ColumnSpec b = ColumnSpec.builder()
                    .name("x").expression("x").isAmbiguous(true).build();
            assertEquals(a, b,
                    "PR1 真零行为: isAmbiguous not in equals");
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("现有字段差异仍正确触发 equals=false")
        void existingFieldEqualityPreserved() {
            ColumnSpec a = ColumnSpec.builder()
                    .name("x").expression("x").sourceModel("M1").build();
            ColumnSpec b = ColumnSpec.builder()
                    .name("x").expression("x").sourceModel("M2").build();
            assertNotEquals(a, b,
                    "Existing equality contract (sourceModel) must remain enforced");
        }
    }

    @Nested
    @DisplayName("toString 包含新字段（用于调试）")
    class ToStringIncludesNewFields {

        @Test
        @DisplayName("toString 含 planProvenance + isAmbiguous")
        void includesNewFields() {
            ColumnSpec c = ColumnSpec.builder()
                    .name("x").expression("x")
                    .planProvenance(PlanId.of(stubPlan("M")))
                    .isAmbiguous(true)
                    .build();
            String s = c.toString();
            assertTrue(s.contains("planProvenance="),
                    "toString must include planProvenance for debug visibility: " + s);
            assertTrue(s.contains("isAmbiguous=true"),
                    "toString must include isAmbiguous: " + s);
        }
    }
}
