package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanAliasSupport;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryOptions;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link JoinPlan} compilation · dialect-aware CTE + subquery modes. */
class JoinCompileTest {

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

    private static FakeSemanticService twoModelSvc() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("L", "SELECT id, foo FROM tl");
        svc.stub("R", "SELECT id, bar FROM tr");
        return svc;
    }

    private static Map<String, ModelBinding> twoModelBindings() {
        return Map.of(
                "L", CompileTestHelpers.emptyBinding(),
                "R", CompileTestHelpers.emptyBinding());
    }

    @Test
    @DisplayName("INNER JOIN · SQLite (CTE) · 正确 SQL 结构")
    void innerJoinSqliteCte() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "sqlite");
        assertTrue(sql.getSql().startsWith("WITH "));
        assertTrue(sql.getSql().contains("INNER JOIN"));
        assertTrue(sql.getSql().contains(".id = "));
    }

    @Test
    @DisplayName("LEFT JOIN · MySQL 5.7 (子查询) · 用 tN 别名")
    void leftJoinMysqlSubquery() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.LEFT,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "mysql");
        assertTrue(!sql.getSql().startsWith("WITH "));
        assertTrue(sql.getSql().contains("LEFT JOIN"));
        assertTrue(sql.getSql().contains("AS t0"));
        assertTrue(sql.getSql().contains("AS t1"));
    }

    @Test
    @DisplayName("MySQL 8 · CTE 模式（显式 opt-in）")
    void mysql8OptInCte() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "mysql8");
        assertTrue(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("RIGHT JOIN · PostgreSQL (CTE)")
    void rightJoinPostgresCte() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.RIGHT,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "postgres");
        assertTrue(sql.getSql().contains("RIGHT JOIN"));
    }

    @Test
    @DisplayName("PostgreSQL · 隐式 $ 输出列跨 CTE/join/outer select 保持引号")
    void postgresImplicitDollarOutputQuotedAcrossCteLayers() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("L", "SELECT \"orderStatus$caption\" FROM tl");
        svc.stub("R", "SELECT \"orderStatus$caption\" FROM tr");

        BaseModelPlan left = CompileTestHelpers.base("L", "orderStatus$caption");
        BaseModelPlan right = CompileTestHelpers.base("R", "orderStatus$caption");
        JoinPlan joined = left.join(right, JoinType.INNER,
                List.of(JoinOn.of("orderStatus$caption", "=", "orderStatus$caption")));
        DerivedQueryPlan result = DerivedQueryPlan.builder()
                .source(joined)
                .columns(List.of("orderStatus$caption"))
                .orderBy(List.of("orderStatus$caption"))
                .build();

        ComposedSql sql = compile(result, svc, twoModelBindings(), "postgres");
        assertTrue(sql.getSql().contains("cte_0.\"orderStatus$caption\""));
        assertTrue(sql.getSql().contains("cte_1.\"orderStatus$caption\""));
        assertTrue(sql.getSql().contains("\"orderStatus$caption\""));
        assertTrue(sql.getSql().contains("ORDER BY \"orderStatus$caption\" ASC"));
        assertTrue(!sql.getSql().contains(".orderStatus$caption"));
    }

    @Test
    @DisplayName("FULL JOIN · MSSQL ok")
    void fullJoinMssqlOk() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.FULL,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "mssql");
        assertTrue(sql.getSql().contains("FULL OUTER JOIN"));
    }

    @Test
    @DisplayName("FULL JOIN · SQLite 拒绝 · UNSUPPORTED_PLAN_SHAPE (plan-lower)")
    void fullJoinSqliteRejected() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.FULL,
                List.of(JoinOn.of("id", "=", "id")));
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(j, twoModelSvc(), twoModelBindings(), "sqlite"));
        assertEquals(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
        assertTrue(ex.getMessage().contains("SQLite"));
    }

    @Test
    @DisplayName("多 JoinOn 用 AND 连接")
    void multipleOnClausesAndConnected() {
        JoinPlan j = CompileTestHelpers.base("L", "id", "foo").join(
                CompileTestHelpers.base("R", "id", "bar"),
                JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id"), JoinOn.of("foo", "=", "bar")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "sqlite");
        assertTrue(sql.getSql().contains(" AND "));
    }

    @Test
    @DisplayName("JoinOn 非等值 op=< 正确渲染")
    void nonEqualityOpRendered() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"),
                JoinType.INNER,
                List.of(JoinOn.of("id", "<", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "sqlite");
        assertTrue(sql.getSql().contains(".id < "));
    }

    @Test
    @DisplayName("self-join · 两侧同一 plan 实例 → 单 CTE")
    void selfJoinProducesSingleCte() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT id FROM tm");
        BaseModelPlan base = CompileTestHelpers.base("M", "id");
        JoinPlan j = base.join(base, JoinType.INNER, List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, svc, Map.of("M", CompileTestHelpers.emptyBinding()), "sqlite");
        // Only one generateSql invocation, and SQL contains only one CTE clause.
        assertEquals(1, svc.invocations.size());
        int count = 0;
        int idx = 0;
        while ((idx = sql.getSql().indexOf(" AS (", idx)) != -1) {
            count += 1;
            idx += 1;
        }
        assertEquals(1, count);
    }

    @Test
    @DisplayName("参数顺序：左 binding 参数先于右 binding 参数")
    void paramOrderLeftThenRight() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("L", "SELECT id FROM tl WHERE lx = ?", "LV");
        svc.stub("R", "SELECT id FROM tr WHERE rx = ?", "RV");

        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, svc, twoModelBindings(), "sqlite");
        assertEquals(List.of("LV", "RV"), sql.getParams());
    }

    @Test
    @DisplayName("3 模型链式 join · a join b join c")
    void threeWayJoin() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT id FROM ta");
        svc.stub("B", "SELECT id FROM tb");
        svc.stub("C", "SELECT id FROM tc");
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        BaseModelPlan b = CompileTestHelpers.base("B", "id");
        BaseModelPlan c = CompileTestHelpers.base("C", "id");

        QueryPlan joined = a.join(b, JoinType.INNER, List.of(JoinOn.of("id", "=", "id")))
                .join(c, JoinType.INNER, List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(joined, svc,
                Map.of("A", CompileTestHelpers.emptyBinding(),
                        "B", CompileTestHelpers.emptyBinding(),
                        "C", CompileTestHelpers.emptyBinding()),
                "sqlite");
        int count = 0;
        int idx = 0;
        while ((idx = sql.getSql().indexOf("INNER JOIN", idx)) != -1) {
            count += 1;
            idx += 1;
        }
        assertEquals(2, count);
    }

    @Test
    @DisplayName("JoinOn op=!= 正确透传")
    void neqOp() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"),
                JoinType.INNER,
                List.of(JoinOn.of("id", "!=", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "sqlite");
        assertTrue(sql.getSql().contains(".id != "));
    }

    @Test
    @DisplayName("post-join query accepts unique unqualified columns when join key overlaps")
    void postJoinQueryAcceptsUniqueUnqualifiedColumnsWithOverlappingJoinKey() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("L", "SELECT partner_id, partner_name, first_order_date FROM left_orders");
        svc.stub("R", "SELECT partner_id, order_count, total_amount FROM right_orders");

        QueryPlan left = CompileTestHelpers.base("L",
                "partner$id", "partner$caption", "firstOrderDate");
        QueryPlan right = CompileTestHelpers.base("R",
                "partner$id", "orderCount", "totalAmount");
        QueryPlan joined = left.join(right, JoinType.LEFT,
                List.of(JoinOn.of("partner$id", "=", "partner$id")));
        QueryPlan result = joined.query(QueryOptions.builder()
                .columns(List.of("partner$caption", "firstOrderDate", "orderCount", "totalAmount"))
                .orderBy(List.of("-totalAmount"))
                .build());

        ComposedSql sql = compile(result, svc, twoModelBindings(), "sqlite");
        assertTrue(sql.getSql().contains("partner$caption"));
        assertTrue(sql.getSql().contains("ORDER BY"));
        assertTrue(sql.getSql().contains("totalAmount"));
    }

    @Test
    @DisplayName("post-join query normalizes left/right and source-alias qualified fields")
    void postJoinQueryNormalizesQualifiedFields() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("L", "SELECT partner_id, partner_name, first_order_date FROM left_orders");
        svc.stub("R", "SELECT partner_id, order_count, total_amount FROM right_orders");

        QueryPlan firstOrders = CompileTestHelpers.base("L",
                "partner$id", "partner$caption", "firstOrderDate");
        PlanAliasSupport.bindAlias(firstOrders, "firstOrders");
        QueryPlan mayFirstCustomers = firstOrders.query(QueryOptions.builder()
                .columns(List.of("partner$id", "partner$caption", "firstOrderDate"))
                .slice(List.of(Map.of("field", "firstOrderDate", "op", ">=", "value", "2026-05-01")))
                .build());
        PlanAliasSupport.bindAlias(mayFirstCustomers, "mayFirstCustomers");
        QueryPlan mayOrders = CompileTestHelpers.base("R",
                "partner$id", "orderCount", "totalAmount");
        PlanAliasSupport.bindAlias(mayOrders, "mayOrders");

        QueryPlan joined = mayFirstCustomers.join(mayOrders, JoinType.LEFT,
                List.of(JoinOn.of("left.partner$id", "=", "right.partner$id")));
        DerivedQueryPlan result = joined.query(QueryOptions.builder()
                .columns(List.of(
                        "firstOrders.partner$caption",
                        "left.firstOrderDate",
                        "mayOrders.orderCount",
                        "right.totalAmount"))
                .orderBy(List.of("-mayOrders.totalAmount"))
                .build());

        assertEquals(List.of("partner$caption", "firstOrderDate", "orderCount", "totalAmount"),
                result.columns());
        assertEquals(List.of("-totalAmount"), result.orderBy());

        ComposedSql sql = compile(result, svc, twoModelBindings(), "sqlite");
        assertTrue(sql.getSql().contains("partner$caption"));
        assertTrue(sql.getSql().contains("ORDER BY"));
        assertTrue(sql.getSql().contains("totalAmount"));
        assertTrue(!sql.getSql().contains("firstOrders.partner$caption"));
        assertTrue(!sql.getSql().contains("mayOrders.totalAmount"));
    }

    @Test
    @DisplayName("source aliases do not expose columns dropped by an intermediate derived query")
    void sourceAliasDoesNotExposeDroppedColumns() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("L", "SELECT partner_id, partner_name, first_order_date FROM left_orders");
        svc.stub("R", "SELECT partner_id, order_count, total_amount FROM right_orders");

        QueryPlan firstOrders = CompileTestHelpers.base("L",
                "partner$id", "partner$caption", "firstOrderDate");
        PlanAliasSupport.bindAlias(firstOrders, "firstOrders");
        QueryPlan dropped = firstOrders.query(QueryOptions.builder()
                .columns(List.of("partner$id"))
                .build());
        QueryPlan mayOrders = CompileTestHelpers.base("R",
                "partner$id", "orderCount", "totalAmount");
        QueryPlan joined = dropped.join(mayOrders, JoinType.LEFT,
                List.of(JoinOn.of("partner$id", "=", "partner$id")));

        ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                () -> joined.query(QueryOptions.builder()
                        .columns(List.of("firstOrders.partner$caption", "orderCount"))
                        .build()));
        assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
    }

    // ------------------------------------------------------------------
    // F-7 · Cross-datasource rejection
    // ------------------------------------------------------------------

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
    @DisplayName("F-7 · cross-datasource inner join 拒绝")
    void crossDatasourceInnerJoinRejected() {
        Map<String, Optional<String>> ds = Map.of(
                "L", Optional.of("ds-1"),
                "R", Optional.of("ds-2"));
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compileWithDs(j, twoModelSvc(), twoModelBindings(), "sqlite", ds));
        assertEquals(ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
        assertTrue(ex.getMessage().contains("join"));
    }

    @Test
    @DisplayName("F-7 · same datasource join 通过")
    void sameDatasourceJoinPasses() {
        Map<String, Optional<String>> ds = Map.of(
                "L", Optional.of("ds-1"),
                "R", Optional.of("ds-1"));
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compileWithDs(j, twoModelSvc(), twoModelBindings(), "sqlite", ds);
        assertTrue(sql.getSql().contains("INNER JOIN"));
    }

    @Test
    @DisplayName("F-7 · unknown datasource join 宽容通过")
    void unknownDatasourceJoinPermissive() {
        Map<String, Optional<String>> ds = Map.of(
                "L", Optional.of("ds-1"),
                "R", Optional.empty());
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compileWithDs(j, twoModelSvc(), twoModelBindings(), "sqlite", ds);
        assertTrue(sql.getSql().contains("INNER JOIN"));
    }

    @Test
    @DisplayName("F-7 · no provider / no datasourceIds join 仍通过")
    void noProviderJoinStillPasses() {
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(j, twoModelSvc(), twoModelBindings(), "sqlite");
        assertTrue(sql.getSql().contains("INNER JOIN"));
    }

    @Test
    @DisplayName("F-7 · left join mismatch 拒绝")
    void leftJoinMismatchRejected() {
        Map<String, Optional<String>> ds = Map.of(
                "L", Optional.of("ds-a"),
                "R", Optional.of("ds-b"));
        JoinPlan j = CompileTestHelpers.base("L", "id").join(
                CompileTestHelpers.base("R", "id"), JoinType.LEFT,
                List.of(JoinOn.of("id", "=", "id")));
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compileWithDs(j, twoModelSvc(), twoModelBindings(), "sqlite", ds));
        assertEquals(ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED, ex.code());
    }
}
