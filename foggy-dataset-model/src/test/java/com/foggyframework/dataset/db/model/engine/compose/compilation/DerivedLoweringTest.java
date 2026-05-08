package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.ProjectedColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.BinaryExpr;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.ColumnExpr;
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
        assertTrue(sql.getSql().contains("WHERE cte_0.amount > ?"));
        assertEquals(List.of(100), sql.getParams());
    }

    @Test
    @DisplayName("WHERE 单键速写 {field: value} 默认 op =")
    void whereShortcutShape() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT * FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "status");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(Map.of("status", "open")))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("WHERE cte_0.status = ?"));
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
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "name");
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
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "name");
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
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "outer_flag");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(Map.of("field", "outer_flag", "op", "=", "value", 2)))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertEquals(List.of(1, 2), sql.getParams());
    }

    @Test
    @DisplayName("WHERE 支持 {'$field': rhs} 字段对字段比较")
    void whereSupportsFieldReferenceValue() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT left_amount, right_amount FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "left_amount", "right_amount");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("left_amount", "right_amount"))
                .slice(List.of(Map.of(
                        "field", "left_amount",
                        "op", "<",
                        "value", Map.of("$field", "right_amount"))))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("cte_0.left_amount < cte_0.right_amount"));
        assertTrue(sql.getParams().stream().noneMatch(Map.class::isInstance));
    }

    @Test
    @DisplayName("同一层新建 alias 不能立刻用于 slice")
    void sameStageAliasSliceRejectedAtCompile() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT partner_id, amount FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "partner_id", "amount");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("partner_id", "amount - 100 AS decrease_amount"))
                .slice(List.of(Map.of("field", "decrease_amount", "op", ">", "value", 0)))
                .build();

        ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                () -> compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite"));
        assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_SAME_STAGE_ALIAS, ex.code());
        assertEquals("decrease_amount", ex.offendingField());
    }

    @Test
    @DisplayName("derived orderBy 未知字段在编译期拒绝")
    void unknownOrderByFieldRejectedBeforeSql() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id, amount FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "amount");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("id", "amount"))
                .orderBy(List.of("collection_rate ASC"))
                .build();

        ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                () -> compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres"));
        assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
        assertEquals("collection_rate", ex.offendingField());
        assertTrue(ex.getMessage().contains("order_by"));
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
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "a", "b");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id"))
                .slice(List.of(
                        Map.of("field", "a", "op", "=", "value", 1),
                        Map.of("field", "b", "op", "=", "value", 2)))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        assertTrue(sql.getSql().contains("cte_0.a = ? AND cte_0.b = ?"));
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
    @DisplayName("derived ProjectedColumn(BinaryExpr) 校验表达式操作数而不是对象 toString")
    void projectedBinaryExprValidatesOperandsInsteadOfClassName() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT amount, tax FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "amount", "tax");
        ProjectedColumn total = new ProjectedColumn(
                new BinaryExpr(new ColumnExpr("amount"), "+", new ColumnExpr("tax")),
                "total",
                null);
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of(total))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");

        assertTrue(sql.getSql().contains("(amount + tax) AS total"));
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

        assertTrue(sql.getSql().contains("status IN (?, ?)"));
        assertTrue(!sql.getSql().contains(" IN ?"));
        assertEquals(List.of("draft", "done"), sql.getParams());
    }

    @Test
    @DisplayName("IS NULL operator generates no parameters")
    void derivedSliceIsNullAddsNoParam() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id, status FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "id", "status");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("id"))
                .slice(List.of(Map.of("field", "status", "op", "is null")))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres");

        assertTrue(sql.getSql().contains("IS NULL"));
        assertTrue(!sql.getSql().contains("IS NULL ?"));
        assertTrue(sql.getParams().isEmpty());
    }

    @Test
    @DisplayName("rejects unresolved dotted alias fields in derived slice")
    void derivedSliceRejectsUnresolvedQualifiedDollarRef() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT partner_id FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "partner_id");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("partner_id"))
                .slice(List.of(Map.of("field", "priorOrders.partner$id", "op", "is null")))
                .build();

        ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                () -> compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres"));
        assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
        assertEquals("priorOrders", ex.offendingField());
    }

    @Test
    @DisplayName("derived alias output schema uses alias in join projection")
    void derivedAliasOutputSchemaUsesAliasInJoinProjection() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT status FROM tbl");
        BaseModelPlan left = CompileTestHelpers.base("M", "status AS current_status");
        DerivedQueryPlan right = DerivedQueryPlan.builder()
                .source(CompileTestHelpers.base("M", "status"))
                .columns(List.of("status as prior_status"))
                .build();
        JoinPlan joined = JoinPlan.builder()
                .left(left)
                .right(right)
                .type("left")
                .on(List.of(JoinOn.of("current_status", "=", "prior_status")))
                .build();

        ComposedSql sql = compile(joined, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres");

        assertTrue(!sql.getSql().contains("cte_2.\"status as prior_status\""));
        assertTrue(sql.getSql().contains("cte_2.prior_status"));
    }

    @Test
    @DisplayName("nested logical slice condition with is null")
    void derivedSliceNestedOrWithIsNull() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT a, b FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "a", "b");
        JoinPlan joined = JoinPlan.builder()
                .left(base)
                .right(base)
                .type("left")
                .on(List.of(JoinOn.of("a", "=", "a")))
                .build();
        
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(joined)
                .columns(List.of("a", "b"))
                .slice(List.of(Map.of(
                        "$or", List.of(
                                Map.of("field", "b", "op", "=", "value", 0),
                                Map.of("field", "b", "op", "is null")
                        )
                )))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres");

        assertTrue(sql.getSql().contains("OR"));
        assertTrue(sql.getSql().contains("IS NULL"));
        assertTrue(!sql.getSql().contains("$or"));
        assertEquals(1, sql.getParams().size());
        assertEquals(0, sql.getParams().get(0));

        DerivedQueryPlan derivedBad = DerivedQueryPlan.builder()
                .source(joined)
                .columns(List.of("a", "b"))
                .slice(List.of(Map.of(
                        "$or", List.of(
                                Map.of("field", "b", "op", "=", "value", 0),
                                Map.of("field", "unknownField", "op", "is null")
                        )
                )))
                .build();

        ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                () -> compile(derivedBad, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres"));
        assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
        assertEquals("unknownField", ex.offendingField());
    }

    @Test
    @DisplayName("$and slice operator renders AND-joined predicates")
    void derivedSliceAndOperator() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT a, b FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "a", "b");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("a", "b"))
                .slice(List.of(Map.of(
                        "$and", List.of(
                                Map.of("field", "a", "op", ">", "value", 10),
                                Map.of("field", "b", "op", "<", "value", 100)
                        )
                )))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres");

        assertTrue(sql.getSql().contains("AND"), "$and should produce AND in SQL");
        assertTrue(!sql.getSql().contains("$and"), "DSL token should not leak into SQL");
        assertEquals(2, sql.getParams().size());
        assertEquals(10, sql.getParams().get(0));
        assertEquals(100, sql.getParams().get(1));
    }

    @Test
    @DisplayName("$and wrapping $or renders nested (a OR b) AND (c)")
    void derivedSliceNestedOrInsideAnd() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT a, b, c FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "a", "b", "c");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("a", "b", "c"))
                .slice(List.of(Map.of(
                        "$and", List.of(
                                // inner $or
                                Map.of("$or", List.of(
                                        Map.of("field", "a", "op", "=", "value", 1),
                                        Map.of("field", "b", "op", "is null")
                                )),
                                Map.of("field", "c", "op", "=", "value", 99)
                        )
                )))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "postgres");

        String sqlStr = sql.getSql();
        assertTrue(sqlStr.contains("OR"), "Inner $or should produce OR");
        assertTrue(sqlStr.contains("AND"), "Outer $and should produce AND");
        assertTrue(sqlStr.contains("IS NULL"), "is null should render without param");
        assertTrue(!sqlStr.contains("$or") && !sqlStr.contains("$and"), "DSL tokens must not leak");
        // params: a=1 and c=99; b IS NULL has no param
        assertEquals(2, sql.getParams().size());
        assertEquals(1, sql.getParams().get(0));
        assertEquals(99, sql.getParams().get(1));
    }

    @Test
    @DisplayName("$not wraps single condition as NOT (...)")
    void derivedSliceNotOperator() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT a, b FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "a", "b");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("a", "b"))
                .slice(List.of(Map.of(
                        "$not", Map.of("field", "a", "op", "=", "value", 42)
                )))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "mysql8");

        assertTrue(sql.getSql().contains("NOT ("), "$not should wrap condition in NOT (...)");
        assertTrue(!sql.getSql().contains("$not"), "DSL token must not leak");
        assertEquals(1, sql.getParams().size());
        assertEquals(42, sql.getParams().get(0));
    }

    @Test
    @DisplayName("empty $or block is skipped — no WHERE clause generated")
    void derivedSliceEmptyLogicalBlockIsSkipped() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT a, b FROM tbl");
        BaseModelPlan base = CompileTestHelpers.base("M", "a", "b");
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("a", "b"))
                // $or with empty list should be a no-op, not a SQL syntax error
                .slice(List.of(Map.of("$or", List.of())))
                .build();

        ComposedSql sql = compile(derived, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "mysql8");

        assertTrue(!sql.getSql().contains("WHERE"), "Empty $or must not emit a WHERE clause");
        assertTrue(sql.getParams().isEmpty(), "No params from empty logical block");
    }
}
