package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.Dsl;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseSubqueryLoweringTest {

    private static ComposedSql compile(QueryPlan plan,
                                       SemanticQueryServiceV3 svc,
                                       Map<String, ModelBinding> bindings,
                                       String dialect) {
        return compile(plan, svc, bindings, dialect, null);
    }

    private static ComposedSql compile(QueryPlan plan,
                                       SemanticQueryServiceV3 svc,
                                       Map<String, ModelBinding> bindings,
                                       String dialect,
                                       Map<String, Optional<String>> datasourceIds) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect(dialect)
                        .datasourceIds(datasourceIds)
                        .build());
    }

    @Test
    @DisplayName("base slice QueryPlan value lowers to SQL subquery for IN and NOT IN")
    void baseSliceQueryPlanValueLowersToSqlSubquery() {
        for (String op : List.of("in", "not in")) {
            FakeSemanticService svc = new FakeSemanticService();
            svc.stub("CurrentM", "SELECT id, amount FROM current_tbl");
            svc.stub("PriorM", "SELECT id FROM prior_tbl WHERE flag = ?", 100);
            BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
            BaseModelPlan current = BaseModelPlan.builder()
                    .model("CurrentM")
                    .columns(List.of("id", "amount"))
                    .slice(List.of(Map.of("field", "id", "op", op, "value", prior)))
                    .build();

            ComposedSql sql = compile(current, svc,
                    Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                            "PriorM", CompileTestHelpers.emptyBinding()),
                    "sqlite");

            assertTrue(sql.getSql().contains(op.toUpperCase() + " (SELECT"));
            assertTrue(sql.getSql().contains("prior_tbl"));
            assertTrue(sql.getSql().contains("IS NOT NULL)"));
            assertEquals(List.of(100), sql.getParams());
            assertTrue(svc.invocations.get(0).request.getSlice() == null
                    || svc.invocations.get(0).request.getSlice().isEmpty());
        }
    }

    @Test
    @DisplayName("base subquery LHS uses semantic physical field expression when available")
    void baseSubqueryLhsUsesSemanticPhysicalFieldExpression() {
        FakeSemanticService fake = new FakeSemanticService();
        fake.stub("CurrentM",
                "SELECT t1.customer_id \"customer$id\" FROM current_tbl t1 WHERE t1.status = ? GROUP BY t1.customer_id",
                "paid");
        fake.stub("PriorM", "SELECT customer_id \"customer$id\" FROM prior_tbl WHERE flag = ?", 100);
        ResolvingSemanticService svc = new ResolvingSemanticService(
                fake,
                Map.of("CurrentM:customer$id", "t1.customer_id"));
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "customer$id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("customer$id"))
                .slice(List.of(Map.of("field", "customer$id", "op", "not in", "value", prior)))
                .groupBy(List.of("customer$id"))
                .build();

        ComposedSql sql = compile(current, svc,
                Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                        "PriorM", CompileTestHelpers.emptyBinding()),
                "sqlite");

        assertTrue(sql.getSql().contains("t1.customer_id NOT IN (SELECT"));
        assertFalse(sql.getSql().contains("\"customer$id\" NOT IN"));
        assertIterableEquals(List.of("paid", 100), sql.getParams());
    }

    @Test
    @DisplayName("base slice explicit subquery(plan, field) lowers multi-column RHS")
    void baseSliceExplicitSubqueryFieldLowersMultiColumnPlan() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT id, amount FROM current_tbl");
        svc.stub("PriorM", "SELECT id, name FROM prior_tbl");
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id", "name");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id", "amount"))
                .slice(List.of(Map.of(
                        "field", "id",
                        "op", "not in",
                        "value", Dsl.subquery(prior, "id"))))
                .build();

        ComposedSql sql = compile(current, svc,
                Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                        "PriorM", CompileTestHelpers.emptyBinding()),
                "sqlite");

        assertTrue(sql.getSql().contains("id NOT IN (SELECT"));
        assertTrue(sql.getSql().contains("prior_tbl"));
        assertTrue(sql.getSql().contains("IS NOT NULL)"));
    }

    @Test
    @DisplayName("base subquery params are merged at SQL injection point")
    void baseSubqueryParamsMergeAtInjectionPoint() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM",
                "SELECT id FROM current_tbl WHERE status = ? GROUP BY ROUND(amount, ?)",
                "paid", 2);
        svc.stub("PriorM", "SELECT id FROM prior_tbl WHERE flag = ?", 100);
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id"))
                .slice(List.of(Map.of("field", "id", "op", "in", "value", prior)))
                .build();

        ComposedSql sql = compile(current, svc,
                Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                        "PriorM", CompileTestHelpers.emptyBinding()),
                "sqlite");

        assertTrue(sql.getSql().contains("status = ? AND id IN"));
        assertTrue(sql.getSql().contains("\nGROUP BY"));
        assertIterableEquals(List.of("paid", 100, 2), sql.getParams());
    }

    @Test
    @DisplayName("base subquery WHERE is inserted before GROUP BY when no WHERE exists")
    void baseSubqueryWhereInsertedBeforeGroupBy() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT ROUND(amount, ?) AS bucket FROM current_tbl GROUP BY ROUND(amount, ?)", 1, 1);
        svc.stub("PriorM", "SELECT id FROM prior_tbl WHERE flag = ?", 100);
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("bucket"))
                .slice(List.of(Map.of("field", "id", "op", "in", "value", prior)))
                .build();

        ComposedSql sql = compile(current, svc,
                Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                        "PriorM", CompileTestHelpers.emptyBinding()),
                "sqlite");

        assertTrue(sql.getSql().contains("\nWHERE id IN"));
        assertTrue(sql.getSql().contains("\nGROUP BY"));
        assertIterableEquals(List.of(1, 100, 1), sql.getParams());
    }

    @Test
    @DisplayName("base subquery rejects implicit multi-column RHS")
    void baseSubqueryImplicitMultiColumnPlanRequiresSubqueryField() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT id FROM current_tbl");
        svc.stub("PriorM", "SELECT id, name FROM prior_tbl");
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id", "name");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id"))
                .slice(List.of(Map.of("field", "id", "op", "in", "value", prior)))
                .build();

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(current, svc,
                        Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                                "PriorM", CompileTestHelpers.emptyBinding()),
                        "sqlite"));
        assertTrue(ex.getMessage().contains("COMPOSE_SUBQUERY_FIELD_AMBIGUOUS"));
    }

    @Test
    @DisplayName("base subquery rejects explicit missing RHS field")
    void baseSubqueryExplicitFieldMustExist() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT id FROM current_tbl");
        svc.stub("PriorM", "SELECT id FROM prior_tbl");
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id"))
                .slice(List.of(Map.of(
                        "field", "id",
                        "op", "in",
                        "value", Dsl.subquery(prior, "missing"))))
                .build();

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(current, svc,
                        Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                                "PriorM", CompileTestHelpers.emptyBinding()),
                        "sqlite"));
        assertTrue(ex.getMessage().contains("COMPOSE_SUBQUERY_FIELD_NOT_FOUND"));
    }

    @Test
    @DisplayName("base subquery compound slice fails closed")
    void baseSubqueryCompoundSliceFailsClosed() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT id FROM current_tbl");
        svc.stub("PriorM", "SELECT id FROM prior_tbl");
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id"))
                .slice(List.of(Map.of("$and", List.of(
                        Map.of("field", "id", "op", "in", "value", prior)))))
                .build();

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(current, svc,
                        Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                                "PriorM", CompileTestHelpers.emptyBinding()),
                        "sqlite"));
        assertTrue(ex.getMessage().contains(QueryPlan.SUBQUERY_VALUE_UNSUPPORTED_CODE));
    }

    @Test
    @DisplayName("base subquery LHS field respects fieldAccess fail-closed")
    void baseSubqueryLhsFieldAccessFailsClosed() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT id FROM current_tbl");
        svc.stub("PriorM", "SELECT id FROM prior_tbl");
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id"))
                .slice(List.of(Map.of("field", "id", "op", "in", "value", prior)))
                .build();

        ModelBinding restricted = ModelBinding.builder()
                .fieldAccess(List.of("amount"))
                .build();
        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(current, svc,
                        Map.of("CurrentM", restricted,
                                "PriorM", CompileTestHelpers.emptyBinding()),
                        "sqlite"));
        assertTrue(ex.getMessage().contains("COMPOSE_SUBQUERY_FIELD_NOT_FOUND"));
    }

    @Test
    @DisplayName("base subquery rejects cross-datasource RHS plan")
    void baseSubqueryCrossDatasourceRejected() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT id FROM current_tbl");
        svc.stub("PriorM", "SELECT id FROM prior_tbl");
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id"))
                .slice(List.of(Map.of("field", "id", "op", "not in", "value", prior)))
                .build();

        ComposeCompileException ex = assertThrows(ComposeCompileException.class,
                () -> compile(current, svc,
                        Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                                "PriorM", CompileTestHelpers.emptyBinding()),
                        "sqlite",
                        Map.of("CurrentM", Optional.of("mysql_main"),
                                "PriorM", Optional.of("pg_analytics"))));

        assertEquals(ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
        assertTrue(ex.getMessage().contains("mysql_main"));
        assertTrue(ex.getMessage().contains("pg_analytics"));
    }

    @Test
    @DisplayName("base having QueryPlan value remains fail-closed")
    void baseHavingQueryPlanValueFailsClosed() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("CurrentM", "SELECT id FROM current_tbl");
        svc.stub("PriorM", "SELECT id FROM prior_tbl");
        BaseModelPlan prior = CompileTestHelpers.base("PriorM", "id");
        BaseModelPlan current = BaseModelPlan.builder()
                .model("CurrentM")
                .columns(List.of("id"))
                .having(List.of(Map.of("field", "id", "op", "in", "value", prior)))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> compile(current, svc,
                        Map.of("CurrentM", CompileTestHelpers.emptyBinding(),
                                "PriorM", CompileTestHelpers.emptyBinding()),
                        "sqlite"));
        assertTrue(ex.getMessage().contains(QueryPlan.SUBQUERY_VALUE_UNSUPPORTED_CODE));
        assertFalse(ex.getMessage().contains("unhashable type"));
    }

    private static final class ResolvingSemanticService implements SemanticQueryServiceV3 {
        private final FakeSemanticService delegate;
        private final Map<String, String> expressions;

        private ResolvingSemanticService(FakeSemanticService delegate, Map<String, String> expressions) {
            this.delegate = delegate;
            this.expressions = expressions;
        }

        @Override
        public Optional<String> resolveFieldSqlExpression(String model, String field, String namespace) {
            return Optional.ofNullable(expressions.get(model + ":" + field));
        }

        @Override
        public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                String model,
                SemanticQueryRequest request,
                SemanticRequestContext context) {
            return delegate.generateSql(model, request, context);
        }

        @Override
        public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
            return delegate.executeSql(sql, params, routeModel);
        }

        @Override
        public SemanticQueryResponse queryModel(
                String model,
                SemanticQueryRequest request,
                String mode,
                SemanticRequestContext context) {
            return delegate.queryModel(model, request, mode, context);
        }

        @Override
        public SemanticQueryResponse validateQuery(
                String model,
                SemanticQueryRequest request,
                SemanticRequestContext context) {
            return delegate.validateQuery(model, request, context);
        }
    }
}
