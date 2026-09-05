package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticTerminalPlanTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();

    @Test
    @DisplayName("CLARIFY terminal returns structured execution response without compiling")
    void clarifyTerminalReturnsStructuredExecutionResponse() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("CLARIFY");
        request.setRiskFlags(List.of("needs_business_rule", "needs_metric_definition"));
        request.setClarifyingQuestions(List.of("请确认 SLA 分母和阈值。"));
        request.setWhy(List.of("缺少可执行业务口径。"));

        SemanticQueryResponse response = service.queryModel(
                "AnyModel", request, "execute", SemanticRequestContext.empty());

        assertEquals(List.of(), response.getItems());
        assertEquals("CLARIFY", response.getExecution().getStatus());
        assertEquals("CLARIFY", response.getExecution().getRoute());
        assertEquals(List.of("needs_business_rule", "needs_metric_definition"),
                response.getExecution().getRiskFlags());
        assertEquals(List.of("请确认 SLA 分母和阈值。"),
                response.getExecution().getClarifyingQuestions());
        assertNull(response.getExecution().getExecutablePlan());
        assertEquals("CLARIFY_TERMINAL", response.getExecution().getErrorCode());
        assertTrue(response.getSemantic().getShouldAnswerDirectly());
    }

    @Test
    @DisplayName("REJECT terminal rejects executable_plan payload")
    void rejectTerminalRejectsExecutablePlanPayload() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("REJECT");
        request.setExecutablePlan(Map.of("route", "DSL"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("AnyModel", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("TERMINAL_PLAN_MUST_NOT_HAVE_EXECUTABLE_PLAN"));
    }

    @Test
    @DisplayName("CLARIFY and REJECT terminal plans do not enter SQL generation")
    void terminalPlansDoNotEnterSqlGeneration() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setStatus("REJECT");
        request.setRiskFlags(List.of("unsupported_physical_sql", "governance_risk"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.generateSql("AnyModel", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("TERMINAL_PLAN_NOT_EXECUTABLE"));
    }

    @Test
    @DisplayName("terminal response preserves structured input warnings separately from legacy warnings")
    void terminalResponseShouldPreserveStructuredInputWarnings() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("CLARIFY");
        request.setQueryInputWarnings(List.of(new QueryInputWarning(
                "UNKNOWN_QUERY_PROPERTY_IGNORED",
                "$.groupBy[0].grain",
                "Unknown Query DSL property 'grain' was ignored.",
                "Use a model-defined time grain field.",
                false,
                Map.of("property", "grain"))));

        SemanticQueryResponse response = service.queryModel(
                "AnyModel", request, "execute", SemanticRequestContext.empty());

        assertEquals(1, response.getQueryInputWarnings().size());
        assertEquals("$.groupBy[0].grain", response.getQueryInputWarnings().get(0).path());
        assertEquals(List.of(), response.getWarnings());
    }
}
