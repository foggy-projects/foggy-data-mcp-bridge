package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryRequest;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryResponse;
import com.foggyframework.dataset.mcp.service.routing.RoutingCalibrationAction;
import com.foggyframework.dataset.mcp.service.routing.RoutingCalibrationActionResolver;
import com.foggyframework.dataset.mcp.service.routing.RoutingCalibrationActionType;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ProgressEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryExpertService routing calibration guard")
class QueryExpertServiceRoutingCalibrationTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private DatasetAccessor datasetAccessor;

    @Mock
    private McpProperties mcpProperties;

    @Mock
    private McpToolDispatcher mcpToolDispatcher;

    @Mock
    private McpToolCallbackFactory toolCallbackFactory;

    private QueryExpertService queryExpertService;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void setUp() {
        when(datasetAccessor.getAccessMode()).thenReturn("mock");
        queryExpertService = new QueryExpertService(
                chatClientBuilder,
                datasetAccessor,
                mcpProperties,
                new ObjectMapper(),
                mcpToolDispatcher,
                toolCallbackFactory,
                new RoutingCalibrationActionResolver()
        );
    }

    @Test
    @DisplayName("缺少 calibrated_route 的 replan guard 不应进入 LLM/工具链")
    void blockedGuard_shouldNotCallLlmOrTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("查询客户销售额")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routing_calibration_guard", Map.of(
                                        "raw_route", "SEMANTIC_SQL",
                                        "requires_replan", true,
                                        "execution_allowed", false
                                )
                        ))
                        .build())
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-1", null);

        assertEquals("error", response.getType());
        assertEquals("ROUTING_REPLAN_REQUIRED", response.getCode());
        assertNotNull(response.getDetail());
        assertNotNull(response.getDebug());
        assertEquals("trace-1", response.getDebug().get("trace_id"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("route 变化的 replan guard 应按校准路由进入 LLM/工具链重新调度")
    void replanGuardWithCalibratedRoute_shouldRedispatchByCalibratedRoute() {
        DatasetNLQueryRequest request = replanRequestWithCalibratedRoute();
        mockLlmToolDispatch("trace-replan-1");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-replan-1", null);

        assertEquals("result", response.getType());
        assertEquals(1L, response.getTotal());
        assertEquals("已按校准路由重新查询", response.getSummary());
        assertNull(response.getCode());

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientBuilder).defaultSystem(anyString());
        verify(mcpToolDispatcher, times(3)).getTool(anyString());
        verify(toolCallbackFactory).createToolCallbacks(
                anyList(),
                eq("trace-replan-1"),
                isNull(),
                any(ToolCallCollector.class)
        );
        verifyPromptChain(userMessageCaptor);
        String userMessage = userMessageCaptor.getValue();
        assertTrue(userMessage.contains("[路由校准守卫]"));
        assertTrue(userMessage.contains("禁止复用旧 route 的计划"));
        assertTrue(userMessage.contains("原始路由: CLARIFY"));
        assertTrue(userMessage.contains("校准后路由: MEMORY_GRID"));
        assertTrue(userMessage.contains("必须按校准后路由重新规划"));

        assertNotNull(response.getDebug());
        assertEquals("trace-replan-1", response.getDebug().get("trace_id"));
        Map<String, Object> dispatch = (Map<String, Object>) response.getDebug().get("routing_replan_dispatch");
        assertEquals("MEMORY_GRID", dispatch.get("route"));
        assertEquals("CLARIFY", dispatch.get("raw_route"));
        assertEquals(true, dispatch.get("blocked_stale_plan"));
        assertEquals(true, dispatch.get("allowed_after_replan"));
        assertEquals(true, dispatch.get("dispatched"));
        assertEquals("actual_calibrated_route", dispatch.get("dispatch_mode"));
        assertTrue(((List<String>) dispatch.get("stale_plan_fields")).contains("tool_calls"));

        Map<String, Object> routing = (Map<String, Object>) response.getDebug().get("routing_calibration");
        assertEquals("REPLAN_REQUIRED", routing.get("action"));
        assertEquals("CLARIFY", routing.get("raw_route"));
        assertEquals("MEMORY_GRID", routing.get("calibrated_route"));
        assertEquals(true, routing.get("requires_replan"));
        assertEquals(false, routing.get("execution_allowed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("流式入口遇到 replan guard 应发出重新调度事件并继续 LLM/工具链")
    void replanGuardWithProgress_shouldRedispatchByCalibratedRoute() {
        DatasetNLQueryRequest request = replanRequestWithCalibratedRoute();
        mockLlmToolDispatch("trace-replan-stream-1");

        List<ProgressEvent> events = queryExpertService
                .processQueryWithProgress(request, "trace-replan-stream-1", null)
                .collectList()
                .block();

        assertNotNull(events);
        assertEquals(6, events.size());
        assertEquals("progress", events.get(0).getEventType());
        assertEquals("progress", events.get(1).getEventType());
        assertEquals("complete", events.get(5).getEventType());

        Map<String, Object> data = (Map<String, Object>) events.get(1).getData();
        assertEquals("routing_replan", data.get("phase"));
        assertEquals(15, data.get("percent"));
        assertTrue(String.valueOf(data.get("message")).contains("校准后路由"));
        assertEquals("REPLAN_BY_CALIBRATED_ROUTE", data.get("recommended_action"));
        assertEquals(true, data.get("replan_required"));
        Map<String, Object> dispatch = (Map<String, Object>) data.get("replan_dispatch");
        assertEquals("MEMORY_GRID", dispatch.get("route"));
        assertEquals(true, dispatch.get("blocked_stale_plan"));
        Map<String, Object> debug = (Map<String, Object>) data.get("debug");
        assertEquals("trace-replan-stream-1", debug.get("trace_id"));
        Map<String, Object> routing = (Map<String, Object>) debug.get("routing_calibration");
        assertEquals("REPLAN_REQUIRED", routing.get("action"));
        assertEquals("MEMORY_GRID", routing.get("calibrated_route"));
        assertEquals(false, routing.get("execution_allowed"));

        DatasetNLQueryResponse result = (DatasetNLQueryResponse) events.get(5).getData();
        assertEquals("result", result.getType());
        Map<String, Object> resultDispatch = (Map<String, Object>) result.getDebug().get("routing_replan_dispatch");
        assertEquals(true, resultDispatch.get("dispatched"));
        assertEquals("MEMORY_GRID", resultDispatch.get("route"));

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verifyPromptChain(userMessageCaptor);
        assertTrue(userMessageCaptor.getValue().contains("校准后路由: MEMORY_GRID"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("replan 重新调度未产生结构化结果时应返回 clarify 而不是 info")
    void replanGuardWithoutStructuredResult_shouldClarify() {
        DatasetNLQueryRequest request = replanRequestWithCalibratedRoute();
        mockLlmToolDispatchWithoutQueryResult("trace-replan-clarify-1");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-replan-clarify-1", null);

        assertEquals("clarify", response.getType());
        assertNotNull(response.getQuestions());
        assertTrue(response.getQuestions().get(0).contains("未产生可验收的结构化查询结果"));
        Map<String, Object> candidates = (Map<String, Object>) response.getCandidates();
        assertEquals("info", candidates.get("original_type"));
        assertEquals("MEMORY_GRID", candidates.get("calibrated_route"));

        Map<String, Object> dispatch = (Map<String, Object>) response.getDebug().get("routing_replan_dispatch");
        assertEquals(true, dispatch.get("dispatched"));
        assertEquals("MEMORY_GRID", dispatch.get("route"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("replan 重新调度产生澄清响应时应保留 clarify")
    void replanGuardWithClarifyResponse_shouldKeepClarify() {
        DatasetNLQueryRequest request = replanRequestWithCalibratedRoute();
        mockLlmToolDispatchWithContent("trace-replan-clarify-2", "需要确认金额区间后再查询。");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-replan-clarify-2", null);

        assertEquals("clarify", response.getType());
        assertTrue(response.getQuestions().get(0).contains("需要确认"));
        Map<String, Object> dispatch = (Map<String, Object>) response.getDebug().get("routing_replan_dispatch");
        assertEquals(true, dispatch.get("dispatched"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("replan guard 应写入响应 debug 作为可观测证据")
    void replanGuard_shouldAttachResponseDebugEvidence() {
        DatasetNLQueryResponse response = DatasetNLQueryResponse.info("ai_analysis", Map.of(), "AI 分析完成");
        RoutingCalibrationAction action = new RoutingCalibrationAction(
                RoutingCalibrationActionType.REPLAN_REQUIRED,
                "SEMANTIC_SQL",
                "DSL",
                java.util.List.of("needs_sql"),
                java.util.List.of(),
                java.util.List.of("dsl_audit_boundary"),
                true,
                false,
                "route-changing calibration requires fresh planning by calibrated route"
        );

        DatasetNLQueryResponse result = QueryExpertService.attachRoutingCalibrationDebug(response, action, "trace-2");

        assertSame(response, result);
        assertNotNull(result.getDebug());
        assertEquals("trace-2", result.getDebug().get("trace_id"));
        Map<String, Object> routing = (Map<String, Object>) result.getDebug().get("routing_calibration");
        assertEquals("REPLAN_REQUIRED", routing.get("action"));
        assertEquals("SEMANTIC_SQL", routing.get("raw_route"));
        assertEquals("DSL", routing.get("calibrated_route"));
        assertEquals(true, routing.get("requires_replan"));
        assertEquals(false, routing.get("execution_allowed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("非阻断查询应写入 query_trace 关联 catalog/tool/query result")
    void nonBlockedQuery_shouldAttachQueryTraceDebug() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("查询订单列表")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        McpTool listModelsTool = mockTool("dataset.list_models");
        McpTool describeModelTool = mockTool("dataset.describe_model_internal");
        McpTool queryModelTool = mockTool("dataset.query_model");
        when(mcpToolDispatcher.getTool("dataset.list_models")).thenReturn(listModelsTool);
        when(mcpToolDispatcher.getTool("dataset.describe_model_internal")).thenReturn(describeModelTool);
        when(mcpToolDispatcher.getTool("dataset.query_model")).thenReturn(queryModelTool);
        when(toolCallbackFactory.createToolCallbacks(
                anyList(),
                eq("trace-nonblocked-1"),
                isNull(),
                any(ToolCallCollector.class)
        )).thenAnswer(invocation -> {
            ToolCallCollector collector = invocation.getArgument(3);
            collector.recordToolCall(
                    "dataset.list_models",
                    "dataset_list_models",
                    Map.of(),
                    Map.of("models", List.of("FactOrderQueryModel")),
                    null,
                    3
            );
            Map<String, Object> queryResult = Map.of(
                    "items", List.of(Map.of("orderId", "SO-1")),
                    "total", 1
            );
            collector.recordToolCall(
                    "dataset.query_model",
                    "dataset_query_model",
                    Map.of(
                            "model", "FactOrderQueryModel",
                            "mode", "execute",
                            "payload", Map.of(
                                    "columns", List.of("orderId"),
                                    "limit", 20
                            )
                    ),
                    queryResult,
                    null,
                    5
            );
            QueryExpertService.captureQueryResult(queryResult);
            return new ToolCallback[0];
        });
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("查询完成");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-nonblocked-1", null);

        assertEquals("result", response.getType());
        assertEquals(1L, response.getTotal());
        assertNotNull(response.getDebug());
        assertEquals("trace-nonblocked-1", response.getDebug().get("trace_id"));
        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("trace-nonblocked-1", queryTrace.get("trace_id"));
        assertEquals("result", queryTrace.get("result_type"));
        assertEquals(3, queryTrace.get("registered_tool_count"));
        assertEquals(2, queryTrace.get("tool_call_count"));
        assertEquals(2L, queryTrace.get("tool_success_count"));
        assertEquals(0L, queryTrace.get("tool_failure_count"));
        assertEquals(true, queryTrace.get("all_tools_success"));
        assertEquals(true, queryTrace.get("query_result_captured"));
        assertEquals(1, queryTrace.get("query_result_total"));
        assertEquals(1, queryTrace.get("query_result_item_count"));

        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) queryTrace.get("tool_calls");
        assertEquals("dataset.list_models", toolCalls.get(0).get("tool_name"));
        assertEquals("dataset.query_model", toolCalls.get(1).get("tool_name"));
        Map<String, Object> arguments = (Map<String, Object>) toolCalls.get(1).get("arguments_summary");
        assertEquals("FactOrderQueryModel", arguments.get("model"));
        assertEquals(List.of("orderId"), arguments.get("payload_columns"));
        assertEquals(20, arguments.get("payload_limit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("query_model 工具失败且无结构化结果时应返回 error 而不是 info")
    void queryModelFailureWithoutStructuredResult_shouldReturnErrorContract() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("查询订单金额大于 1000 元的客户")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(
                anyList(),
                eq("trace-query-failed-1"),
                isNull(),
                any(ToolCallCollector.class)
        )).thenAnswer(invocation -> {
            ToolCallCollector collector = invocation.getArgument(3);
            RX<Object> failed = RX.failB("Table \"FACT_ORDER\" not found");
            collector.recordToolCall(
                    "dataset.query_model",
                    "dataset_query_model",
                    Map.of(
                            "model", "FactOrderQueryModel",
                            "mode", "execute",
                            "payload", Map.of(
                                    "columns", List.of("customer$caption", "amount"),
                                    "slice", List.of(Map.of("name", "amount", "type", ">", "value", 1000))
                            )
                    ),
                    failed,
                    "[QUERY_MODEL_FAILED] Table \"FACT_ORDER\" not found",
                    7
            );
            QueryExpertService.captureQueryResult(failed);
            return new ToolCallback[0];
        });
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("底层数据库报错，表 FACT_ORDER 不存在。");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-query-failed-1", null);

        assertEquals("error", response.getType());
        assertEquals("QUERY_MODEL_FAILED", response.getCode());
        assertTrue(response.getMsg().contains("dataset.query_model 执行失败"));
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("dataset.query_model", detail.get("tool_name"));
        assertEquals("info", detail.get("original_type"));
        assertTrue(String.valueOf(detail.get("tool_error")).contains("FACT_ORDER"));

        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("error", queryTrace.get("result_type"));
        assertEquals(1, queryTrace.get("tool_call_count"));
        assertEquals(0L, queryTrace.get("tool_success_count"));
        assertEquals(1L, queryTrace.get("tool_failure_count"));
        assertEquals(false, queryTrace.get("all_tools_success"));
        assertEquals(false, queryTrace.get("query_result_captured"));

        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) queryTrace.get("tool_calls");
        assertEquals(false, toolCalls.get(0).get("success"));
        assertTrue(String.valueOf(toolCalls.get(0).get("error")).contains("QUERY_MODEL_FAILED"));
        Map<String, Object> resultSummary = (Map<String, Object>) toolCalls.get(0).get("result_summary");
        assertEquals("RX", resultSummary.get("type"));
        assertEquals(false, resultSummary.get("success"));
        assertTrue(String.valueOf(resultSummary.get("message")).contains("FACT_ORDER"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("无结构化结果且提示缺少模型能力时应返回 reject 而不是 info")
    void unsupportedInfoWithoutStructuredResult_shouldReturnRejectContract() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("统计客服团队超 48 小时未响应工单数量")
                .build();

        mockLlmToolDispatchWithContent(
                "trace-unsupported-info-1",
                "当前系统没有接入客服工单模型，无法统计超 48 小时未响应工单数量。"
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-unsupported-info-1", null);

        assertEquals("reject", response.getType());
        assertEquals("UNSUPPORTED_BY_CURRENT_MODEL_CATALOG", response.getCode());
        assertTrue(response.getMsg().contains("不支持"));
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("info", detail.get("original_type"));
        assertEquals("unsupported_by_current_model_catalog", detail.get("terminal_contract"));
        assertNotNull(response.getDebug());
        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("reject", queryTrace.get("result_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("无结构化结果的普通自由文本应返回 clarify 而不是 info")
    void freeformInfoWithoutStructuredResult_shouldReturnClarifyContract() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("分析客户复购情况")
                .build();

        mockLlmToolDispatchWithContent(
                "trace-freeform-info-1",
                "已完成分析，建议补充统计口径后再试。"
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-freeform-info-1", null);

        assertEquals("clarify", response.getType());
        assertNotNull(response.getQuestions());
        assertTrue(response.getQuestions().get(0).contains("没有产生可验收的结构化结果"));
        Map<String, Object> candidates = (Map<String, Object>) response.getCandidates();
        assertEquals("info", candidates.get("original_type"));
        assertEquals("clarification_required_for_unstructured_response", candidates.get("terminal_contract"));
        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("clarify", queryTrace.get("result_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("query_model 捕获应归一化 RX 语义查询结果")
    void captureQueryResult_shouldNormalizeRxSemanticQueryResponse() {
        try {
            SemanticQueryResponse semanticResponse = new SemanticQueryResponse();
            semanticResponse.setItems(List.of(Map.of("orderId", "SO-1")));
            semanticResponse.setTotal(1L);
            semanticResponse.setHasNext(false);

            QueryExpertService.captureQueryResult(RX.ok(semanticResponse));

            Map<String, Object> captured = QueryExpertService.LAST_QUERY_RESULT.get();
            assertNotNull(captured);
            assertEquals(1L, captured.get("total"));
            assertEquals(false, captured.get("hasNext"));
            List<Map<String, Object>> items = (List<Map<String, Object>>) captured.get("items");
            assertEquals("SO-1", items.get(0).get("orderId"));
        } finally {
            QueryExpertService.clearCapture();
        }
    }

    private static DatasetNLQueryRequest replanRequestWithCalibratedRoute() {
        return DatasetNLQueryRequest.builder()
                .query("查询最近订单并保留记忆表")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routing_calibration_guard", Map.of(
                                        "raw_route", "CLARIFY",
                                        "calibrated_route", "MEMORY_GRID",
                                        "requires_replan", true,
                                        "execution_allowed", false,
                                        "route_changed", true
                                )
                        ))
                        .build())
                .build();
    }

    private void mockLlmToolDispatch(String traceId) {
        mockLlmToolDispatchWithContent(traceId, "已按校准路由重新查询");
        when(callResponseSpec.content()).thenAnswer(invocation -> {
            QueryExpertService.captureQueryResult(Map.of(
                    "items", List.of(Map.of("id", 1, "route", "MEMORY_GRID")),
                    "total", 1
            ));
            return "已按校准路由重新查询";
        });
    }

    private void mockLlmToolDispatchWithoutQueryResult(String traceId) {
        mockLlmToolDispatchWithContent(traceId, "已完成分析，但没有调用结构化查询。");
    }

    private void mockLlmToolDispatchWithContent(String traceId, String content) {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(anyList(), eq(traceId), isNull(), any(ToolCallCollector.class)))
                .thenReturn(new ToolCallback[0]);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(content);
    }

    private void verifyPromptChain(ArgumentCaptor<String> userMessageCaptor) {
        verify(chatClientBuilder).build();
        verify(toolCallbackFactory).createToolCallbacks(anyList(), anyString(), isNull(), any(ToolCallCollector.class));
        verify(chatClient).prompt();
        verify(requestSpec).user(userMessageCaptor.capture());
        verify(requestSpec).toolCallbacks(any(ToolCallback[].class));
        verify(requestSpec).call();
    }

    private static McpTool mockTool(String name) {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn(name);
        return tool;
    }
}
