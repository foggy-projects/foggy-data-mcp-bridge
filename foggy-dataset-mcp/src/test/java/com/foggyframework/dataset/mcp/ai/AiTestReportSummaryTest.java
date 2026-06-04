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
    @DisplayName("应暴露最终通过用例里的中间工具业务错误")
    @SuppressWarnings("unchecked")
    void build_shouldExposeToolBusinessErrorsFromSuccessfulToolCalls() {
        ToolCallCollector.ToolCallRecord record = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.describe_model_internal")
                .springToolName("dataset_describe_model_internal")
                .arguments(Map.of("model", "ProductInfoModel"))
                .result(Map.of(
                        "code", 600,
                        "exCode", "B600",
                        "msg", "获取模型描述失败: 资源ProductInfoModel.qm不存在"
                ))
                .success(true)
                .durationMs(5)
                .timestamp(Instant.now())
                .sequence(1)
                .build();

        SpringAiTestExecutor.AiTestResult result = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("QUERY-002")
                .provider("spring-ai")
                .modelName("gpt-oss-120b-medium")
                .success(true)
                .question("各商品销售额")
                .toolCallRecords(List.of(record))
                .durationMs(100)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(result));

        assertEquals(1, summary.get("toolBusinessErrorCount"));
        assertEquals(1, summary.get("toolBusinessErrorCaseCount"));
        assertEquals(2, summary.get("warningCount"));
        assertEquals(1, summary.get("warningCaseCount"));
        assertEquals(1, summary.get("toolBusinessErrorWarningCount"));
        assertEquals(Map.of("tool_business_error", 1L, "unknown_model_probe", 1L),
                summary.get("warningCategories"));
        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) summary.get("warnings");
        assertEquals(2, warnings.size());
        assertEquals("tool_business_error", warnings.get(0).get("warningType"));
        assertEquals("warning", warnings.get(0).get("severity"));
        assertEquals("ProductInfoModel", warnings.get(0).get("argumentModel"));
        assertEquals("unknown_model_probe", warnings.get(1).get("warningType"));
        assertEquals("ProductInfoModel", warnings.get(1).get("argumentModel"));
        List<Map<String, Object>> rootErrors =
                (List<Map<String, Object>>) summary.get("toolBusinessErrors");
        assertEquals(1, rootErrors.size());
        assertEquals("QUERY-002", rootErrors.get(0).get("testCaseId"));
        assertEquals("dataset.describe_model_internal", rootErrors.get(0).get("toolName"));
        assertEquals(600, rootErrors.get(0).get("code"));
        assertEquals("B600", rootErrors.get(0).get("exCode"));
        assertEquals("ProductInfoModel", rootErrors.get(0).get("argumentModel"));

        List<Map<String, Object>> cases = (List<Map<String, Object>>) summary.get("cases");
        assertEquals(1, cases.get(0).get("toolBusinessErrorCount"));
        assertEquals(2, cases.get(0).get("warningCount"));
        List<Map<String, Object>> caseWarnings =
                (List<Map<String, Object>>) cases.get(0).get("warnings");
        assertEquals("tool_business_error", caseWarnings.get(0).get("warningType"));
        List<Map<String, Object>> caseErrors =
                (List<Map<String, Object>>) cases.get(0).get("toolBusinessErrors");
        assertEquals("toolCall#1", caseErrors.get(0).get("source"));
        assertEquals(1, caseErrors.get(0).get("sequence"));
        assertEquals(5L, caseErrors.get(0).get("durationMs"));

        List<Map<String, Object>> models = (List<Map<String, Object>>) summary.get("models");
        assertEquals(1L, models.get(0).get("toolBusinessErrorCount"));
        assertEquals(1L, models.get(0).get("toolBusinessErrorCaseCount"));
        assertEquals(2L, models.get(0).get("warningCount"));
        assertEquals(1L, models.get(0).get("warningCaseCount"));
        assertEquals(100.0, (Double) models.get(0).get("warningRate"), 0.001);
        assertEquals(1L, models.get(0).get("toolBusinessErrorWarningCount"));

        List<Map<String, Object>> comparison = (List<Map<String, Object>>) summary.get("caseComparison");
        List<Map<String, Object>> comparedModels =
                (List<Map<String, Object>>) comparison.get(0).get("models");
        assertEquals(1, comparedModels.get(0).get("toolBusinessErrorCount"));
        assertEquals(2, comparedModels.get(0).get("warningCount"));
    }

    @Test
    @DisplayName("应识别重复 describe 同一模型的 warning")
    @SuppressWarnings("unchecked")
    void build_shouldExposeRepeatedDescribeModelWarning() {
        ToolCallCollector.ToolCallRecord first = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.describe_model_internal")
                .springToolName("dataset_describe_model_internal")
                .arguments(Map.of("model", "SalesOrderModel"))
                .result(Map.of("code", 200, "data", Map.of("name", "SalesOrderModel")))
                .success(true)
                .durationMs(4)
                .timestamp(Instant.now())
                .sequence(1)
                .build();
        ToolCallCollector.ToolCallRecord second = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.describe_model_internal")
                .springToolName("dataset_describe_model_internal")
                .arguments(Map.of("model", "SalesOrderModel"))
                .result(Map.of("code", 200, "data", Map.of("name", "SalesOrderModel")))
                .success(true)
                .durationMs(6)
                .timestamp(Instant.now())
                .sequence(2)
                .build();

        SpringAiTestExecutor.AiTestResult result = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("QUERY-RETRY")
                .provider("spring-ai")
                .modelName("gemini-pro-agent")
                .success(true)
                .toolCallRecords(List.of(first, second))
                .durationMs(100)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(result));

        assertEquals(0, summary.get("toolBusinessErrorCount"));
        assertEquals(1, summary.get("warningCount"));
        assertEquals(Map.of("model_describe_retry", 1L), summary.get("warningCategories"));
        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) summary.get("warnings");
        assertEquals("model_describe_retry", warnings.get(0).get("warningType"));
        assertEquals("SalesOrderModel", warnings.get(0).get("argumentModel"));
        assertEquals(2, warnings.get(0).get("describeCallCount"));
        assertEquals(List.of(1, 2), warnings.get(0).get("sequences"));
        assertEquals(List.of("toolCall#1", "toolCall#2"), warnings.get(0).get("sources"));
    }

    @Test
    @DisplayName("应识别成功工具调用的空结果 warning")
    @SuppressWarnings("unchecked")
    void build_shouldExposeEmptyToolResultWarning() {
        ToolCallCollector.ToolCallRecord record = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of("model", "SalesOrderModel"))
                .result(null)
                .success(true)
                .durationMs(9)
                .timestamp(Instant.now())
                .sequence(3)
                .build();

        SpringAiTestExecutor.AiTestResult result = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("QUERY-EMPTY")
                .provider("spring-ai")
                .modelName("gemini-3-flash")
                .success(true)
                .toolCallRecords(List.of(record))
                .durationMs(100)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(result));

        assertEquals(1, summary.get("warningCount"));
        assertEquals(Map.of("empty_tool_result", 1L), summary.get("warningCategories"));
        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) summary.get("warnings");
        assertEquals("empty_tool_result", warnings.get(0).get("warningType"));
        assertEquals("toolCall#3", warnings.get(0).get("source"));
        assertEquals("dataset.query_model", warnings.get(0).get("toolName"));
        assertEquals("SalesOrderModel", warnings.get(0).get("argumentModel"));
    }

    @Test
    @DisplayName("应区分 JSON 解析失败和普通工具调用失败 warning")
    @SuppressWarnings("unchecked")
    void build_shouldExposeToolCallFailureWarnings() {
        ToolCallCollector.ToolCallRecord parseError = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of("model", "SalesOrderModel"))
                .result(null)
                .error("JSON_PARSE_ERROR: unexpected token")
                .success(false)
                .durationMs(12)
                .timestamp(Instant.now())
                .sequence(4)
                .build();
        ToolCallCollector.ToolCallRecord executionError = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of("model", "SalesOrderModel"))
                .result(null)
                .error("QUERY_MODEL_FAILED: missing field")
                .success(false)
                .durationMs(15)
                .timestamp(Instant.now())
                .sequence(5)
                .build();

        SpringAiTestExecutor.AiTestResult result = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("QUERY-FAILURE")
                .provider("spring-ai")
                .modelName("gemini-pro-agent")
                .success(true)
                .toolCallRecords(List.of(parseError, executionError))
                .durationMs(100)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(result));

        assertEquals(2, summary.get("warningCount"));
        assertEquals(Map.of("tool_result_parse_error", 1L, "tool_call_failure", 1L),
                summary.get("warningCategories"));
        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) summary.get("warnings");
        assertEquals("tool_result_parse_error", warnings.get(0).get("warningType"));
        assertEquals("JSON_PARSE_ERROR: unexpected token", warnings.get(0).get("error"));
        assertEquals(4, warnings.get(0).get("sequence"));
        assertEquals("tool_call_failure", warnings.get(1).get("warningType"));
        assertEquals("QUERY_MODEL_FAILED: missing field", warnings.get(1).get("error"));
        assertEquals(5, warnings.get(1).get("sequence"));
    }

    @Test
    @DisplayName("不应把 code=200 的 RX 包装结果识别为工具业务错误")
    void toolBusinessErrors_shouldIgnoreSuccessfulRxWrapper() {
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

        assertTrue(AiTestReportSummary.toolBusinessErrors(result).isEmpty());
        Map<String, Object> summary = AiTestReportSummary.build(List.of(result));
        assertEquals(0, summary.get("warningCount"));
        assertEquals(0, summary.get("warningCaseCount"));
        assertEquals(Map.of(), summary.get("warningCategories"));
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
    @DisplayName("应暴露 query_model payload shape 分歧")
    @SuppressWarnings("unchecked")
    void build_shouldExposeQueryPayloadShapeDivergence() {
        ToolCallCollector.ToolCallRecord aggregateFilter = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "SalesOrderModel",
                        "mode", "execute",
                        "payload", Map.of(
                                "columns", List.of("store$caption", "salesDate$year", "sum(salesAmount) as totalSales"),
                                "slice", List.of(Map.of(
                                        "field", "salesDate$year",
                                        "op", "in",
                                        "value", List.of(2024, 2025)
                                )),
                                "having", List.of(Map.of(
                                        "field", "totalSales",
                                        "op", ">",
                                        "value", 500
                                )),
                                "groupBy", List.of(
                                        Map.of("field", "store$id"),
                                        Map.of("field", "store$caption"),
                                        Map.of("field", "salesDate$year")
                                ),
                                "orderBy", List.of("-totalSales"),
                                "limit", 3
                        )
                ))
                .result(Map.of("code", 200))
                .success(true)
                .durationMs(12)
                .timestamp(Instant.now())
                .sequence(2)
                .build();
        ToolCallCollector.ToolCallRecord detailFilter = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "SalesOrderModel",
                        "mode", "execute",
                        "payload", Map.of(
                                "columns", List.of("store$caption", "salesDate$year", "sum(salesAmount) as totalSales"),
                                "slice", List.of(
                                        Map.of("field", "salesDate$year", "op", "in", "value", List.of(2024, 2025)),
                                        Map.of("field", "salesAmount", "op", ">", "value", 500)
                                ),
                                "groupBy", List.of(
                                        Map.of("field", "store$id"),
                                        Map.of("field", "store$caption"),
                                        Map.of("field", "salesDate$year")
                                ),
                                "orderBy", List.of(Map.of("field", "totalSales", "dir", "desc")),
                                "limit", 3
                        )
                ))
                .result(Map.of("code", 200))
                .success(true)
                .durationMs(11)
                .timestamp(Instant.now())
                .sequence(2)
                .build();

        SpringAiTestExecutor.AiTestResult gpt = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("COMPLEX-001")
                .provider("spring-ai")
                .modelName("gpt-oss-120b-medium")
                .success(true)
                .question("查询2024、2025年销售金额超过500的记录，按门店、年分组统计总销售额")
                .toolCallRecords(List.of(aggregateFilter))
                .durationMs(100)
                .build();
        SpringAiTestExecutor.AiTestResult gemini = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("COMPLEX-001")
                .provider("spring-ai")
                .modelName("gemini-3-flash")
                .success(true)
                .question("查询2024、2025年销售金额超过500的记录，按门店、年分组统计总销售额")
                .toolCallRecords(List.of(detailFilter))
                .durationMs(100)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(gpt, gemini));

        assertEquals(1, summary.get("warningCount"));
        assertEquals(Map.of("query_payload_shape_divergence", 1L), summary.get("warningCategories"));
        List<Map<String, Object>> cases = (List<Map<String, Object>>) summary.get("cases");
        assertEquals(1, cases.get(0).get("queryPayloadCount"));
        List<Map<String, Object>> firstPayloads =
                (List<Map<String, Object>>) cases.get(0).get("queryPayloads");
        assertEquals(List.of("salesDate$year"), firstPayloads.get(0).get("sliceFields"));
        assertEquals(List.of("totalSales"), firstPayloads.get(0).get("havingFields"));
        List<Map<String, Object>> secondPayloads =
                (List<Map<String, Object>>) cases.get(1).get("queryPayloads");
        assertEquals(List.of("salesDate$year", "salesAmount"), secondPayloads.get(0).get("sliceFields"));
        assertEquals(List.of(), secondPayloads.get(0).get("havingFields"));

        List<Map<String, Object>> comparison = (List<Map<String, Object>>) summary.get("caseComparison");
        assertEquals("mixed", comparison.get(0).get("queryPayloadShapeConsensus"));
        assertEquals("semantic", comparison.get(0).get("queryPayloadShapeDivergenceClass"));
        assertEquals(2, comparison.get(0).get("queryPayloadCount"));
        assertEquals(2, comparison.get(0).get("queryPayloadShapeSignatureCount"));
        assertEquals(2, comparison.get(0).get("queryPayloadSemanticSignatureCount"));
        List<Map<String, Object>> signatures =
                (List<Map<String, Object>>) comparison.get(0).get("queryPayloadShapeSignatures");
        assertEquals(2, signatures.size());

        List<Map<String, Object>> comparedModels =
                (List<Map<String, Object>>) comparison.get(0).get("models");
        assertEquals(1, comparedModels.get(0).get("queryPayloadCount"));
        assertEquals(1, comparedModels.get(1).get("queryPayloadCount"));
        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) summary.get("warnings");
        assertEquals("query_payload_shape_divergence", warnings.get(0).get("warningType"));
        assertEquals("semantic", warnings.get(0).get("queryPayloadShapeDivergenceClass"));
        assertEquals("comparison", warnings.get(0).get("provider"));
    }

    @Test
    @DisplayName("应将别名、排序、limit 和冗余 ID 分组差异分类为良性 payload shape 分歧")
    @SuppressWarnings("unchecked")
    void build_shouldClassifyBenignQueryPayloadShapeDivergence() {
        ToolCallCollector.ToolCallRecord baseline = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "SalesOrderModel",
                        "mode", "execute",
                        "payload", Map.of(
                                "columns", List.of("store$caption", "salesAmount"),
                                "groupBy", List.of("store$caption"),
                                "limit", 20
                        )
                ))
                .result(Map.of("code", 200))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(1)
                .build();
        ToolCallCollector.ToolCallRecord llm = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "SalesOrderModel",
                        "payload", Map.of(
                                "columns", List.of("store$id", "store$caption", "sum(salesAmount) as totalSales"),
                                "groupBy", List.of(
                                        Map.of("field", "store$id"),
                                        Map.of("field", "store$caption")
                                ),
                                "orderBy", List.of(Map.of("field", "totalSales", "dir", "desc")),
                                "limit", 100
                        )
                ))
                .result(Map.of("code", 200))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(1)
                .build();

        SpringAiTestExecutor.AiTestResult direct = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("AGG-STORE")
                .provider("direct")
                .modelName("tool-execution")
                .success(true)
                .question("按门店统计销售额")
                .toolCallRecords(List.of(baseline))
                .durationMs(10)
                .build();
        SpringAiTestExecutor.AiTestResult gemini = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("AGG-STORE")
                .provider("spring-ai")
                .modelName("gemini-3-flash")
                .success(true)
                .question("按门店统计销售额")
                .toolCallRecords(List.of(llm))
                .durationMs(10)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(direct, gemini));

        assertEquals(1, summary.get("warningCount"));
        assertEquals(Map.of("benign_query_payload_shape_divergence", 1L), summary.get("warningCategories"));

        List<Map<String, Object>> comparison = (List<Map<String, Object>>) summary.get("caseComparison");
        assertEquals("mixed", comparison.get(0).get("queryPayloadShapeConsensus"));
        assertEquals("benign", comparison.get(0).get("queryPayloadShapeDivergenceClass"));
        assertEquals(2, comparison.get(0).get("queryPayloadShapeSignatureCount"));
        assertEquals(1, comparison.get(0).get("queryPayloadSemanticSignatureCount"));

        List<Map<String, Object>> semanticSignatures =
                (List<Map<String, Object>>) comparison.get(0).get("queryPayloadSemanticSignatures");
        assertEquals(1, semanticSignatures.size());
        assertEquals(List.of("store$caption"), semanticSignatures.get(0).get("groupBy"));

        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) summary.get("warnings");
        assertEquals("benign_query_payload_shape_divergence", warnings.get(0).get("warningType"));
        assertEquals("info", warnings.get(0).get("severity"));
        assertEquals("benign", warnings.get(0).get("queryPayloadShapeDivergenceClass"));
    }

    @Test
    @DisplayName("应将 OR 等值条件和 IN 条件的等价差异分类为良性 payload shape 分歧")
    @SuppressWarnings("unchecked")
    void build_shouldClassifyOrEqualsAndInConditionShapeAsBenign() {
        ToolCallCollector.ToolCallRecord baseline = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "mode", "execute",
                        "payload", Map.of(
                                "columns", List.of("store$caption", "salesAmount", "salesDate$year"),
                                "slice", List.of(
                                        Map.of("field", "salesAmount", "op", ">", "value", 500),
                                        Map.of("$or", List.of(
                                                Map.of("field", "salesDate$year", "op", "=", "value", 2024),
                                                Map.of("field", "salesDate$year", "op", "=", "value", 2025)
                                        ))
                                ),
                                "groupBy", List.of("store$caption", "salesDate$year"),
                                "orderBy", List.of(Map.of("column", "salesAmount", "direction", "DESC")),
                                "limit", 3
                        )
                ))
                .result(Map.of("code", 200))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(1)
                .build();
        ToolCallCollector.ToolCallRecord llm = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("store$id", "store$caption", "salesDate$year",
                                        "sum(salesAmount) as totalSales"),
                                "slice", List.of(
                                        Map.of("field", "salesDate$year", "op", "in", "value", List.of(2024, 2025)),
                                        Map.of("field", "salesAmount", "op", ">", "value", 500)
                                ),
                                "groupBy", List.of(
                                        Map.of("field", "store$id"),
                                        Map.of("field", "store$caption"),
                                        Map.of("field", "salesDate$year")
                                ),
                                "orderBy", List.of(Map.of("field", "totalSales", "dir", "desc")),
                                "limit", 3
                        )
                ))
                .result(Map.of("code", 200))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(1)
                .build();

        SpringAiTestExecutor.AiTestResult direct = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("COMPLEX-001")
                .provider("direct")
                .modelName("tool-execution")
                .success(true)
                .question("查询2024、2025年销售金额超过500的记录，按门店、年分组统计总销售额")
                .toolCallRecords(List.of(baseline))
                .durationMs(10)
                .build();
        SpringAiTestExecutor.AiTestResult gemini = SpringAiTestExecutor.AiTestResult.builder()
                .testCaseId("COMPLEX-001")
                .provider("spring-ai")
                .modelName("gemini-3-flash")
                .success(true)
                .question("查询2024、2025年销售金额超过500的记录，按门店、年分组统计总销售额")
                .toolCallRecords(List.of(llm))
                .durationMs(10)
                .build();

        Map<String, Object> summary = AiTestReportSummary.build(List.of(direct, gemini));

        assertEquals(1, summary.get("warningCount"));
        assertEquals(Map.of("benign_query_payload_shape_divergence", 1L), summary.get("warningCategories"));

        List<Map<String, Object>> cases = (List<Map<String, Object>>) summary.get("cases");
        List<Map<String, Object>> directPayloads =
                (List<Map<String, Object>>) cases.get(0).get("queryPayloads");
        assertEquals(List.of("salesAmount", "salesDate$year"), directPayloads.get(0).get("sliceFields"));
        assertEquals(List.of("salesAmount|>|500", "salesDate$year|in|[2024,2025]"),
                directPayloads.get(0).get("sliceConditions"));

        List<Map<String, Object>> comparison = (List<Map<String, Object>>) summary.get("caseComparison");
        assertEquals("benign", comparison.get(0).get("queryPayloadShapeDivergenceClass"));
        assertEquals(2, comparison.get(0).get("queryPayloadShapeSignatureCount"));
        assertEquals(1, comparison.get(0).get("queryPayloadSemanticSignatureCount"));

        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) summary.get("warnings");
        assertEquals("benign_query_payload_shape_divergence", warnings.get(0).get("warningType"));
        assertEquals("info", warnings.get(0).get("severity"));
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
