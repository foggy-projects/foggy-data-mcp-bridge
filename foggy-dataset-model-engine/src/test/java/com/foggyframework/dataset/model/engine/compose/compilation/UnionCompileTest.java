package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UnionPlan} compilation · native {@code UNION} / {@code UNION ALL}
 * SQL emission (M6 · 6.2).
 */
class UnionCompileTest {

    private static ComposedSql compile(QueryPlan plan, FakeSemanticService svc,
                                       Map<String, ModelBinding> bindings, String dialect) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect(dialect)
                        .build());
    }

    /** Compile with explicit datasource IDs for F-7 tests. */
    private static ComposedSql compileWithDs(QueryPlan plan, FakeSemanticService svc,
                                              Map<String, ModelBinding> bindings, String dialect,
                                              Map<String, Optional<String>> datasourceIds) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .datasourceIds(datasourceIds)
                        .dialect(dialect)
                        .build());
    }

    @Test
    @DisplayName("UNION (distinct) · all=false")
    void unionDistinct() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT a FROM ta");
        svc.stub("B", "SELECT b FROM tb");
        BaseModelPlan a = CompileTestHelpers.base("A", "col");
        BaseModelPlan b = CompileTestHelpers.base("B", "col");

        ComposedSql sql = compile(a.union(b, false), svc,
                Map.of("A", CompileTestHelpers.emptyBinding(),
                        "B", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertTrue(sql.getSql().contains("UNION"));
        assertTrue(!sql.getSql().contains("UNION ALL"));
    }

    @Test
    @DisplayName("UNION ALL · all=true")
    void unionAll() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT a FROM ta");
        svc.stub("B", "SELECT b FROM tb");

        ComposedSql sql = compile(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col"), true),
                svc,
                Map.of("A", CompileTestHelpers.emptyBinding(), "B", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertTrue(sql.getSql().contains("UNION ALL"));
    }

    @Test
    @DisplayName("UNION 顶层 SQL 不包裹分支，保持 SQLite 可执行")
    void topLevelUnionBranchesAreExecutableOnSqlite() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT a FROM ta");
        svc.stub("B", "SELECT b FROM tb");

        ComposedSql sql = compile(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                svc,
                Map.of("A", CompileTestHelpers.emptyBinding(), "B", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertTrue(sql.getSql().contains("SELECT a FROM ta\nUNION\nSELECT b FROM tb"));
        assertTrue(!sql.getSql().contains("(SELECT a FROM ta)"));
    }

    @Test
    @DisplayName("UNION 参数按 left → right 顺序拼接")
    void paramOrderLeftThenRight() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT a FROM ta WHERE x = ?", "L");
        svc.stub("B", "SELECT b FROM tb WHERE y = ?", "R");

        ComposedSql sql = compile(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                svc,
                Map.of("A", CompileTestHelpers.emptyBinding(), "B", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertEquals(List.of("L", "R"), sql.getParams());
    }

    @Test
    @DisplayName("嵌套 union (a ∪ b) ∪ c → 两个 UNION 关键字")
    void nestedUnionLeftAssociative() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        svc.stub("C", "SELECT * FROM tc");

        BaseModelPlan a = CompileTestHelpers.base("A", "col");
        BaseModelPlan b = CompileTestHelpers.base("B", "col");
        BaseModelPlan c = CompileTestHelpers.base("C", "col");

        ComposedSql sql = compile(
                a.union(b).union(c),
                svc,
                Map.of(
                        "A", CompileTestHelpers.emptyBinding(),
                        "B", CompileTestHelpers.emptyBinding(),
                        "C", CompileTestHelpers.emptyBinding()),
                "sqlite");
        int count = 0;
        int idx = 0;
        while ((idx = sql.getSql().indexOf("UNION", idx)) != -1) {
            count += 1;
            idx += 1;
        }
        assertEquals(2, count);
    }

    @Test
    @DisplayName("derived over union — outer SELECT 围绕 union SQL")
    void derivedOverUnionSupported() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT v FROM ta");
        svc.stub("B", "SELECT v FROM tb");
        BaseModelPlan a = CompileTestHelpers.base("A", "v");
        BaseModelPlan b = CompileTestHelpers.base("B", "v");
        UnionPlan u = a.union(b);
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(u).columns(List.of("v")).build();

        ComposedSql sql = compile(
                derived,
                svc,
                Map.of("A", CompileTestHelpers.emptyBinding(), "B", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertTrue(sql.getSql().contains("UNION"));
        assertTrue(sql.getSql().contains("FROM ("));
    }

    @Test
    @DisplayName("3 个 BaseModelPlan 各自 generateSql 被调用恰好 1 次")
    void threeBasesEachGenerateSqlOnce() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        svc.stub("C", "SELECT * FROM tc");
        BaseModelPlan a = CompileTestHelpers.base("A", "col");
        BaseModelPlan b = CompileTestHelpers.base("B", "col");
        BaseModelPlan c = CompileTestHelpers.base("C", "col");

        compile(a.union(b).union(c), svc,
                Map.of("A", CompileTestHelpers.emptyBinding(),
                        "B", CompileTestHelpers.emptyBinding(),
                        "C", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertEquals(3, svc.invocations.size());
    }

    @Test
    @DisplayName("union 单个 BaseModelPlan 两侧 → id-cache 命中，只 1 次 generateSql")
    void selfUnionDeduped() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        BaseModelPlan a = CompileTestHelpers.base("A", "col");

        compile(a.union(a), svc,
                Map.of("A", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertEquals(1, svc.invocations.size());
    }

    @Test
    @DisplayName("union 默认 all=false（不 ALL）")
    void unionDefaultsToDistinct() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");

        ComposedSql sql = compile(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                svc,
                Map.of("A", CompileTestHelpers.emptyBinding(), "B", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertTrue(sql.getSql().contains("UNION"));
        assertTrue(!sql.getSql().contains("UNION ALL"));
    }

    @Test
    @DisplayName("异构 bindings · 左右都独立透传 fieldAccess")
    void heterogeneousBindings() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        ModelBinding ba = ModelBinding.builder().fieldAccess(List.of("x")).build();
        ModelBinding bb = ModelBinding.builder().fieldAccess(List.of("y")).build();

        compile(
                CompileTestHelpers.base("A", "x").union(CompileTestHelpers.base("B", "y")),
                svc,
                Map.of("A", ba, "B", bb),
                "sqlite");
        CompileTestHelpers.CapturedInvocation first = svc.invocations.get(0);
        CompileTestHelpers.CapturedInvocation second = svc.invocations.get(1);
        assertTrue(first.context.getFieldAccess().contains("x")
                || second.context.getFieldAccess().contains("x"));
        assertTrue(first.context.getFieldAccess().contains("y")
                || second.context.getFieldAccess().contains("y"));
    }

    // ------------------------------------------------------------------
    // F-7 · Cross-datasource rejection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F-7 · 跨数据源 union 拒绝 · CROSS_DATASOURCE_REJECTED")
    void crossDatasourceUnionRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        Map<String, ModelBinding> bindings = Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding());
        Map<String, Optional<String>> ds = Map.of(
                "A", Optional.of("ds-1"),
                "B", Optional.of("ds-2"));

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compileWithDs(
                        CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                        svc, bindings, "sqlite", ds));
        assertEquals(ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
        assertTrue(ex.getMessage().contains("union"));
    }

    @Test
    @DisplayName("F-7 · 同数据源 union 通过")
    void sameDatasourceUnionPasses() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        Map<String, ModelBinding> bindings = Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding());
        Map<String, Optional<String>> ds = Map.of(
                "A", Optional.of("ds-1"),
                "B", Optional.of("ds-1"));

        ComposedSql sql = compileWithDs(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                svc, bindings, "sqlite", ds);
        assertTrue(sql.getSql().contains("UNION"));
    }

    @Test
    @DisplayName("F-7 · unknown datasource 宽容通过")
    void unknownDatasourcePermissive() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        Map<String, ModelBinding> bindings = Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding());
        Map<String, Optional<String>> ds = Map.of(
                "A", Optional.of("ds-1"),
                "B", Optional.empty());

        ComposedSql sql = compileWithDs(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                svc, bindings, "sqlite", ds);
        assertTrue(sql.getSql().contains("UNION"));
    }

    @Test
    @DisplayName("F-7 · 双 unknown datasource 宽容通过")
    void bothUnknownDatasourcePermissive() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        Map<String, ModelBinding> bindings = Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding());
        Map<String, Optional<String>> ds = Map.of(
                "A", Optional.empty(),
                "B", Optional.empty());

        ComposedSql sql = compileWithDs(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                svc, bindings, "sqlite", ds);
        assertTrue(sql.getSql().contains("UNION"));
    }

    @Test
    @DisplayName("F-7 · 无 provider / 无 datasourceIds 仍通过（向后兼容）")
    void noProviderNoDatasourceIdsStillPasses() {
        // The original compile() helper does not set datasourceIds or modelInfoProvider
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        ComposedSql sql = compile(
                CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col")),
                svc,
                Map.of("A", CompileTestHelpers.emptyBinding(), "B", CompileTestHelpers.emptyBinding()),
                "sqlite");
        assertTrue(sql.getSql().contains("UNION"));
    }

    @Test
    @DisplayName("F-7 · 3-way union mismatch 拒绝")
    void threeWayUnionMismatchRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        svc.stub("C", "SELECT * FROM tc");
        Map<String, ModelBinding> bindings = Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding(),
                "C", CompileTestHelpers.emptyBinding());
        Map<String, Optional<String>> ds = Map.of(
                "A", Optional.of("ds-1"),
                "B", Optional.of("ds-1"),
                "C", Optional.of("ds-2"));

        // (A ∪ B) ∪ C — the outer union spans ds-1 and ds-2
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compileWithDs(
                        CompileTestHelpers.base("A", "col")
                                .union(CompileTestHelpers.base("B", "col"))
                                .union(CompileTestHelpers.base("C", "col")),
                        svc, bindings, "sqlite", ds));
        assertEquals(ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED, ex.code());
    }

    @Test
    @DisplayName("F-7 · UNION ALL mismatch 拒绝")
    void unionAllMismatchRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT * FROM ta");
        svc.stub("B", "SELECT * FROM tb");
        Map<String, ModelBinding> bindings = Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding());
        Map<String, Optional<String>> ds = Map.of(
                "A", Optional.of("ds-x"),
                "B", Optional.of("ds-y"));

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compileWithDs(
                        CompileTestHelpers.base("A", "col").union(CompileTestHelpers.base("B", "col"), true),
                        svc, bindings, "sqlite", ds));
        assertEquals(ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED, ex.code());
    }
}
