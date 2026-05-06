package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Derived plan lowering (M6 · 6.1) · {@code SELECT ... FROM (<inner>) AS t}.
 */
class DerivedLoweringTest {

    private static ComposedSql compile(com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan plan,
                                       FakeSemanticService svc,
                                       Map<String, ModelBinding> bindings,
                                       String dialect) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect(dialect)
                        .build());
    }

    @Test
    @DisplayName("DerivedQueryPlan 基础 · SELECT cols FROM (inner) AS alias")
    void derivedBasicShape() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id, amount FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "amount");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("id"))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("SELECT id"));
        assertTrue(sql.getSql().contains("FROM (SELECT id, amount FROM tbl)"));
    }

    @Test
    @DisplayName("DISTINCT 列前缀")
    void distinctEmitted() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id")).distinct(true).build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("SELECT DISTINCT id"));
    }

    @Test
    @DisplayName("WHERE 条件使用 {field, op, value} dict 构造")
    void whereFromFullDict() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id, amount FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "amount");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(Map.of("field", "amount", "op", ">", "value", 100)))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("WHERE amount > ?"));
        assertEquals(List.of(100), sql.getParams());
    }

    @Test
    @DisplayName("WHERE 单键速写 {field: value} 默认 op =")
    void whereShortcutShape() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(Map.of("status", "open")))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("WHERE status = ?"));
        assertEquals(List.of("open"), sql.getParams());
    }

    @Test
    @DisplayName("GROUP BY 透传")
    void groupByEmitted() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "dept");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("dept"))
                .groupBy(List.of("dept"))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("GROUP BY dept"));
    }

    @Test
    @DisplayName("ORDER BY 'name:desc' → ORDER BY name DESC")
    void orderByDescEmitted() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .orderBy(List.of("name:desc"))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("ORDER BY name DESC"));
    }

    @Test
    @DisplayName("ORDER BY bare 名称 → ORDER BY name （不加 ASC 标注）")
    void orderByBareEmitted() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .orderBy(List.of("name"))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("ORDER BY name"));
    }

    @Test
    @DisplayName("LIMIT + OFFSET 内联整数")
    void limitOffsetInlined() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .limit(50).start(100).build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("LIMIT 50 OFFSET 100"));
    }

    @Test
    @DisplayName("参数顺序：inner params 先于 outer params")
    void paramOrderingInnerBeforeOuter() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl WHERE inner_flag = ?", 1);
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(Map.of("field", "outer_flag", "op", "=", "value", 2)))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertEquals(List.of(1, 2), sql.getParams());
    }

    @Test
    @DisplayName("derived-over-derived 链式嵌套支持")
    void chainedDerivedSupported() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan d1 = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id")).build();
        DerivedQueryPlan d2 = DerivedQueryPlan.builder()
                .source(d1).columns(List.of("id")).build();

        ComposedSql sql = compile(d2, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        // outer wraps inner which wraps the base SQL
        int first = sql.getSql().indexOf("SELECT id");
        int second = sql.getSql().indexOf("SELECT id", first + 1);
        assertTrue(second > first, "outer + inner SELECTs should be distinct");
    }

    @Test
    @DisplayName("slice entry 类型错误 → UNSUPPORTED_PLAN_SHAPE")
    void sliceNotMapRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of("not a map"))
                .build();

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite"));
        assertEquals(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE, ex.code());
    }

    @Test
    @DisplayName("single-key shortcut 但 key 数 > 1 → UNSUPPORTED_PLAN_SHAPE")
    void sliceShortcutMultiKeyRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        Map<String, Object> bad = Map.of("a", 1, "b", 2);
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(bad))
                .build();

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite"));
        assertEquals(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE, ex.code());
    }

    @Test
    @DisplayName("multi slice entry 用 AND 连接")
    void multipleSliceEntriesAndConnected() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(
                        Map.of("field", "a", "op", "=", "value", 1),
                        Map.of("field", "b", "op", "=", "value", 2)))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("a = ? AND b = ?"));
    }

    @Test
    @DisplayName("derived 引用不存在的 $ 字段时编译期拒绝")
    void unknownDollarFieldRejectedBeforeSql() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id, amount FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "amount");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("salesperson$id"))
                .build();

        ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                () -> compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres"));
        assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
        assertEquals("salesperson$id", ex.offendingField());
    }

    @Test
    @DisplayName("derived slice IN 列表展开为多个占位符")
    void sliceInListExpandsPlaceholders() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id, status FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "status");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("id"))
                .slice(List.of(Map.of(
                        "field", "status",
                        "op", "in",
                        "value", List.of("draft", "done"))))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres");

        assertTrue(sql.getSql().contains("WHERE status IN (?, ?)"));
        assertTrue(!sql.getSql().contains(" IN ?"));
        assertEquals(List.of("draft", "done"), sql.getParams());
    }
}
