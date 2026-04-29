package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 4-dialect × plan-shape CTE vs subquery fallback matrix. */
class DialectFallbackTest {

    private static FakeSemanticService svcAB() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("A", "SELECT id FROM ta");
        svc.stub("B", "SELECT id FROM tb");
        return svc;
    }

    private static Map<String, ModelBinding> bindingsAB() {
        return Map.of(
                "A", CompileTestHelpers.emptyBinding(),
                "B", CompileTestHelpers.emptyBinding());
    }

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

    // ---------- dialectSupportsCte ----------

    @Test
    @DisplayName("dialectSupportsCte: mysql/mysql57 → false")
    void mysqlLegacyNoCte() {
        assertFalse(ComposePlanner.dialectSupportsCte("mysql"));
        assertFalse(ComposePlanner.dialectSupportsCte("mysql57"));
        assertFalse(ComposePlanner.dialectSupportsCte("MYSQL"));  // case-insensitive
    }

    @Test
    @DisplayName("dialectSupportsCte: mysql8 → true")
    void mysql8IsCte() {
        assertTrue(ComposePlanner.dialectSupportsCte("mysql8"));
    }

    @Test
    @DisplayName("dialectSupportsCte: postgres / postgresql / sqlite → true; mssql → false")
    void modernDialectsAreCte() {
        assertTrue(ComposePlanner.dialectSupportsCte("postgres"));
        assertTrue(ComposePlanner.dialectSupportsCte("postgresql"));
        assertTrue(ComposePlanner.dialectSupportsCte("sqlite"));
        assertFalse(ComposePlanner.dialectSupportsCte("mssql"));
        assertFalse(ComposePlanner.dialectSupportsCte("sqlserver"));
    }

    // ---------- unknown dialect fail-closed ----------

    @Test
    @DisplayName("未知 dialect → UNSUPPORTED_PLAN_SHAPE (plan-lower)")
    void unknownDialectRejected() {
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(a, svcAB(), bindingsAB(), "oracle"));
        assertEquals(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
    }

    @Test
    @DisplayName("空 dialect → UNSUPPORTED_PLAN_SHAPE")
    void emptyDialectRejected() {
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        assertThrows(ComposeCompileException.class,
                () -> compile(a, svcAB(), bindingsAB(), ""));
    }

    // ---------- single base under each dialect ----------

    @Test
    @DisplayName("single-base mysql · 子查询 FROM (inner) AS t0")
    void singleBaseMysqlSubquery() {
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        ComposedSql sql = compile(a, svcAB(), bindingsAB(), "mysql");
        assertTrue(sql.getSql().contains("FROM ("));
        assertTrue(sql.getSql().contains("AS t0"));
    }

    @Test
    @DisplayName("single-base mysql8 · WITH cte_N AS (...)")
    void singleBaseMysql8Cte() {
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        ComposedSql sql = compile(a, svcAB(), bindingsAB(), "mysql8");
        assertTrue(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("single-base postgres · WITH cte_N")
    void singleBasePostgres() {
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        ComposedSql sql = compile(a, svcAB(), bindingsAB(), "postgres");
        assertTrue(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("single-base sqlite · WITH cte_N")
    void singleBaseSqlite() {
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        ComposedSql sql = compile(a, svcAB(), bindingsAB(), "sqlite");
        assertTrue(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("single-base mssql · 子查询 fallback")
    void singleBaseMssql() {
        BaseModelPlan a = CompileTestHelpers.base("A", "id");
        ComposedSql sql = compile(a, svcAB(), bindingsAB(), "mssql");
        assertTrue(sql.getSql().contains("FROM ("));
        assertFalse(sql.getSql().startsWith("WITH "));
    }

    // ---------- join across dialects ----------

    @Test
    @DisplayName("join · mysql (legacy) 走子查询 + tN 别名")
    void joinMysqlSubquery() {
        QueryPlan plan = CompileTestHelpers.base("A", "id").join(
                CompileTestHelpers.base("B", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(plan, svcAB(), bindingsAB(), "mysql");
        assertTrue(sql.getSql().contains("AS t0"));
        assertTrue(sql.getSql().contains("AS t1"));
        assertFalse(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("join · mysql8 走 CTE")
    void joinMysql8Cte() {
        QueryPlan plan = CompileTestHelpers.base("A", "id").join(
                CompileTestHelpers.base("B", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(plan, svcAB(), bindingsAB(), "mysql8");
        assertTrue(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("join · postgres 走 CTE")
    void joinPostgresCte() {
        QueryPlan plan = CompileTestHelpers.base("A", "id").join(
                CompileTestHelpers.base("B", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(plan, svcAB(), bindingsAB(), "postgres");
        assertTrue(sql.getSql().startsWith("WITH "));
    }

    @Test
    @DisplayName("join · sqlite 走 CTE")
    void joinSqliteCte() {
        QueryPlan plan = CompileTestHelpers.base("A", "id").join(
                CompileTestHelpers.base("B", "id"), JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));
        ComposedSql sql = compile(plan, svcAB(), bindingsAB(), "sqlite");
        assertTrue(sql.getSql().startsWith("WITH "));
    }

    // ---------- union across dialects ----------

    @Test
    @DisplayName("union 不受 dialect 影响（都是 native UNION SQL）")
    void unionDialectIndependent() {
        QueryPlan plan = CompileTestHelpers.base("A", "id").union(CompileTestHelpers.base("B", "id"));
        for (String dialect : List.of("mysql", "mysql8", "postgres", "mssql", "sqlite")) {
            ComposedSql sql = compile(plan, svcAB(), bindingsAB(), dialect);
            assertTrue(sql.getSql().contains("UNION"), "dialect " + dialect + " missing UNION");
        }
    }

    // ---------- derived chain across dialects ----------

    @Test
    @DisplayName("derived-chain · 4 方言下参数一致")
    void derivedChainParamsStableAcrossDialects() {
        com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan derived =
                com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan.builder()
                        .source(CompileTestHelpers.base("A", "id"))
                        .columns(List.of("id"))
                        .slice(List.of(Map.of("field", "id", "op", ">", "value", 10)))
                        .build();
        List<Object> params = null;
        for (String dialect : List.of("mysql", "mysql8", "postgres", "sqlite")) {
            FakeSemanticService svc = new FakeSemanticService();
            svc.stub("A", "SELECT id FROM ta");
            ComposedSql sql = compile(derived, svc,
                    Map.of("A", CompileTestHelpers.emptyBinding()),
                    dialect);
            if (params == null) {
                params = sql.getParams();
            } else {
                assertEquals(params, sql.getParams(),
                        "params drifted across dialects at " + dialect);
            }
        }
    }
}
