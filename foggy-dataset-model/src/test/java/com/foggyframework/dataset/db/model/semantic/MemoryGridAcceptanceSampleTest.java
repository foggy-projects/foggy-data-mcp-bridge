package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MemoryGridAcceptanceSampleTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();

    @Test
    @DisplayName("third-011 Memory Grid sample accepts two governed result handles")
    void third011AcceptsGovernedResultHandles() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", memoryGridPlan(third011Plan()), SemanticRequestContext.empty());

        assertEquals("MEMORY_GRID", response.getExecution().getRoute());
        assertEquals("PLAN_READY", response.getExecution().getStatus());
        assertNotNull(response.getExecution().getMemoryGridValidation());
        assertEquals(500, response.getExecution().getMemoryGridValidation().get("output_limit"));
    }

    @Test
    @DisplayName("third-009 Memory Grid sample accepts actual and target governed handles")
    void third009AcceptsActualAndTargetHandles() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", memoryGridPlan(third009Plan()), SemanticRequestContext.empty());

        assertEquals("MEMORY_GRID", response.getExecution().getRoute());
        assertEquals("PLAN_READY", response.getExecution().getStatus());
        assertNotNull(response.getExecution().getMemoryGridValidation());
        assertEquals(200, response.getExecution().getMemoryGridValidation().get("output_limit"));
    }

    private SemanticQueryRequest memoryGridPlan(Map<String, Object> plan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("MEMORY_GRID");
        request.setMemoryGridPlan(plan);
        return request;
    }

    private Map<String, Object> third011Plan() {
        return Map.of(
                "inputs", List.of(
                        Map.of(
                                "name", "sales_by_customer",
                                "source_route", "DSL_CTE",
                                "result_handle", "dsl_cte_result_sales_by_customer_90d",
                                "model", "SaleOrder",
                                "grain", List.of("customer.name"),
                                "filters", List.of(Map.of("field", "orderDate", "op", "last_n_days", "value", 90)),
                                "metrics", List.of(Map.of("name", "salesAmount", "expr", "sum(amount)")),
                                "row_limit", 500,
                                "governed", true
                        ),
                        Map.of(
                                "name", "ar_by_customer",
                                "source_route", "DSL_CTE",
                                "result_handle", "dsl_cte_result_ar_by_customer_90d",
                                "model", "ArInvoice",
                                "grain", List.of("customer.name"),
                                "filters", List.of(Map.of("field", "invoiceDate", "op", "last_n_days", "value", 90)),
                                "metrics", List.of(Map.of("name", "unpaidAmount", "expr", "sum(unpaidAmount)")),
                                "row_limit", 500,
                                "governed", true
                        )
                ),
                "join", Map.of("keys", List.of("customer.name"), "type", "full_outer"),
                "derived", List.of(Map.of("name", "salesArGap", "expr", "salesAmount - unpaidAmount")),
                "output_limit", 500
        );
    }

    private Map<String, Object> third009Plan() {
        return Map.of(
                "inputs", List.of(
                        Map.of(
                                "name", "actual_by_team",
                                "source_route", "DSL_CTE",
                                "result_handle", "dsl_cte_result_actual_by_team_2026_05",
                                "model", "SaleOrder",
                                "grain", List.of("salesTeam.name"),
                                "filters", List.of(Map.of("field", "orderDate", "op", "month", "value", "2026-05")),
                                "metrics", List.of(Map.of("name", "actualSalesAmount", "expr", "sum(amount)")),
                                "row_limit", 200,
                                "governed", true
                        ),
                        Map.of(
                                "name", "target_by_team",
                                "source_route", "DSL",
                                "result_handle", "dsl_result_target_by_team_2026_05_approved",
                                "model", "SalesTarget",
                                "grain", List.of("salesTeam.name"),
                                "filters", List.of(
                                        Map.of("field", "targetMonth", "op", "=", "value", "2026-05"),
                                        Map.of("field", "targetVersion", "op", "=", "value", "approved")
                                ),
                                "metrics", List.of(Map.of("name", "targetSalesAmount", "expr", "sum(targetSalesAmount)")),
                                "row_limit", 200,
                                "governed", true
                        )
                ),
                "join", Map.of("keys", List.of("salesTeam.name"), "type", "inner"),
                "derived", List.of(Map.of("name", "targetAchievementRate", "expr", "actualSalesAmount / targetSalesAmount")),
                "output_limit", 200
        );
    }
}
