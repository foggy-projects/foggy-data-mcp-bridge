package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.authority.ModelInfoProvider;
import com.foggyframework.dataset.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.model.semantic.port.ComposeSqlGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public entry point {@link ComposeSqlCompiler} — API shape + one-shot /
 * two-step caller patterns + defensive reflection guard.
 */
class ComposeSqlCompilerTest {

    @Test
    @DisplayName("反射保护 · 编译器不得暴露 public 实例字段或 public ctor")
    void compilerSurfaceIsStaticOnly() {
        for (Field f : ComposeSqlCompiler.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers()),
                    () -> "Compiler must not expose instance field " + f.getName());
        }
        for (Constructor<?> c : ComposeSqlCompiler.class.getDeclaredConstructors()) {
            assertFalse(Modifier.isPublic(c.getModifiers()),
                    "Compiler must not expose a public ctor");
        }
    }

    @Test
    @DisplayName("one-shot 路径 · bindings=null → 内部调 AuthorityResolutionPipeline.resolve")
    void oneShotInvokesResolver() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");

        ModelBinding binding = CompileTestHelpers.emptyBinding();
        ComposeQueryContext ctx = CompileTestHelpers.context(
                CompileTestHelpers.resolverFor(Map.of("M", binding)));

        ComposedSql sql = ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                ctx,
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .dialect("sqlite")
                        .build());
        assertNotNull(sql.getSql());
    }

    @Test
    @DisplayName("two-step 路径 · 预先 resolve 的 bindings 直接复用")
    void twoStepReusesBindings() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposedSql sql = ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
        assertNotNull(sql.getSql());
    }

    @Test
    @DisplayName("opts == null → IllegalArgumentException")
    void nullOptsRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        CompileTestHelpers.base("M", "id"),
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of())),
                        (ComposeSqlCompiler.CompileOptions) null));
    }

    @Test
    @DisplayName("semanticService null → IllegalArgumentException")
    void nullSemanticServiceRejected() {
        // Builder allows null; the check fires at compile time.
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        CompileTestHelpers.base("M", "id"),
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of())),
                        ComposeSqlCompiler.CompileOptions.builder().build()));
    }

    @Test
    @DisplayName("plan null → IllegalArgumentException")
    void nullPlanRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        null,
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of())),
                        ComposeSqlCompiler.CompileOptions.builder()
                                .semanticService(svc).build()));
    }

    @Test
    @DisplayName("便捷 overload compilePlanToSql(plan, ctx, semanticService) 走 mysql 默认")
    void convenienceOverloadUsesMysqlDefault() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");
        ModelBinding binding = CompileTestHelpers.emptyBinding();
        ComposedSql sql = ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(Map.of("M", binding))),
                svc);
        // default dialect "mysql" → subquery (no WITH prefix)
        assertFalse(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("narrow planning port 可独立编译且保留 null bind 参数")
    void narrowPlanningPortCompilesWithoutLegacySemanticService() {
        ComposeSemanticPlanningPort planningPort = (model, request, context) ->
                new ComposeSqlGeneration(
                        "SELECT id FROM t WHERE deleted_at IS ?",
                        Arrays.asList((Object) null),
                        List.of(),
                        Map.of());
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeSqlCompiler.CompileOptions opts = ComposeSqlCompiler.CompileOptions.builder()
                .planningPort(planningPort)
                .bindings(bindings)
                .dialect("sqlite")
                .build();
        ComposedSql sql = ComposeSqlCompiler.compilePlanToSql(
                CompileTestHelpers.base("M", "id"),
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                opts);

        assertEquals(planningPort, opts.planningPort());
        assertNull(opts.semanticService());
        assertEquals(1, sql.getParams().size());
        assertNull(sql.getParams().get(0));
    }

    @Test
    @DisplayName("CompileOptions.Builder 默认 dialect=mysql")
    void builderDefaultDialectIsMysql() {
        FakeSemanticService svc = new FakeSemanticService();
        ComposeSqlCompiler.CompileOptions opts = ComposeSqlCompiler.CompileOptions.builder()
                .semanticService(svc).build();
        assertEquals("mysql", opts.dialect());
    }

    @Test
    @DisplayName("CompileOptions.Builder 字段可读")
    void builderFieldsReadback() {
        FakeSemanticService svc = new FakeSemanticService();
        Map<String, ModelBinding> bindings = Map.of();
        Map<String, Optional<String>> datasourceIds = Map.of("M", Optional.of("ds-main"));
        ComposeSqlCompiler.CompileOptions opts = ComposeSqlCompiler.CompileOptions.builder()
                .semanticService(svc)
                .bindings(bindings)
                .datasourceIds(datasourceIds)
                .dialect("postgres")
                .normalizePlan(true)
                .build();
        assertEquals(svc, opts.semanticService());
        assertEquals(bindings, opts.bindings());
        assertEquals(datasourceIds, opts.datasourceIds());
        assertEquals("postgres", opts.dialect());
        assertTrue(opts.normalizePlan());
    }

    @Test
    @DisplayName("CompileOptions.Builder 默认不启用 plan normalization")
    void builderNormalizePlanDefaultsFalse() {
        FakeSemanticService svc = new FakeSemanticService();
        ComposeSqlCompiler.CompileOptions opts = ComposeSqlCompiler.CompileOptions.builder()
                .semanticService(svc)
                .build();
        assertFalse(opts.normalizePlan());
    }

    @Test
    @DisplayName("normalizePlan=true · 空 derived wrapper 输出与 base plan SQL/params 等价")
    void normalizePlanOptInKeepsSqlAndParamsEquivalentForEmptyWrapper() {
        for (String dialect : List.of("mysql", "sqlite", "postgres")) {
            QueryPlan base = CompileTestHelpers.base("M", "id");
            QueryPlan wrapped = DerivedQueryPlan.builder().source(base).build();
            Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

            FakeSemanticService baseSvc = new FakeSemanticService();
            baseSvc.stub("M", "SELECT id FROM t WHERE status = ?", "paid");
            ComposedSql baseSql = ComposeSqlCompiler.compilePlanToSql(
                    base,
                    CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                    ComposeSqlCompiler.CompileOptions.builder()
                            .semanticService(baseSvc)
                            .bindings(bindings)
                            .dialect(dialect)
                            .build());

            FakeSemanticService wrappedSvc = new FakeSemanticService();
            wrappedSvc.stub("M", "SELECT id FROM t WHERE status = ?", "paid");
            ComposedSql normalizedSql = ComposeSqlCompiler.compilePlanToSql(
                    wrapped,
                    CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                    ComposeSqlCompiler.CompileOptions.builder()
                            .semanticService(wrappedSvc)
                            .bindings(bindings)
                            .dialect(dialect)
                            .normalizePlan(true)
                            .build());

            assertEquals(baseSql.getSql(), normalizedSql.getSql(), dialect);
            assertEquals(baseSql.getParams(), normalizedSql.getParams(), dialect);
        }
    }

    @Test
    @DisplayName("normalizePlan=false · 空 wrapper 仍走历史 plan shape，超深 plan 触发深度保护")
    void normalizePlanDefaultFalseKeepsDepthGuardShape() {
        QueryPlan root = CompileTestHelpers.base("M", "id");
        for (int i = 0; i < PlanHash.MAX_PLAN_DEPTH; i++) {
            root = DerivedQueryPlan.builder().source(root).build();
        }
        final QueryPlan deep = root;
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        FakeSemanticService defaultSvc = new FakeSemanticService();
        defaultSvc.stub("M", "SELECT id FROM t");
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        deep,
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                        ComposeSqlCompiler.CompileOptions.builder()
                                .semanticService(defaultSvc)
                                .bindings(bindings)
                                .dialect("sqlite")
                                .build()));
        assertEquals(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE, ex.code());

        FakeSemanticService normalizedSvc = new FakeSemanticService();
        normalizedSvc.stub("M", "SELECT id FROM t");
        ComposedSql sql = ComposeSqlCompiler.compilePlanToSql(
                deep,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(normalizedSvc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .normalizePlan(true)
                        .build());
        assertNotNull(sql.getSql());
    }

    @Test
    @DisplayName("normalizePlan=true · 非空 derived wrapper 不被误删")
    void normalizePlanOptInKeepsNonEmptyDerivedWrapper() {
        QueryPlan base = CompileTestHelpers.base("M", "id", "amount");
        QueryPlan wrapped = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("id"))
                .build();
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        FakeSemanticService defaultSvc = new FakeSemanticService();
        defaultSvc.stub("M", "SELECT id, amount FROM t", 10);
        ComposedSql defaultSql = ComposeSqlCompiler.compilePlanToSql(
                wrapped,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(defaultSvc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());

        FakeSemanticService normalizedSvc = new FakeSemanticService();
        normalizedSvc.stub("M", "SELECT id, amount FROM t", 10);
        ComposedSql normalizedSql = ComposeSqlCompiler.compilePlanToSql(
                wrapped,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(normalizedSvc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .normalizePlan(true)
                        .build());

        assertEquals(defaultSql.getSql(), normalizedSql.getSql());
        assertEquals(defaultSql.getParams(), normalizedSql.getParams());
    }

    @Test
    @DisplayName("F-7 · modelInfoProvider 自动收集 datasourceIds 并拒绝跨数据源 union")
    void autoCollectsDatasourceIdsFromProvider() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT id FROM ta");
        svc.stub("B", "SELECT id FROM tb");
        Map<String, ModelBinding> bindings = Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding());
        ModelInfoProvider provider = new ModelInfoProvider() {
            @Override
            public Optional<List<String>> getTablesForModel(String modelName, String namespace) {
                return Optional.of(List.of());
            }

            @Override
            public Optional<String> getDatasourceId(String modelName, String namespace) {
                return Optional.of("A".equals(modelName) ? "ds-1" : "ds-2");
            }
        };

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        CompileTestHelpers.base("A", "id")
                                .union(CompileTestHelpers.base("B", "id")),
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                        ComposeSqlCompiler.CompileOptions.builder()
                                .semanticService(svc)
                                .bindings(bindings)
                                .modelInfoProvider(provider)
                                .dialect("sqlite")
                                .build()));
        assertEquals(ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
    }

    @Test
    @DisplayName("MAX_PLAN_DEPTH 超限 · UNSUPPORTED_PLAN_SHAPE (plan-lower)")
    void depthGuardTriggers() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");

        QueryPlan root = CompileTestHelpers.base("M", "id");
        for (int i = 0; i < PlanHash.MAX_PLAN_DEPTH; i++) {
            root = DerivedQueryPlan.builder().source(root).columns(List.of("id")).build();
        }
        final QueryPlan deep = root;
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> ComposeSqlCompiler.compilePlanToSql(
                        deep,
                        CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                        ComposeSqlCompiler.CompileOptions.builder()
                                .semanticService(svc)
                                .bindings(bindings)
                                .dialect("sqlite")
                                .build()));
        assertEquals(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
        assertTrue(ex.getMessage().contains("MAX_PLAN_DEPTH=32"));
    }

    @Test
    @DisplayName("MAX_PLAN_DEPTH 正好等于 32 时通过")
    void depthAtMaxAllowed() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM t");

        QueryPlan root = CompileTestHelpers.base("M", "id");
        // base=1; 31 wrappings → depth 32
        for (int i = 1; i < PlanHash.MAX_PLAN_DEPTH; i++) {
            root = DerivedQueryPlan.builder().source(root).columns(List.of("id")).build();
        }
        assertEquals(PlanHash.MAX_PLAN_DEPTH, PlanHash.planDepth(root));
        Map<String, ModelBinding> bindings = Map.of("M", CompileTestHelpers.emptyBinding());
        // Should not throw.
        ComposeSqlCompiler.compilePlanToSql(
                root,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("sqlite")
                        .build());
    }
}
