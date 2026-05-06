package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code PerBaseCompiler} · 单 BaseModelPlan → CteUnit 路径。
 *
 * <p>镜像 Python {@code tests/compose/compilation/test_per_base.py}。</p>
 */
class PerBaseCompileTest {

    @Test
    @DisplayName("BaseModelPlan 无 binding → MISSING_BINDING (plan-lower)")
    void missingBindingThrows() {
        FakeSemanticService svc = new FakeSemanticService();
        BaseModelPlan plan = CompileTestHelpers.base("PartnerModel", "id");
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        plan,
                        CompileTestHelpers.context(
                                CompileTestHelpers.resolverFor(Map.of())),
                        ComposeSqlCompiler.CompileOptions.builder()
                                .semanticService(svc)
                                .bindings(Map.of())
                                .build()));
        assertEquals(ComposeCompileErrorCodes.MISSING_BINDING, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
        assertTrue(ex.getMessage().contains("PartnerModel"));
    }

    @Test
    @DisplayName("v1.3 抛 'Model not found: x' → MISSING_BINDING (plan-lower)")
    void modelNotFoundReclassified() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.throwNext(new RuntimeException("Model not found: PartnerModel"));

        BaseModelPlan plan = CompileTestHelpers.base("PartnerModel", "id");
        Map<String, ModelBinding> bindings = Map.of("PartnerModel", CompileTestHelpers.emptyBinding());

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        plan,
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                        ComposeSqlCompiler.CompileOptions.builder()
                                .semanticService(svc)
                                .bindings(bindings)
                                .dialect("sqlite")
                                .build()));
        assertEquals(ComposeCompileErrorCodes.MISSING_BINDING, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
    }

    @Test
    @DisplayName("v1.3 抛通用 RuntimeException → PER_BASE_COMPILE_FAILED (compile)")
    void perBaseFailureMapped() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.throwNext(new RuntimeException("no column 'foo'"));

        BaseModelPlan plan = CompileTestHelpers.base("M", "id");
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        plan,
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                        ComposeSqlCompiler.CompileOptions.builder()
                                .semanticService(svc)
                                .bindings(bindings)
                                .dialect("sqlite")
                                .build()));
        assertEquals(ComposeCompileErrorCodes.PER_BASE_COMPILE_FAILED, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_COMPILE, ex.phase());
        assertNotNull(ex.getCause());
        assertEquals("no column 'foo'", ex.getCause().getMessage());
    }

    @Test
    @DisplayName("正常路径返回 ComposedSql · SQL 包含底层 canned SQL")
    void happyPathReturnsComposedSql() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM partner_tbl");

        BaseModelPlan plan = CompileTestHelpers.base("M", "id");
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposedSql composed = ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertTrue(composed.getSql().contains("SELECT id FROM partner_tbl"));
    }

    @Test
    @DisplayName("fieldAccess 非空 → Request context fieldAccess 保持同一套 QM 字段")
    void fieldAccessForwardedToContext() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        ModelBinding binding = ModelBinding.builder()
                .fieldAccess(List.of("id", "name"))
                .build();

        ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of("M", binding))),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(Map.of("M", binding))
                        .dialect("sqlite")
                        .build());
        assertEquals(1, svc.invocations.size());
        assertNotNull(svc.invocations.get(0).context.getFieldAccess());
        assertTrue(svc.invocations.get(0).context.getFieldAccess().contains("id"));
        assertTrue(svc.invocations.get(0).context.getFieldAccess().contains("name"));
    }

    @Test
    @DisplayName("fieldAccess null → Request context fieldAccess 保持 null")
    void fieldAccessNullPreserved() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM t");
        ModelBinding binding = CompileTestHelpers.emptyBinding();   // fieldAccess null

        ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of("M", binding))),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(Map.of("M", binding))
                        .dialect("sqlite")
                        .build());
        assertNull(svc.invocations.get(0).context.getFieldAccess());
    }

    @Test
    @DisplayName("deniedColumns 非空 → 透传到 Request context")
    void deniedColumnsForwarded() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        DeniedPhysicalColumn denied = new DeniedPhysicalColumn("public", "t", "secret_col");
        ModelBinding binding = ModelBinding.builder()
                .deniedColumns(List.of(denied))
                .build();

        ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of("M", binding))),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(Map.of("M", binding))
                        .dialect("sqlite")
                        .build());
        assertEquals(1, svc.invocations.get(0).context.getDeniedColumns().size());
        assertEquals("secret_col",
                svc.invocations.get(0).context.getDeniedColumns().get(0).getColumn());
    }

    @Test
    @DisplayName("systemSlice 空 list → Request context systemSlice 置 null（对齐 Python）")
    void systemSliceEmptyBecomesNull() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        ModelBinding binding = CompileTestHelpers.emptyBinding();

        ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of("M", binding))),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(Map.of("M", binding))
                        .dialect("sqlite")
                        .build());
        assertNull(svc.invocations.get(0).context.getSystemSlice());
    }

    @Test
    @DisplayName("systemSlice 非空 → 透传")
    void systemSliceForwarded() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        ModelBinding binding = ModelBinding.builder()
                .systemSlice(List.of(new SliceRequestDef("tenant_id", "=", "ns1")))
                .build();

        ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of("M", binding))),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(Map.of("M", binding))
                        .dialect("sqlite")
                        .build());
        assertNotNull(svc.invocations.get(0).context.getSystemSlice());
        assertEquals(1, svc.invocations.get(0).context.getSystemSlice().size());
    }

    @Test
    @DisplayName("plan.columns 作为 Request.columns 传递（原序）")
    void columnsForwarded() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT a, b FROM t");
        BaseModelPlan plan = CompileTestHelpers.base("M", "a", "b");
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertEquals(List.of("a", "b"), svc.invocations.get(0).request.getColumns());
    }

    @Test
    @DisplayName("plan.calculatedFields 透传到 SemanticQueryRequest")
    void calculatedFieldsForwarded() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT name FROM t");
        CalculatedFieldDef cf = new CalculatedFieldDef("genderCopy", "gender");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M")
                .columns(List.of("name"))
                .calculatedFields(List.of(cf))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertEquals(1, svc.invocations.size());
        assertEquals(List.of(cf), svc.invocations.get(0).request.getCalculatedFields());
    }

    @Test
    @DisplayName("plan.orderBy 'name:desc' → Request.orderBy OrderItem {field=name, dir=desc}")
    void orderByDescParsed() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM t");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id"))
                .orderBy(List.of("name:desc"))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertEquals("name", svc.invocations.get(0).request.getOrderBy().get(0).getField());
        assertEquals("desc", svc.invocations.get(0).request.getOrderBy().get(0).getDir());
    }

    @Test
    @DisplayName("plan.orderBy bare 'name' → dir 默认 'asc'")
    void orderByBareFieldDefaultAsc() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM t");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id"))
                .orderBy(List.of("name"))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertEquals("asc", svc.invocations.get(0).request.getOrderBy().get(0).getDir());
    }

    @ParameterizedTest
    @CsvSource({
            "'-name', name, desc",
            "'+name', name, asc",
            "name, name, asc",
            "'name desc', name, desc",
            "'name asc', name, asc"
    })
    @DisplayName("compose base DSL orderBy shorthand normalizes before v1.3 validation")
    void orderByShorthandNormalizesBeforeValidation(
            String rawOrderBy,
            String expectedField,
            String expectedDir) {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM t");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id", "name"))
                .orderBy(List.of(rawOrderBy))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());

        assertEquals(expectedField, svc.invocations.get(0).request.getOrderBy().get(0).getField());
        assertEquals(expectedDir, svc.invocations.get(0).request.getOrderBy().get(0).getDir());
    }

    @Test
    @DisplayName("invalid shorthand orderBy forwards normalized field to validator")
    void orderByInvalidFieldForwardedWithoutPrefix() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM t");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id", "missingField"))
                .orderBy(List.of("-missingField"))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());

        assertEquals("missingField", svc.invocations.get(0).request.getOrderBy().get(0).getField());
        assertFalse(svc.invocations.get(0).request.getOrderBy().get(0).getField().startsWith("-"));
    }

    @Test
    @DisplayName("fieldAccess governance sees normalized orderBy field")
    void orderByFieldAccessUsesNormalizedField() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        ModelBinding binding = ModelBinding.builder()
                .fieldAccess(List.of("id"))
                .build();
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id", "secretAmount"))
                .orderBy(List.of("-secretAmount"))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", binding);

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());

        assertEquals("secretAmount", svc.invocations.get(0).request.getOrderBy().get(0).getField());
        assertTrue(svc.invocations.get(0).context.getFieldAccess().contains("id"));
        assertFalse(svc.invocations.get(0).context.getFieldAccess().contains("secretAmount"));
    }

    @Test
    @DisplayName("plan.groupBy 每项转为 GroupByItem(field, agg=null)")
    void groupByForwarded() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM t");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id"))
                .groupBy(List.of("dept"))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertEquals("dept", svc.invocations.get(0).request.getGroupBy().get(0).getField());
        assertNull(svc.invocations.get(0).request.getGroupBy().get(0).getAgg());
    }

    @Test
    @DisplayName("同一 binding 被两次 compile 时 governance-cache 只触发一次 generateSql")
    void governanceCacheDedup() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        BaseModelPlan plan = CompileTestHelpers.base("M", "id");
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        // Self-union at the plan level triggers two visits to the same
        // (model, binding) pair within a single compile pass.
        com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan selfUnion = plan.union(plan);
        ComposeSqlCompiler.compilePlanToSql(
                selfUnion,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());

        // id-cache hits first (same plan instance) — no re-call either way.
        assertEquals(1, svc.invocations.size());
    }

    @Test
    @DisplayName("同一 model/binding 的不同 base query shape 不能复用 governance-cache")
    void governanceCacheIncludesBasePlanShape() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("M")
                .columns(List.of("id", "current_amount"))
                .slice(List.of(Map.of("field", "period", "op", "=", "value", "current")))
                .build();
        BaseModelPlan previous = BaseModelPlan.builder()
                .model("M")
                .columns(List.of("id", "previous_amount"))
                .slice(List.of(Map.of("field", "period", "op", "=", "value", "previous")))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                current.union(previous),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());

        assertEquals(2, svc.invocations.size());
        assertEquals("current", svc.invocations.get(0).request.getSlice().get(0).getValue());
        assertEquals("previous", svc.invocations.get(1).request.getSlice().get(0).getValue());
    }

    @Test
    @DisplayName("plan.distinct 透传到 Request.distinct")
    void distinctForwarded() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT DISTINCT id FROM t");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id")).distinct(true).build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertTrue(svc.invocations.get(0).request.getDistinct());
    }

    @Test
    @DisplayName("plan.limit / start 透传到 Request")
    void limitStartForwarded() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t LIMIT 10 OFFSET 20");
        BaseModelPlan plan = BaseModelPlan.builder()
                .model("M").columns(List.of("id")).limit(10).start(20).build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertEquals(10, svc.invocations.get(0).request.getLimit());
        assertEquals(20, svc.invocations.get(0).request.getStart());
        assertFalse(svc.invocations.isEmpty());
    }
}
