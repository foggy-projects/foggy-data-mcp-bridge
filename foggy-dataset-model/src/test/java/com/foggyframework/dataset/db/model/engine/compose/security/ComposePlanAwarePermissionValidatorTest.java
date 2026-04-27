package com.foggyframework.dataset.db.model.engine.compose.security;

import com.foggyframework.dataset.db.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanId;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR4 · {@link ComposePlanAwarePermissionValidator} contract.
 *
 * <p>Tests pin the G10 flag explicitly via
 * {@link ComposeFeatureFlags#overrideG10Enabled(Boolean)} so they don't
 * depend on the production default; the validator's logic itself is
 * flag-independent (the gate lives at the call site in
 * {@code ComposePlanner.compileToComposedSql}).</p>
 */
@DisplayName("G10 PR4 · ComposePlanAwarePermissionValidator")
class ComposePlanAwarePermissionValidatorTest {

    @AfterEach
    void clearOverride() {
        ComposeFeatureFlags.overrideG10Enabled(null);
    }

    private static QueryPlan basePlan(String model, List<String> columns) {
        return BaseModelPlan.builder().model(model).columns(List.copyOf(columns)).build();
    }

    private static ColumnSpec specWithProvenance(String name, QueryPlan plan, boolean ambiguous) {
        return ColumnSpec.builder()
                .name(name).expression(name)
                .planProvenance(plan == null ? null : PlanId.of(plan))
                .isAmbiguous(ambiguous)
                .build();
    }

    // ------------------------------------------------------------------
    // F5 plan-qualified
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("F5 plan-qualified routing")
    class PlanQualified {

        @Test
        @DisplayName("plan 在 context + field 在白名单 → 通过")
        void allowedInWhitelistPasses() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId", "customerId"));
            PlanColumnRef ref = new PlanColumnRef(order, "orderId");
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order).columns(List.of(ref)).build();

            ModelBinding binding = ModelBinding.builder()
                    .fieldAccess(List.of("orderId", "customerId")).build();
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(order, binding).build();

            // Schema for the derived layer with provenance set
            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("orderId", order, false)));
            assertDoesNotThrow(() -> ComposePlanAwarePermissionValidator.validate(
                    derived, schema, ctx));
        }

        @Test
        @DisplayName("plan 在谱系内但 context 未绑定 → COLUMN_PLAN_NOT_BOUND (PR4 fail-closed)")
        void unknownPlanFailsClosed() {
            // G5 spec §5.1 build-time visibility check rejects plans not in
            // the source's lineage. To exercise PR4's COLUMN_PLAN_NOT_BOUND
            // (a separate, permission-validate-stage code), the plan must be
            // VISIBLE (passes build) but missing from the binding context.
            // Here `stranger` is reachable via the join's right branch, so
            // visibility passes; ctx then deliberately omits the binding.
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId"));
            QueryPlan stranger = basePlan("StrangerQM", List.of("x"));
            JoinPlan joined = JoinPlan.builder()
                    .left(order).right(stranger).type(JoinType.INNER)
                    .on(List.of(JoinOn.of("orderId", "=", "x"))).build();

            PlanColumnRef ref = new PlanColumnRef(stranger, "x");
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(joined).columns(List.of(ref)).build();

            // Bind only `order`; deliberately omit `stranger` to trigger
            // PR4's fail-closed COLUMN_PLAN_NOT_BOUND.
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(order, ModelBinding.builder().build()).build();
            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("x", stranger, false)));

            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(derived, schema, ctx));
            assertEquals(ComposeSchemaErrorCodes.COLUMN_PLAN_NOT_BOUND, ex.code());
            assertEquals(ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE, ex.phase());
            assertEquals("x", ex.offendingField());
        }

        @Test
        @DisplayName("plan 在 context + field 不在白名单 → FIELD_ACCESS_DENIED")
        void deniedByWhitelist() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId", "secret"));
            PlanColumnRef ref = new PlanColumnRef(order, "secret");
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order).columns(List.of(ref)).build();

            ModelBinding binding = ModelBinding.builder()
                    .fieldAccess(List.of("orderId")).build();   // 'secret' excluded
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(order, binding).build();

            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("secret", order, false)));
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(derived, schema, ctx));
            assertEquals(ComposeSchemaErrorCodes.FIELD_ACCESS_DENIED, ex.code());
            assertEquals(ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE, ex.phase());
            assertEquals("secret", ex.offendingField());
        }

        @Test
        @DisplayName("plan 在 context 但 binding 无 fieldAccess → 通过（无白名单=不限制）")
        void noWhitelistMeansUnrestricted() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId"));
            PlanColumnRef ref = new PlanColumnRef(order, "orderId");
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order).columns(List.of(ref)).build();

            // binding has no fieldAccess list → caller treats as unrestricted
            ModelBinding binding = ModelBinding.builder().build();
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(order, binding).build();

            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("orderId", order, false)));
            assertDoesNotThrow(() -> ComposePlanAwarePermissionValidator.validate(
                    derived, schema, ctx));
        }

        @Test
        @DisplayName("plan-qualified 列名带维度后缀 → strip 后白名单匹配")
        void dimensionSuffixStripped() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("salesDate"));
            // 列名带 $id 后缀（典型时间维度引用）
            PlanColumnRef ref = new PlanColumnRef(order, "salesDate$id");
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order).columns(List.of(ref)).build();

            // 白名单只列了基础字段名，不含 $id 后缀
            ModelBinding binding = ModelBinding.builder()
                    .fieldAccess(List.of("salesDate")).build();
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(order, binding).build();

            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("salesDate$id", order, false)));
            assertDoesNotThrow(() -> ComposePlanAwarePermissionValidator.validate(
                    derived, schema, ctx),
                    "strip $id 后基础字段在白名单中 → 通过");
        }
    }

    // ------------------------------------------------------------------
    // Bare-field resolution
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("bare field resolution (§6.4)")
    class BareField {

        @Test
        @DisplayName("schema 不存在 → COLUMN_FIELD_NOT_FOUND")
        void unknownFieldRejected() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId"));
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order).columns(List.of("totallyMissing")).build();

            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("orderId", order, false)));
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(order, ModelBinding.builder().build()).build();

            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(derived, schema, ctx));
            assertEquals(ComposeSchemaErrorCodes.COLUMN_FIELD_NOT_FOUND, ex.code());
            assertEquals("totallyMissing", ex.offendingField());
        }

        @Test
        @DisplayName("schema 歧义 → JOIN_AMBIGUOUS_COLUMN（不因权限混合状态影响）")
        void ambiguousFieldRejected() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan customers = basePlan("CustomerQM", List.of("name"));
            QueryPlan orders = basePlan("OrderQM", List.of("name"));
            DerivedQueryPlan wrapper = DerivedQueryPlan.builder()
                    .source(customers).columns(List.of("name")).build();

            // schema with both sides marked ambiguous (mimics PR2 deriveJoin output)
            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("name", customers, true),
                    specWithProvenance("name", orders, true)));
            // Both plans bound, both whitelist 'name' — ambiguity must still
            // trump permission state.
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(customers, ModelBinding.builder().fieldAccess(List.of("name")).build())
                    .bind(orders, ModelBinding.builder().fieldAccess(List.of("name")).build())
                    .build();

            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(wrapper, schema, ctx));
            assertEquals(ComposeSchemaErrorCodes.JOIN_AMBIGUOUS_COLUMN, ex.code());
            assertTrue(ex.getMessage().contains("plan-qualified"),
                    "error message must hint F5 disambiguation: " + ex.getMessage());
        }

        @Test
        @DisplayName("schema 唯一 + provenance plan 在 context → 走该 plan 的白名单")
        void uniqueResolvedRoutesViaProvenance() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId"));
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order).columns(List.of("orderId")).build();

            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("orderId", order, false)));

            // Whitelist includes orderId — passes.
            PlanFieldAccessContext allow = PlanFieldAccessContext.builder()
                    .bind(order, ModelBinding.builder().fieldAccess(List.of("orderId")).build())
                    .build();
            assertDoesNotThrow(() -> ComposePlanAwarePermissionValidator.validate(
                    derived, schema, allow));

            // Whitelist excludes orderId — denies.
            PlanFieldAccessContext deny = PlanFieldAccessContext.builder()
                    .bind(order, ModelBinding.builder().fieldAccess(List.of("other")).build())
                    .build();
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(derived, schema, deny));
            assertEquals(ComposeSchemaErrorCodes.FIELD_ACCESS_DENIED, ex.code());
        }

        @Test
        @DisplayName("schema 唯一但无 provenance（单 base 场景）→ 跳过 plan-aware 校验")
        void uniqueWithoutProvenanceDefersToLegacy() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId"));
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order).columns(List.of("orderId")).build();

            // No provenance on the spec — legacy single-base case.
            OutputSchema schema = OutputSchema.of(List.of(
                    ColumnSpec.of("orderId", "orderId")));
            // Even an empty context should pass; the legacy
            // FieldAccessPermissionStep at @Order(-25) handles enforcement.
            PlanFieldAccessContext ctx = PlanFieldAccessContext.empty();
            assertDoesNotThrow(() -> ComposePlanAwarePermissionValidator.validate(
                    derived, schema, ctx));
        }

        @Test
        @DisplayName("alias 形式 'expr AS aliasName' → 用 alias 做 schema 查找")
        void aliasFormResolvedByAlias() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan order = basePlan("OrderQM", List.of("orderId", "amount"));
            DerivedQueryPlan derived = DerivedQueryPlan.builder()
                    .source(order)
                    .columns(List.of("SUM(amount) AS total"))
                    .build();

            // schema names 'total' (the alias)
            OutputSchema schema = OutputSchema.of(List.of(
                    specWithProvenance("total", order, false)));
            PlanFieldAccessContext ctx = PlanFieldAccessContext.builder()
                    .bind(order, ModelBinding.builder().fieldAccess(List.of("total")).build())
                    .build();
            assertDoesNotThrow(() -> ComposePlanAwarePermissionValidator.validate(
                    derived, schema, ctx));
        }
    }

    // ------------------------------------------------------------------
    // Argument-validation guards
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("argument guards")
    class ArgumentGuards {

        @Test
        @DisplayName("validate(null, _, _) → IllegalArgumentException")
        void nullPlanRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(
                            null, OutputSchema.empty(), PlanFieldAccessContext.empty()));
        }

        @Test
        @DisplayName("validate(_, null, _) → IllegalArgumentException")
        void nullSchemaRejected() {
            QueryPlan p = basePlan("X", List.of("a"));
            assertThrows(IllegalArgumentException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(
                            p, null, PlanFieldAccessContext.empty()));
        }

        @Test
        @DisplayName("validate(_, _, null) → IllegalArgumentException")
        void nullContextRejected() {
            QueryPlan p = basePlan("X", List.of("a"));
            assertThrows(IllegalArgumentException.class,
                    () -> ComposePlanAwarePermissionValidator.validate(
                            p, OutputSchema.empty(), null));
        }
    }
}
