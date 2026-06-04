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
