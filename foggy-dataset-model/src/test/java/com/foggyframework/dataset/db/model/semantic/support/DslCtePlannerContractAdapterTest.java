package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslCtePlannerContractAdapterTest {

    @Test
    @DisplayName("L1 relation planner contract adapts to Java DSL_CTE executable plan")
    void adaptsRelationPlannerContractToExecutablePlan() {
        Map<String, Object> plannerOutput = relationPlannerOutput();

        Map<String, Object> executablePlan = DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput);
        Map<String, Object> validation = DslCtePlanValidator.validate(executablePlan);

        assertEquals(List.of("product.categoryName", "categorySalesAmount",
                "companySalesAmount", "categoryShare"), validation.get("output"));
        @SuppressWarnings("unchecked")
        List<String> stageTypes = (List<String>) validation.get("stage_types");
        assertTrue(stageTypes.containsAll(List.of("aggregate", "derive", "orderBy")));
    }

    @Test
    @DisplayName("L1 relation planner contract adapts to SemanticQueryRequest")
    void adaptsRelationPlannerContractToQueryRequest() {
        SemanticQueryRequest request = DslCtePlannerContractAdapter.toQueryRequest(relationPlannerOutput());

        assertEquals("DSL_CTE", request.getRoute());
        assertEquals("PLAN_READY", request.getStatus());
        assertNotNull(request.getExecutablePlan());
        DslCtePlanValidator.validate(request.getExecutablePlan());
    }

    @Test
    @DisplayName("Adapter copies planner stages defensively")
    @SuppressWarnings("unchecked")
    void copiesPlannerStagesDefensively() {
        Map<String, Object> plannerOutput = relationPlannerOutput();
        Map<String, Object> executablePlan = DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput);

        List<Map<String, Object>> originalStages = (List<Map<String, Object>>) plannerOutput.get("stages");
        originalStages.get(0).put("name", "mutated");

        Map<String, Object> ctePlan = (Map<String, Object>) executablePlan.get("cte_plan");
        List<Map<String, Object>> adaptedStages = (List<Map<String, Object>>) ctePlan.get("stages");
        assertEquals("category_sales", adaptedStages.get(0).get("name"));
    }

    @Test
    @DisplayName("Adapter rejects non-ready L1 planner output")
    void rejectsNonReadyPlannerOutput() {
        Map<String, Object> plannerOutput = relationPlannerOutput();
        plannerOutput.put("status", "DEFERRED");
        plannerOutput.put("execution_surface", "NONE");
        plannerOutput.put("selected_template", "none");
        plannerOutput.put("l3_validation", "FAIL_CLOSED");
        plannerOutput.put("stages", List.of());
        plannerOutput.put("output", List.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput));

        assertTrue(ex.getMessage().contains(DslCtePlannerContractAdapter.CONTRACT_UNSUPPORTED));
        assertTrue(ex.getMessage().contains("status must be PLAN_READY"));
    }

    @Test
    @DisplayName("Adapter rejects unsupported templates before Java preflight")
    void rejectsUnsupportedTemplate() {
        Map<String, Object> plannerOutput = relationPlannerOutput();
        plannerOutput.put("selected_template", "source_cohort_target_month@v1");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput));

        assertTrue(ex.getMessage().contains(DslCtePlannerContractAdapter.CONTRACT_UNSUPPORTED));
        assertTrue(ex.getMessage().contains("selected_template"));
    }

    @Test
    @DisplayName("Adapter rejects missing final output contract")
    void rejectsMissingOutput() {
        Map<String, Object> plannerOutput = relationPlannerOutput();
        plannerOutput.put("output", List.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput));

        assertTrue(ex.getMessage().contains(DslCtePlannerContractAdapter.CONTRACT_INVALID));
        assertTrue(ex.getMessage().contains("output must be a non-empty string list"));
    }

    @Test
    @DisplayName("Adapter rejects non-object stages")
    void rejectsNonObjectStage() {
        Map<String, Object> plannerOutput = relationPlannerOutput();
        plannerOutput.put("stages", List.of("bad-stage"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput));

        assertTrue(ex.getMessage().contains(DslCtePlannerContractAdapter.CONTRACT_INVALID));
        assertTrue(ex.getMessage().contains("stages[0] must be an object"));
    }

    @Test
    @DisplayName("Java preflight rejects planner output fields not produced by final stage")
    void preflightRejectsUnavailableOutput() {
        Map<String, Object> plannerOutput = relationPlannerOutput();
        plannerOutput.put("output", List.of("product.categoryName", "amount", "categoryShare"));

        Map<String, Object> executablePlan = DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> DslCtePlanValidator.validate(executablePlan));

        assertTrue(ex.getMessage().contains(DslCtePlanValidator.STAGE_INVALID));
        assertTrue(ex.getMessage().contains("cte_plan.output references unavailable field 'amount'"));
    }

    @Test
    @DisplayName("Java preflight rejects expression orderBy after planner adapter")
    @SuppressWarnings("unchecked")
    void preflightRejectsExpressionOrderBy() {
        Map<String, Object> plannerOutput = relationPlannerOutput();
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plannerOutput.get("stages");
        stages.get(2).put("orderBy", List.of(m("expr", "categoryShare + 1", "dir", "DESC")));

        Map<String, Object> executablePlan = DslCtePlannerContractAdapter.toExecutablePlan(plannerOutput);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> DslCtePlanValidator.validate(executablePlan));

        assertTrue(ex.getMessage().contains(DslCtePlanValidator.STAGE_INVALID));
        assertTrue(ex.getMessage().contains("orderBy stage does not support expression orderBy"));
    }

    private Map<String, Object> relationPlannerOutput() {
        return m(
                "business_question", "按产品品类计算销售占比并取 Top 3",
                "planner", "dsl_cte_planner",
                "status", "PLAN_READY",
                "execution_surface", "DSL_CTE",
                "selected_template", "relation_result_derive@v1",
                "required_contract_fields", List.of(
                        "source_model=SaleOrder",
                        "groupBy=product.categoryName",
                        "stage=aggregate(categorySalesAmount=sum(amount), companySalesAmount=sum(amount) over all)",
                        "stage=derive(categoryShare=categorySalesAmount / NULLIF(companySalesAmount, 0))",
                        "stage=orderBy(categoryShare DESC), TopN=3"
                ),
                "missing_context", List.of(),
                "risk_flags", List.of("post_filter_required"),
                "stages", List.of(
                        stage("category_sales", "aggregate",
                                "input", m("model", "SaleOrder"),
                                "groupBy", List.of("product.categoryName"),
                                "metrics", List.of(
                                        m("name", "categorySalesAmount", "expr", "sum(amount)"),
                                        m("name", "companySalesAmount", "expr", "sum(amount) over all")
                                )),
                        stage("category_share", "derive",
                                "inputs", List.of("category_sales"),
                                "derived", List.of(m(
                                        "name", "categoryShare",
                                        "expr", "categorySalesAmount / NULLIF(companySalesAmount, 0)"
                                ))),
                        stage("top_categories", "orderBy",
                                "inputs", List.of("category_share"),
                                "orderBy", List.of(m("field", "categoryShare", "dir", "DESC")),
                                "limit", 3)
                ),
                "output", List.of("product.categoryName", "categorySalesAmount",
                        "companySalesAmount", "categoryShare"),
                "l3_validation", "ATTEMPT",
                "reason", "relation result derive with governed output schema and result-stage ordering"
        );
    }

    private Map<String, Object> stage(String name, String type, Object... entries) {
        Map<String, Object> result = m("name", name, "type", type);
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    private Map<String, Object> m(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }
}
