package com.foggyframework.dataset.model.engine.compose.runtime;

import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.model.semantic.port.ComposeSqlExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeSqlGeneration;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M7 unit tests for {@link PlanExecution}.
 *
 * @since 8.2.0.beta
 */
@DisplayName("PlanExecution · Plan → rows 执行单元测试")
class PlanExecutionTest {

    /** Builds a minimal context with a resolver that auto-approves everything. */
    private static ComposeQueryContext ctx() {
        AuthorityResolver resolver = request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String name : request.modelNames()) {
                bindings.put(name, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("u1").tenantId("t1").roles(List.of("analyst")).build())
                .namespace("ns1")
                .traceId("trace-1")
                .authorityResolver(resolver)
                .build();
    }

    /**
     * Abstract base for test-only SemanticQueryServiceV3 implementations.
     * Subclasses only need to override {@code generateSql} and {@code executeSql}.
     */
    private static abstract class TestSemanticService implements SemanticQueryServiceV3 {
        @Override
        public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    @DisplayName("pickRouteModel returns first base model name")
    void pickRouteModel_firstBase() {
        QueryPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("amount"))
                .build();
        assertEquals("SaleOrderQM", PlanExecution.pickRouteModel(plan));
    }

    @Test
    @DisplayName("executePlan wraps DB errors with proper prefix")
    void executePlan_dbError_wrapsWithPrefix() {
        SemanticQueryServiceV3 fakeSvc = new TestSemanticService() {
            @Override
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT amount FROM sale_order", List.of(), null);
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                throw new RuntimeException("executeSql failed: connection refused");
            }
        };

        QueryPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("amount"))
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> PlanExecution.executePlan(plan, ctx(), fakeSvc, "mysql"));
        assertTrue(ex.getMessage().contains("Plan execution failed at execute phase:"),
                "Expected execute-phase prefix, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("executePlan success returns rows from executeSql")
    void executePlan_success_returnsRows() {
        List<Map<String, Object>> expectedRows = List.of(
                Map.of("amount", 100),
                Map.of("amount", 200)
        );

        SemanticQueryServiceV3 fakeSvc = new TestSemanticService() {
            @Override
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT amount FROM sale_order", List.of(), null);
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                return expectedRows;
            }
        };

        QueryPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("amount"))
                .build();

        List<Map<String, Object>> result = PlanExecution.executePlan(plan, ctx(), fakeSvc, "mysql");
        assertEquals(expectedRows, result);
    }

    @Test
    @DisplayName("narrow planning/execution ports execute without legacy semantic service")
    void executePlan_narrowPorts_returnsRows() {
        List<Map<String, Object>> expectedRows = List.of(Map.of("amount", 300));
        ComposeSemanticPlanningPort planningPort = (model, request, context) ->
                new ComposeSqlGeneration(
                        "SELECT amount FROM sale_order WHERE state = ?",
                        List.of("paid"), List.of(), Map.of());
        ComposeSqlExecutionPort executionPort = (sql, params, routeModel) -> {
            assertTrue(sql.contains("sale_order"));
            assertEquals(List.of("paid"), params);
            assertEquals("SaleOrderQM", routeModel);
            return expectedRows;
        };
        QueryPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("amount"))
                .build();

        List<Map<String, Object>> result = PlanExecution.executePlan(
                plan, ctx(), planningPort, executionPort, "mysql");

        assertEquals(expectedRows, result);
    }

    @Test
    @DisplayName("QueryPlan.execute without bundle throws RuntimeException")
    void queryPlanExecute_noBundle_throws() {
        QueryPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("amount"))
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, plan::execute);
        assertTrue(ex.getMessage().contains("requires an ambient ComposeRuntimeBundle"));
    }

    @Test
    @DisplayName("QueryPlan.toSql without bundle throws RuntimeException")
    void queryPlanToSql_noBundle_throws() {
        QueryPlan plan = BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("amount"))
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, plan::toSql);
        assertTrue(ex.getMessage().contains("requires either an explicit ctx or an ambient ComposeRuntimeBundle"));
    }
}
