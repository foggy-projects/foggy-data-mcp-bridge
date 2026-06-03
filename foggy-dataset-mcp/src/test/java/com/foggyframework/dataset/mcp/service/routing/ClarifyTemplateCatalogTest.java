package com.foggyframework.dataset.mcp.service.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Clarify template catalog")
class ClarifyTemplateCatalogTest {

    private final ClarifyTemplateCatalog catalog = ClarifyTemplateCatalog.loadDefault();

    @Test
    @DisplayName("keywordGroups 应按整组关键词命中服务工单 SLA 模板")
    void matchingQuestions_shouldMatchServiceTicketSlaKeywordGroup() {
        List<String> questions = catalog.matchingQuestions("统计客服工单 SLA 超 48 小时未响应的占比", List.of());
        List<String> missingSlots = catalog.matchingMissingSlots("统计客服工单 SLA 超 48 小时未响应的占比", List.of())
                .stream()
                .map(ClarifyTemplateCatalog.MissingSlot::slot)
                .toList();

        assertTrue(questions.stream().anyMatch(question -> question.contains("SLA 达成率定义")));
        assertTrue(questions.stream().anyMatch(question -> question.contains("目标响应时限")));
        assertTrue(missingSlots.contains("sla_definition"));
        assertTrue(missingSlots.contains("target_response_threshold"));
    }

    @Test
    @DisplayName("keywordGroups 不应被单个宽泛关键词触发")
    void matchingQuestions_shouldNotMatchKeywordGroupPartially() {
        List<String> questions = catalog.matchingQuestions("统计最近一周客服工单总数", List.of());
        List<String> missingSlots = catalog.matchingMissingSlots("统计最近一周客服工单总数", List.of())
                .stream()
                .map(ClarifyTemplateCatalog.MissingSlot::slot)
                .toList();

        assertFalse(questions.stream().anyMatch(question -> question.contains("SLA 达成率定义")));
        assertFalse(missingSlots.contains("sla_definition"));
    }

    @Test
    @DisplayName("ruleSignals 应直接命中对应模板")
    void matchingQuestions_shouldMatchRuleSignal() {
        List<String> questions = catalog.matchingQuestions("", List.of("missing_funnel_definition"));
        List<String> missingSlots = catalog.matchingMissingSlots("", List.of("missing_funnel_definition"))
                .stream()
                .map(ClarifyTemplateCatalog.MissingSlot::slot)
                .toList();

        assertTrue(questions.stream().anyMatch(question -> question.contains("漏斗阶段定义")));
        assertTrue(missingSlots.contains("funnel_stage_definition"));
    }
}
