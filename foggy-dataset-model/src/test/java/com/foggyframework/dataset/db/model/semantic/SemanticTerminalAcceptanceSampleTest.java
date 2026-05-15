package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticTerminalAcceptanceSampleTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();

    @Test
    @DisplayName("CLARIFY acceptance samples return terminal responses without compiling")
    void clarifySamplesReturnTerminalResponses() {
        assertTerminal(sample(
                "CLARIFY",
                List.of("needs_business_rule", "needs_time_range", "needs_metric_definition"),
                List.of("请确认统计时间范围、SLA 达成率公式和分母，以及超 48 小时未响应的判定规则。"),
                List.of("缺少可执行的 SLA 指标定义。")
        ));
        assertTerminal(sample(
                "CLARIFY",
                List.of("needs_business_rule", "needs_metric_definition"),
                List.of("请定义响应质量不达标的指标、阈值和计算公式。"),
                List.of("响应质量不是已声明的可执行指标。")
        ));
        assertTerminal(sample(
                "CLARIFY",
                List.of("needs_business_rule", "needs_metric_definition", "needs_time_range"),
                List.of("请定义线索质量指标、统计时间范围、分母和阈值。"),
                List.of("线索质量缺少业务规则和指标公式。")
        ));
    }

    @Test
    @DisplayName("REJECT acceptance samples return terminal responses without compiling")
    void rejectSamplesReturnTerminalResponses() {
        assertTerminal(sample(
                "REJECT",
                List.of("needs_business_rule", "needs_metric_definition", "unsupported_causality"),
                List.of(),
                List.of("问题要求解释目标达成率下降的主要原因，超出现有事实查询和执行计划边界。")
        ));
        assertTerminal(sample(
                "REJECT",
                List.of("unsupported_physical_sql", "governance_risk"),
                List.of(),
                List.of("请求直接访问并 join 物理业务表，绕过虚拟语义模型治理。")
        ));
        assertTerminal(sample(
                "REJECT",
                List.of("unsupported_physical_sql", "governance_risk"),
                List.of(),
                List.of("请求直接 join 物理表，绕过虚拟语义模型治理。")
        ));
    }

    private void assertTerminal(SemanticQueryRequest request) {
        SemanticQueryResponse response = service.queryModel(
                "AnyModel", request, "execute", SemanticRequestContext.empty());

        assertEquals(List.of(), response.getItems());
        assertEquals(request.getRoute(), response.getExecution().getRoute());
        assertEquals(request.getRoute(), response.getExecution().getStatus());
        assertEquals(request.getRiskFlags(), response.getExecution().getRiskFlags());
        assertEquals(request.getClarifyingQuestions(), response.getExecution().getClarifyingQuestions());
        assertEquals(request.getWhy(), response.getExecution().getWhy());
        assertNull(response.getExecution().getExecutablePlan());
        assertEquals(request.getRoute() + "_TERMINAL", response.getExecution().getErrorCode());
        assertTrue(response.getSemantic().getShouldAnswerDirectly());
    }

    private SemanticQueryRequest sample(String route, List<String> riskFlags,
                                        List<String> clarifyingQuestions, List<String> why) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute(route);
        request.setStatus(route);
        request.setRiskFlags(riskFlags);
        request.setClarifyingQuestions(clarifyingQuestions);
        request.setWhy(why);
        return request;
    }
}
