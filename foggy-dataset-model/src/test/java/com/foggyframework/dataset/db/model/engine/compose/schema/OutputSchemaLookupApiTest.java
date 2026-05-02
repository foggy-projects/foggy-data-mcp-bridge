package com.foggyframework.dataset.db.model.engine.compose.schema;

import com.foggyframework.dataset.db.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanId;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR2 · {@link OutputSchema} lookup API contract.
 *
 * <p>Splits the new lookup surface into two regimes:
 * <ul>
 *   <li>Non-ambiguous (every column has {@code isAmbiguous=false}) — works
 *       under both flag values; behaves identically to the M4 baseline.</li>
 *   <li>Ambiguous (≥2 columns share a name with {@code isAmbiguous=true}) —
 *       only allowed when {@link ComposeFeatureFlags#g10Enabled() G10 flag}
 *       is on; tests pin the flag explicitly to avoid env leakage.</li>
 * </ul>
 */
@DisplayName("G10 PR2 · OutputSchema lookup API")
class OutputSchemaLookupApiTest {

    private static QueryPlan stubPlan(String model) {
        return BaseModelPlan.builder().model(model).build();
    }

    @AfterEach
    void clearOverride() {
        ComposeFeatureFlags.overrideG10Enabled(null);
    }

    // ------------------------------------------------------------------
    // Non-ambiguous regime (legacy + G10 path identical)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("非歧义场景：双 flag 状态行为一致")
    class NonAmbiguous {

        @Test
        @DisplayName("get(name) 返回唯一列；缺失返回 null")
        void getReturnsSingleOrNull() {
            OutputSchema s = OutputSchema.of(List.of(
                    ColumnSpec.of("a", "a"),
                    ColumnSpec.of("b", "SUM(b)")
            ));
            assertEquals("a", s.get("a").name());
            assertEquals("SUM(b)", s.get("b").expression());
            assertNull(s.get("missing"));
        }

        @Test
        @DisplayName("getAll(name) 单元素 list / 缺失空 list")
        void getAllReturnsSingletonOrEmpty() {
            OutputSchema s = OutputSchema.of(List.of(
                    ColumnSpec.of("a", "a"),
                    ColumnSpec.of("b", "b")
            ));
            assertEquals(1, s.getAll("a").size());
            assertEquals("a", s.getAll("a").get(0).name());
            assertTrue(s.getAll("missing").isEmpty());
        }

        @Test
        @DisplayName("isAmbiguous 在非歧义/缺失场景返回 false")
        void isAmbiguousFalseForUniqueOrMissing() {
            OutputSchema s = OutputSchema.of(List.of(ColumnSpec.of("a", "a")));
            assertFalse(s.isAmbiguous("a"));
            assertFalse(s.isAmbiguous("missing"));
        }

        @Test
        @DisplayName("requireUnique 命中返回唯一列；缺失抛 NoSuchElementException")
        void requireUniqueHitVsMissing() {
            OutputSchema s = OutputSchema.of(List.of(ColumnSpec.of("a", "a")));
            assertEquals("a", s.requireUnique("a").name());
            assertThrows(NoSuchElementException.class, () -> s.requireUnique("missing"));
        }

        @Test
        @DisplayName("indexOf 唯一返回索引；缺失抛 NoSuchElementException")
        void indexOfUniqueOrMissing() {
            OutputSchema s = OutputSchema.of(List.of(
                    ColumnSpec.of("a", "a"),
                    ColumnSpec.of("b", "b")
            ));
            assertEquals(0, s.indexOf("a"));
            assertEquals(1, s.indexOf("b"));
            assertThrows(NoSuchElementException.class, () -> s.indexOf("missing"));
        }

        @Test
        @DisplayName("flag=true 下非歧义查询行为不变")
        void flagOnNonAmbiguousIdentical() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            OutputSchema s = OutputSchema.of(List.of(
                    ColumnSpec.of("a", "a"),
                    ColumnSpec.of("b", "SUM(b)")
            ));
            assertEquals("a", s.get("a").name());
            assertNull(s.get("missing"));
            assertFalse(s.isAmbiguous("a"));
            assertEquals(1, s.getAll("b").size());
        }
    }

    // ------------------------------------------------------------------
    // Legacy duplicate rejection (flag=false)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("flag=false：所有重复仍被拒绝（M4 兼容）")
    class LegacyDuplicateRejection {

        @Test
        @DisplayName("普通重复名拒绝（PR1 真零行为延续）")
        void plainDuplicateRejected() {
            assertThrows(IllegalArgumentException.class, () ->
                    OutputSchema.of(List.of(
                            ColumnSpec.of("name", "a"),
                            ColumnSpec.of("name", "b")
                    )));
        }

        @Test
        @DisplayName("flag=false 下即使 isAmbiguous=true 也拒绝（兜底安全网）")
        void ambiguousMarkedRejectedUnderLegacy() {
            ComposeFeatureFlags.overrideG10Enabled(false);
            ColumnSpec a = ColumnSpec.builder().name("name").expression("name")
                    .isAmbiguous(true).build();
            ColumnSpec b = ColumnSpec.builder().name("name").expression("name")
                    .isAmbiguous(true).build();
            assertThrows(IllegalArgumentException.class,
                    () -> OutputSchema.of(List.of(a, b)),
                    "flag=false 下 OutputSchema 必须忽略 isAmbiguous，按 M4 严格拒重复");
        }
    }

    // ------------------------------------------------------------------
    // G10 ambiguous regime (flag=true)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("G10 (flag=true)：歧义列共存 + 显式 lookup 语义")
    class G10AmbiguousRegime {

        private OutputSchema buildAmbiguous() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            PlanId leftPid = PlanId.of(stubPlan("OrderQM"));
            PlanId rightPid = PlanId.of(stubPlan("CustomerQM"));
            ColumnSpec leftName = ColumnSpec.builder()
                    .name("name").expression("name")
                    .sourceModel("OrderQM")
                    .planProvenance(leftPid)
                    .isAmbiguous(true).build();
            ColumnSpec rightName = ColumnSpec.builder()
                    .name("name").expression("name")
                    .sourceModel("CustomerQM")
                    .planProvenance(rightPid)
                    .isAmbiguous(true).build();
            ColumnSpec orderId = ColumnSpec.builder()
                    .name("orderId").expression("orderId")
                    .sourceModel("OrderQM")
                    .planProvenance(leftPid)
                    .build();
            return OutputSchema.of(List.of(orderId, leftName, rightName));
        }

        @Test
        @DisplayName("两列同名共存 → size=3、names 出现重复、isAmbiguous(name)=true")
        void duplicateAmbiguousAllowed() {
            OutputSchema s = buildAmbiguous();
            assertEquals(3, s.size());
            assertEquals(List.of("orderId", "name", "name"), s.names());
            assertTrue(s.isAmbiguous("name"));
            assertFalse(s.isAmbiguous("orderId"));
            assertTrue(s.contains("name"));
        }

        @Test
        @DisplayName("getAll(name) 多元素 + 顺序与构造一致")
        void getAllReturnsAllInOrder() {
            OutputSchema s = buildAmbiguous();
            List<ColumnSpec> all = s.getAll("name");
            assertEquals(2, all.size());
            assertEquals("OrderQM", all.get(0).sourceModel());
            assertEquals("CustomerQM", all.get(1).sourceModel());
            // returned list must be unmodifiable
            assertThrows(UnsupportedOperationException.class,
                    () -> all.add(ColumnSpec.of("x", "x")));
        }

        @Test
        @DisplayName("get(name) 命中歧义列 → OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP")
        void getOnAmbiguousFailsFast() {
            OutputSchema s = buildAmbiguous();
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> s.get("name"));
            assertEquals(ComposeSchemaErrorCodes.OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP,
                    ex.code());
            assertEquals("name", ex.offendingField());
            assertTrue(ex.getMessage().contains("ambiguous"),
                    "error message must mention ambiguity: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("planProvenance"),
                    "error message must list candidate plan provenance: "
                            + ex.getMessage());
        }

        @Test
        @DisplayName("requireUnique(name) 命中歧义列同样 OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP")
        void requireUniqueOnAmbiguousFailsFast() {
            OutputSchema s = buildAmbiguous();
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> s.requireUnique("name"));
            assertEquals(ComposeSchemaErrorCodes.OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP,
                    ex.code());
        }

        @Test
        @DisplayName("indexOf(name) 命中歧义列同样 OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP")
        void indexOfOnAmbiguousFailsFast() {
            OutputSchema s = buildAmbiguous();
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> s.indexOf("name"));
            assertEquals(ComposeSchemaErrorCodes.OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP,
                    ex.code());
        }

        @Test
        @DisplayName("非歧义列在歧义 schema 中仍走 fast-path")
        void nonAmbiguousColumnsStillUniqueLookup() {
            OutputSchema s = buildAmbiguous();
            assertEquals("orderId", s.get("orderId").name());
            assertEquals(0, s.indexOf("orderId"));
            assertFalse(s.isAmbiguous("orderId"));
            assertEquals(1, s.getAll("orderId").size());
        }

        @Test
        @DisplayName("flag=true 下混入未标 isAmbiguous 的同名列被拒绝")
        void mixedFlagsRejected() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            ColumnSpec markedAmbiguous = ColumnSpec.builder()
                    .name("name").expression("name")
                    .planProvenance(PlanId.of(stubPlan("L")))
                    .isAmbiguous(true).build();
            ColumnSpec plainCopy = ColumnSpec.builder()
                    .name("name").expression("name")
                    .planProvenance(PlanId.of(stubPlan("R")))
                    .isAmbiguous(false).build();
            assertThrows(IllegalArgumentException.class,
                    () -> OutputSchema.of(List.of(markedAmbiguous, plainCopy)),
                    "flag=true 下歧义列必须每个 occurrence 都 isAmbiguous=true");
        }

        @Test
        @DisplayName("flag=true 下两列 planProvenance 完全相同 → 仍拒绝（plan-tree 构造 bug）")
        void identicalPlanProvenanceRejected() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            PlanId pid = PlanId.of(stubPlan("Same"));
            ColumnSpec a = ColumnSpec.builder()
                    .name("name").expression("name")
                    .planProvenance(pid).isAmbiguous(true).build();
            ColumnSpec b = ColumnSpec.builder()
                    .name("name").expression("name")
                    .planProvenance(pid).isAmbiguous(true).build();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> OutputSchema.of(List.of(a, b)));
            assertTrue(ex.getMessage().contains("pure duplicate"),
                    "must call out plan-tree construction bug: " + ex.getMessage());
        }
    }
}
