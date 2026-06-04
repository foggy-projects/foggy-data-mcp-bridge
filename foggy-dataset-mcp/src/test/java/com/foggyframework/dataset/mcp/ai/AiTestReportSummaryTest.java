package com.foggyframework.dataset.mcp.ai;

import com.foggyframework.dataset.mcp.schema.DatasetNLQueryResponse;
import com.foggyframework.dataset.mcp.service.ToolCallCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AI test report summary")
class AiTestReportSummaryTest {

    @Test
    @DisplayName("应从直接工具结果中提取澄清 template match 元数据")
    @SuppressWarnings("unchecked")
    void build_shouldExposeClarifyMetadataFromDirectToolResult() {
        DatasetNLQueryResponse response = DatasetNLQueryResponse.builder()
                .type("clarify")
                .code("ROUTING_TERMINAL_CLARIFY")
                .missing(List.of("time_range"))
                .detail(clarifyDetail())
                .build();

        SpringAiTestExecutor.AiTestResult result = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("CLARIFY-001")
                .provider("direct")
                .modelName("tool-execution")
                .success(true)
                .question("最近新增工单怎么定义")
                .toolResult(response)
                .durationMs(12)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(result));

        Map<String, Object> clarify = (Map<String, Object>) summary.get("clarify");
        assertEquals(1, clarify.get("caseCount"));
        assertEquals(List.of("service_order"), clarify.get("domains"));
        assertEquals(List.of("needs_time_range"), clarify.get("riskTypes"));
        assertEquals(List.of("service_order_created_at_missing_time"), clarify.get("ownerRules"));
        assertEquals(List.of("time_range"), clarify.get("missingSlots"));

        List<Map<String, Object>> cases = (List<Map<String, Object>>) summary.get("cases");
        List<Map<String, Object>> observations =
                (List<Map<String, Object>>) cases.get(0).get("clarifyObservability");
        assertEquals("toolResult", observations.get(0).get("source"));
        assertEquals("CLARIFY", observations.get(0).get("terminalRoute"));
    }

    @Test
    @DisplayName("应从 tool call 的 RX 包装结果中提取澄清可观测性")
    @SuppressWarnings("unchecked")
    void clarifyObservability_shouldExtractFromToolCallRxWrapper() {
        Map<String, Object> wrappedResult = new LinkedHashMap<>();
        wrappedResult.put("code", 200);
        wrappedResult.put("data", Map.of(
                "type", "clarify",
                "code", "ROUTING_TERMINAL_CLARIFY",
                "detail", clarifyDetail()
        ));

        ToolCallCollector.ToolCallRecord record = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .result(wrappedResult)
                .success(true)
                .durationMs(7)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        SpringAiTestExecutor.AiTestResult result = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("CLARIFY-002")
                .provider("spring-ai")
                .modelName("gemini-pro-agent")
                .success(true)
                .toolCallRecords(List.of(record))
                .build();

        List<Map<String, Object>> observations = AiTestReportSummary.clarifyObservability(result);

        assertEquals(1, observations.size());
        assertEquals("toolCall#0", observations.get(0).get("source"));
        assertEquals("dataset.query_model", observations.get(0).get("toolName"));
        assertEquals(List.of("service_order"), observations.get(0).get("domains"));
        assertEquals(List.of("time_range"), observations.get(0).get("missingSlots"));
    }

    @Test
    @DisplayName("应按 case 输出跨模型对比和失败分类")
    @SuppressWarnings("unchecked")
    void build_shouldExposeCaseComparisonAndFailureCategories() {
        SpringAiTestExecutor.AiTestResult passed = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("QUERY-001")
                .provider("spring-ai")
                .modelName("gemini-pro-agent")
                .success(true)
                .question("查询销售额")
                .durationMs(100)
                .build();
        SpringAiTestExecutor.AiTestResult failed = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("QUERY-001")
                .provider("spring-ai")
                .modelName("claude-sonnet")
                .success(false)
                .question("查询销售额")
                .validationResult(ResultValidator.ValidationResult.failure("QUERY-001", "missing required column"))
                .durationMs(150)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(passed, failed));

        Map<String, Object> failureCategories = (Map<String, Object>) summary.get("failureCategories");
        assertEquals(1L, failureCategories.get("success"));
        assertEquals(1L, failureCategories.get("validation_failed"));

        List<Map<String, Object>> models = (List<Map<String, Object>>) summary.get("models");
        Map<String, Object> failedModel = models.stream()
                .filter(model -> "spring-ai/claude-sonnet".equals(model.get("model")))
                .findFirst()
                .orElseThrow();
        assertEquals(0L, failedModel.get("clarifyCaseCount"));
        assertEquals(Map.of("validation_failed", 1L), failedModel.get("failureCategories"));

        List<Map<String, Object>> comparison = (List<Map<String, Object>>) summary.get("caseComparison");
        assertEquals(1, comparison.size());
        assertEquals("QUERY-001", comparison.get(0).get("testCaseId"));
        assertEquals("mixed", comparison.get(0).get("consensus"));
        assertEquals(1L, comparison.get(0).get("passedCount"));
        assertEquals(1L, comparison.get(0).get("failedCount"));

        List<Map<String, Object>> comparedModels =
                (List<Map<String, Object>>) comparison.get(0).get("models");
        assertEquals(List.of("spring-ai/gemini-pro-agent", "spring-ai/claude-sonnet"),
                comparedModels.stream().map(model -> model.get("model")).toList());
        assertEquals("validation_failed", comparedModels.get(1).get("errorCategory"));
    }

    @Test
    @DisplayName("应从 validation 错误中识别数据库不可用")
    @SuppressWarnings("unchecked")
    void build_shouldClassifyDatabaseUnavailableValidationFailure() {
        SpringAiTestExecutor.AiTestResult failed = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("QUERY-DB")
                .provider("direct")
                .modelName("tool-execution")
                .success(false)
                .validationResult(ResultValidator.ValidationResult.failure("QUERY-DB",
                        "Query returned error: Communications link failure"))
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(failed));
        Map<String, Object> failureCategories = (Map<String, Object>) summary.get("failureCategories");

        assertEquals(1L, failureCategories.get("validation_failed:database_unavailable"));
    }

    private static Map<String, Object> clarifyDetail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("terminal_route", "CLARIFY");
        detail.put("clarify_missing_slots", List.of("time_range"));
        detail.put("clarify_template_matches", List.of(Map.of(
                "domain", "service_order",
                "riskType", "needs_time_range",
                "ownerRule", "service_order_created_at_missing_time"
        )));
        return detail;
    }
}
