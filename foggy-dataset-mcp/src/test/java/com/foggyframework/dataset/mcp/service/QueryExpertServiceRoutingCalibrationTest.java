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
    @DisplayName("CLARIFY terminal guard 不应进入 LLM/工具链")
    void clarifyTerminalGuard_shouldNotCallLlmOrTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("统计本月高质量线索")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routing_calibration_guard", Map.of(
                                        "raw_route", "CLARIFY",
                                        "calibrated_route", "CLARIFY",
                                        "raw_risks", List.of("needs_metric_definition"),
                                        "calibrated_risks", List.of("needs_metric_definition"),
                                        "applied_rules", List.of("vague_lead_quality"),
                                        "execution_allowed", true
                                )
                        ))
                        .build())
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-clarify", null);

        assertEquals("clarify", response.getType());
        assertEquals("ROUTING_TERMINAL_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("CLARIFY", detail.get("terminal_route"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        Map<String, Object> routing = (Map<String, Object>) response.getDebug().get("routing_calibration");
        assertEquals("TERMINAL_ROUTE", routing.get("action"));
        assertEquals("CLARIFY", routing.get("calibrated_route"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("CLARIFY terminal guard 应按 SLA 场景生成具体澄清问题")
    void clarifyTerminalGuard_shouldAskScenarioAwareSlaQuestions() {
        DatasetNLQueryRequest request = terminalClarifyRequest(
                "统计各客服团队超 48 小时未响应工单数量和 SLA 达成率。",
                List.of("needs_business_rule", "needs_metric_definition", "needs_time_range"),
                List.of()
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-sla", null);

        assertEquals("clarify", response.getType());
        assertQuestionTextContains(response, "SLA", "达成率", "业务日历", "优先级", "时间单位");
        assertTerminalRouteWithoutTools(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("CLARIFY terminal guard 应按漏斗场景生成阶段/分母/去重澄清问题")
    void clarifyTerminalGuard_shouldAskScenarioAwareFunnelQuestions() {
        DatasetNLQueryRequest request = terminalClarifyRequest(
                "从线索到商机到订单，各阶段转化率是多少？",
                List.of("needs_business_rule", "needs_metric_definition", "needs_time_range"),
                List.of("missing_funnel_definition")
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-funnel", null);

        assertEquals("clarify", response.getType());
        assertQuestionTextContains(response, "阶段定义", "分母", "时间范围", "去重", "统计粒度");
        assertTerminalRouteWithoutTools(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("CLARIFY terminal guard 应按预算差异场景生成版本/组织/币种澄清问题")
    void clarifyTerminalGuard_shouldAskScenarioAwareBudgetQuestions() {
        DatasetNLQueryRequest request = terminalClarifyRequest(
                "本季度费用预算和实际支出差异在哪里？",
                List.of("grain_mismatch", "needs_business_rule", "needs_metric_definition"),
                List.of()
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-budget", null);

        assertEquals("clarify", response.getType());
        assertQuestionTextContains(response, "预算版本", "组织", "币种", "对比期间");
        assertTerminalRouteWithoutTools(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("CLARIFY terminal guard 应按无界明细场景生成范围/行数/导出聚合澄清问题")
    void clarifyTerminalGuard_shouldAskScenarioAwareUnboundedQuestions() {
        DatasetNLQueryRequest request = terminalClarifyRequest(
                "把所有历史订单明细都取出来后，临时按自定义金额区间重新分桶分析。",
                List.of("governance_risk", "result_size_risk"),
                List.of("unbounded_memory_governance")
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-unbounded", null);

        assertEquals("clarify", response.getType());
        assertQuestionTextContains(response, "结果范围", "最大行数", "导出明细", "聚合汇总");
        assertQuestionTextNotContains(response, "阶段定义", "转化率分母");
        assertTerminalRouteWithoutTools(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("CLARIFY terminal guard 应按敏感导出场景生成脱敏/权限/接收人澄清问题")
    void clarifyTerminalGuard_shouldAskScenarioAwareSensitiveExportQuestions() {
        DatasetNLQueryRequest request = terminalClarifyRequest(
                "导出本月下单客户的手机号、身份证号和订单金额。",
                List.of("governance_risk"),
                List.of()
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-sensitive-export", null);

        assertEquals("clarify", response.getType());
        assertQuestionTextContains(response, "脱敏", "权限", "接收人", "用途", "数据范围");
        assertQuestionTextNotContains(response, "阶段定义", "转化率分母");
        assertTerminalRouteWithoutTools(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("CLARIFY terminal guard 应按目标完成场景生成目标版本/公式/团队粒度澄清问题")
    void clarifyTerminalGuard_shouldAskScenarioAwareTargetQuestions() {
        DatasetNLQueryRequest request = terminalClarifyRequest(
                "各销售团队这个月目标完成得怎么样？",
                List.of("grain_mismatch", "needs_business_rule", "needs_metric_definition"),
                List.of("budget_or_target_ambiguity", "sales_target_version_guard")
        );

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-target", null);

        assertEquals("clarify", response.getType());
        assertQuestionTextContains(response, "目标版本", "计算公式", "统计期间", "团队", "负责人");
        assertTerminalRouteWithoutTools(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("REJECT terminal guard 不应进入 LLM/工具链")
    void rejectTerminalGuard_shouldNotCallLlmOrTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("直接执行 SELECT * FROM crm_lead")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routing_calibration_guard", Map.of(
                                        "raw_route", "REJECT",
                                        "calibrated_route", "REJECT",
                                        "applied_rules", List.of("physical_table_sql_boundary"),
                                        "execution_allowed", true
                                )
                        ))
                        .build())
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-terminal-reject", null);

        assertEquals("reject", response.getType());
        assertEquals("ROUTING_TERMINAL_REJECT", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("REJECT", detail.get("terminal_route"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        Map<String, Object> routing = (Map<String, Object>) response.getDebug().get("routing_calibration");
        assertEquals("TERMINAL_ROUTE", routing.get("action"));
        assertEquals("REJECT", routing.get("calibrated_route"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("流式入口遇到 terminal guard 应直接 complete")
    void terminalGuardWithProgress_shouldCompleteBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("高质量线索转化率")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routing_calibration_guard", Map.of(
                                        "raw_route", "CLARIFY",
                                        "calibrated_route", "CLARIFY",
                                        "applied_rules", List.of("vague_lead_quality"),
                                        "execution_allowed", true
                                )
                        ))
                        .build())
                .build();

        List<ProgressEvent> events = queryExpertService
                .processQueryWithProgress(request, "trace-terminal-stream", null)
                .collectList()
                .block();

        assertNotNull(events);
        assertEquals(2, events.size());
        assertEquals("progress", events.get(0).getEventType());
        assertEquals("complete", events.get(1).getEventType());
        DatasetNLQueryResponse response = (DatasetNLQueryResponse) events.get(1).getData();
        assertEquals("clarify", response.getType());
        Map<String, Object> routing = (Map<String, Object>) response.getDebug().get("routing_calibration");
        assertEquals("TERMINAL_ROUTE", routing.get("action"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket SLA 缺阈值应在 LLM/工具链前澄清")
    void serviceTicketSlaMissingThreshold_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按团队统计本月创建工单的首次响应 SLA 达成率，分母为工单数，未响应不达标。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-missing-threshold", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        assertNotNull(response.getQuestions());
        assertTrue(response.getQuestions().get(0).contains("阈值"));
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("missing_sla_threshold", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        assertNotNull(response.getDebug());
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 物理表 SQL 应在 LLM/工具链前拒绝")
    void serviceTicketPhysicalSql_shouldRejectBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("直接查询 service_ticket 物理表，用 SQL 算每个团队 P1 工单首响超时率。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-physical-sql", null);

        assertEquals("reject", response.getType());
        assertEquals("SERVICE_TICKET_SLA_BOUNDARY_REJECTED", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("physical_table_sql", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 解决/合同日历 SLA 越界应在 LLM/工具链前澄清")
    void serviceTicketResolutionCalendarSla_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按团队统计本月创建工单的解决时效 SLA 达成率，客户合同 SLA 和工作日历都要生效。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-resolution-calendar", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("resolution_or_contract_calendar_sla_out_of_scope", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 首响 SLA 时间窗口冲突应在 LLM/工具链前澄清")
    void serviceTicketConflictingTimeScope_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("本月按团队统计最近 30 天创建工单的首次响应 SLA 达成率，SLA 阈值 48 小时，分母为工单数。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-conflicting-time", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("conflicting_time_scope", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 首响 SLA 字段混用应在 LLM/工具链前澄清")
    void serviceTicketFirstResponseFieldMismatch_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按团队统计本月创建工单的首次响应 SLA 达成率，用解决时间 resolvedAt 与 createdAt 的差值判断 48 小时是否达标。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-field-mismatch", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("first_response_resolution_field_mismatch", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 未响应数差值公式应在 LLM/工具链前澄清")
    void serviceTicketUnrespondedFormula_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("本月按团队统计首次响应 SLA 达成率，SLA 阈值 48 小时，并把未响应数直接按总工单数减去 SLA 达成工单数计算。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-unresponded-formula", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("ambiguous_unresponded_count_formula", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket SLA 阈值缺单位应在 LLM/工具链前澄清")
    void serviceTicketThresholdUnit_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按团队统计本月创建工单的首次响应 SLA 达成率，阈值为 48，分母为工单数，未响应不达标。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-threshold-unit", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("missing_sla_threshold_unit", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 按优先级不同 SLA 阈值但缺策略应在 LLM/工具链前澄清")
    void serviceTicketPriorityThresholdPolicy_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按团队统计本月创建工单的首次响应 SLA 达成率，要求按优先级使用不同 SLA 阈值，分母为工单数，未响应不达标。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-priority-policy", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("missing_priority_sla_threshold_policy", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 首响 SLA 暂停挂起扣除越界应在 LLM/工具链前澄清")
    void serviceTicketPauseHoldExclusion_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按团队统计本月创建工单的首次响应 SLA 达成率，SLA 阈值 48 小时，要求扣除客户等待、暂停和挂起时长，分母为工单数，未响应不达标。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-pause-hold", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("pause_hold_exclusion_sla_out_of_scope", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 首响 SLA 工作小时日历越界应在 LLM/工具链前澄清")
    void serviceTicketBusinessHoursSla_shouldClarifyBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按团队统计本月创建工单的首次响应 SLA 达成率，SLA 阈值 8 个工作小时，只计算工作日 9:00-18:00，节假日不计，分母为工单数，未响应不达标。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-business-hours", null);

        assertEquals("clarify", response.getType());
        assertEquals("SERVICE_TICKET_SLA_PARAMETER_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("business_hours_sla_out_of_scope", detail.get("boundary"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ServiceTicket 预测因果请求应在 LLM/工具链前拒绝")
    void serviceTicketPrediction_shouldRejectBeforeTools() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("预测下个月各客服团队首响 SLA 变差的原因，并给出人员调整建议。")
                .build();

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-sla-prediction", null);

        assertEquals("reject", response.getType());
        assertEquals("SERVICE_TICKET_SLA_BOUNDARY_REJECTED", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("unsupported_prediction_or_causality", detail.get("boundary"));
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
                                    "calculatedFields", List.of(Map.of(
                                            "name", "lateShipRisk",
                                            "expression", "if(shipDate > orderDate$id, 1, 0)"
                                    )),
                                    "slice", Map.of(
                                            "$or", List.of(
                                                    Map.of("field", "amount", "op", "is null"),
                                                    Map.of("field", "customer$caption", "op", "is null"),
                                                    Map.of(
                                                            "field", "orderDate$caption",
                                                            "op", ">",
                                                            "value", Map.of("$field", "shipDate")
                                                    )
                                            )
                                    ),
                                    "orderBy", List.of(Map.of(
                                            "field", "amount",
                                            "dir", "DESC"
                                    )),
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
        assertEquals(10, queryTrace.get("tool_failure_budget"));
        assertEquals(false, queryTrace.get("tool_failure_budget_exceeded"));
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
        assertEquals(List.of("lateShipRisk"), arguments.get("payload_calculated_field_names"));
        assertEquals(List.of("if(shipDate > orderDate$id, 1, 0)"), arguments.get("payload_calculated_field_expressions"));
        assertEquals(List.of("amount"), arguments.get("payload_order_by_fields"));
        assertEquals(List.of("desc"), arguments.get("payload_order_by_dirs"));
        assertEquals(20, arguments.get("payload_limit"));
        assertEquals(List.of("amount", "customer$caption", "orderDate$caption"), arguments.get("payload_slice_fields"));
        assertEquals(List.of("is null", ">"), arguments.get("payload_slice_ops"));
        assertEquals(List.of("shipDate"), arguments.get("payload_slice_field_refs"));
        assertEquals(List.of("or"), arguments.get("payload_slice_boolean_groups"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("query_trace 应展开 DSL_CTE executable_plan 语义信号")
    void queryTrace_shouldSummarizeDslCteExecutablePlanSignals() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按线索创建月份统计转化率")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(
                anyList(),
                eq("trace-dsl-cte-1"),
                isNull(),
                any(ToolCallCollector.class)
        )).thenAnswer(invocation -> {
            ToolCallCollector collector = invocation.getArgument(3);
            Map<String, Object> queryResult = Map.of(
                    "items", List.of(Map.of(
                            "createdAt$yearMonth", "2024-01",
                            "leadCount", 6,
                            "leadToOppRate", 0.67
                    )),
                    "total", 1
            );
            collector.recordToolCall(
                    "dataset.query_model",
                    "dataset_query_model",
                    Map.of(
                            "model", "CrmLead",
                            "payload", Map.of(
                                    "route", "DSL_CTE",
                                    "executable_plan", Map.of(
                                            "cte_plan", List.of(
                                                    Map.of(
                                                            "stage", "aggregate",
                                                            "groupBy", List.of("createdAt$yearMonth"),
                                                            "metrics", List.of(
                                                                    Map.of("name", "leadCount", "expr", "count(*)"),
                                                                    Map.of("name", "oppCount", "expr", "count(convertedOpportunityId)"),
                                                                    Map.of("name", "orderCount", "expr", "count(convertedOrderId)")
                                                            )
                                                    ),
                                                    Map.of(
                                                            "stage", "derive",
                                                            "derived", List.of(
                                                                    Map.of("name", "leadToOppRate", "expr", "oppCount / leadCount"),
                                                                    Map.of("name", "leadToOrderRate", "expr", "orderCount / leadCount")
                                                            )
                                                    )
                                            ),
                                            "outputs", List.of(
                                                    "createdAt$yearMonth",
                                                    "leadCount",
                                                    "oppCount",
                                                    "orderCount",
                                                    "leadToOppRate",
                                                    "leadToOrderRate"
                                            )
                                    )
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

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-dsl-cte-1", null);

        assertEquals("result", response.getType());
        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) queryTrace.get("tool_calls");
        Map<String, Object> arguments = (Map<String, Object>) toolCalls.get(0).get("arguments_summary");
        assertEquals("CrmLead", arguments.get("model"));
        assertTrue(((List<String>) arguments.get("payload_group_by")).contains("createdAt$yearMonth"));
        assertTrue(((List<String>) arguments.get("payload_columns")).contains("leadToOppRate"));
        assertTrue(((List<String>) arguments.get("payload_calculated_field_names")).contains("leadToOrderRate"));
        assertTrue(((List<String>) arguments.get("payload_calculated_field_expressions")).contains("count(convertedOpportunityId)"));
        assertTrue(((List<String>) arguments.get("payload_query_text")).stream()
                .anyMatch(value -> value.contains("convertedOrderId")));
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
        assertEquals(10, queryTrace.get("tool_failure_budget"));
        assertEquals(false, queryTrace.get("tool_failure_budget_exceeded"));
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
    @DisplayName("query_model 缺失字段且无结构化结果时应返回稳定 reject")
    void queryModelMissingFieldWithoutStructuredResult_shouldReturnRejectContract() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按产品品类统计销售额占比")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(
                anyList(),
                eq("trace-missing-field-1"),
                isNull(),
                any(ToolCallCollector.class)
        )).thenAnswer(invocation -> {
            ToolCallCollector collector = invocation.getArgument(3);
            RX<Object> failed = RX.failB("Field 'product$categoryName' not found in model 'FactOrderQueryModel'.");
            collector.recordToolCall(
                    "dataset.query_model",
                    "dataset_query_model",
                    Map.of(
                            "model", "FactOrderQueryModel",
                            "mode", "execute",
                            "payload", Map.of(
                                    "columns", List.of("sum(amount) as totalAmount"),
                                    "groupBy", List.of("product$categoryName")
                            )
                    ),
                    failed,
                    "[QUERY_MODEL_FAILED] 查询执行失败: Field 'product$categoryName' not found in model 'FactOrderQueryModel'.",
                    6
            );
            QueryExpertService.captureQueryResult(failed);
            return new ToolCallback[0];
        });
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("需要确认是否改用渠道或门店维度。");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-missing-field-1", null);

        assertEquals("reject", response.getType());
        assertEquals("FIELD_NOT_FOUND_IN_QUERY_MODEL", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("model_field_not_found", detail.get("reason"));
        assertEquals("product$categoryName", detail.get("field_name"));
        assertEquals("FactOrderQueryModel", detail.get("model_name"));
        assertEquals("clarify", detail.get("original_type"));

        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("reject", queryTrace.get("result_type"));
        assertEquals(1, queryTrace.get("tool_call_count"));
        assertEquals(1L, queryTrace.get("tool_failure_count"));
        assertEquals(false, queryTrace.get("query_result_captured"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("query_model 空过滤字段且无结构化结果时应返回稳定 reject")
    void queryModelEmptyFilterFieldWithoutStructuredResult_shouldReturnRejectContract() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("找出金额为空、客户为空、或订单日期晚于发货日期的订单。")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(
                anyList(),
                eq("trace-empty-filter-field-1"),
                isNull(),
                any(ToolCallCollector.class)
        )).thenAnswer(invocation -> {
            ToolCallCollector collector = invocation.getArgument(3);
            RX<Object> failed = RX.failB("查询执行失败: 查询条件第1项的field字段不能为空");
            collector.recordToolCall(
                    "dataset.query_model",
                    "dataset_query_model",
                    Map.of(
                            "model", "FactOrderQueryModel",
                            "mode", "execute",
                            "payload", Map.of(
                                    "columns", List.of("orderId", "orderDate$id", "customer$id", "amount"),
                                    "slice", List.of(Map.of("field", "", "op", "isNull"))
                            )
                    ),
                    failed,
                    "[QUERY_MODEL_FAILED] 查询执行失败: 查询条件第1项的field字段不能为空",
                    4
            );
            QueryExpertService.captureQueryResult(failed);
            return new ToolCallback[0];
        });
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("当前字段不足，无法执行该数据质量检查。");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-empty-filter-field-1", null);

        assertEquals("reject", response.getType());
        assertEquals("INVALID_QUERY_MODEL_FILTER", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("empty_filter_field", detail.get("reason"));
        assertEquals("empty_filter_field_in_query_model_filter", detail.get("terminal_contract"));
        assertEquals("info", detail.get("original_type"));

        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("reject", queryTrace.get("result_type"));
        assertEquals(1, queryTrace.get("tool_call_count"));
        assertEquals(1L, queryTrace.get("tool_failure_count"));
        assertEquals(false, queryTrace.get("query_result_captured"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("query_model 聚合别名二次计算失败且无结构化结果时应返回稳定 reject")
    void queryModelPostAggregateAliasFailureWithoutStructuredResult_shouldReturnRejectContract() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按品类统计销售额占总销售额比例，只保留占比超过 5% 的品类")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(
                anyList(),
                eq("trace-postagg-alias-1"),
                isNull(),
                any(ToolCallCollector.class)
        )).thenAnswer(invocation -> {
            ToolCallCollector collector = invocation.getArgument(3);
            RX<Object> failed = RX.failB(
                    "CALCULATED_FIELD_EXPRESSION_INVALID: 编译计算字段表达式失败 [totalShare]: "
                            + "未能在查询模型FactOrderQueryModel中找到列totalSales");
            collector.recordToolCall(
                    "dataset.query_model",
                    "dataset_query_model",
                    Map.of(
                            "model", "FactOrderQueryModel",
                            "mode", "execute",
                            "payload", Map.of(
                                    "columns", List.of("promotion$promotionType", "sum(amount) as totalSales", "totalShare"),
                                    "groupBy", List.of(Map.of("field", "promotion$promotionType")),
                                    "calculatedFields", List.of(Map.of(
                                            "name", "totalShare",
                                            "expression", "totalSales / NULLIF(CALCULATE(SUM(totalSales)), 0)"
                                    ))
                            )
                    ),
                    failed,
                    "[QUERY_MODEL_FAILED] 查询执行失败: CALCULATED_FIELD_EXPRESSION_INVALID: "
                            + "编译计算字段表达式失败 [totalShare]: 未能在查询模型FactOrderQueryModel中找到列totalSales",
                    5
            );
            QueryExpertService.captureQueryResult(failed);
            return new ToolCallback[0];
        });
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("请问是否改用渠道或门店类型作为维度？");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-postagg-alias-1", null);

        assertEquals("reject", response.getType());
        assertEquals("POST_AGGREGATE_ALIAS_EXPRESSION_UNSUPPORTED", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("post_aggregate_alias_expression_unsupported", detail.get("reason"));
        assertEquals("totalShare", detail.get("calculated_field_name"));
        assertEquals("totalSales", detail.get("alias_name"));
        assertEquals("clarify", detail.get("original_type"));

        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("reject", queryTrace.get("result_type"));
        assertEquals(1, queryTrace.get("tool_call_count"));
        assertEquals(1L, queryTrace.get("tool_failure_count"));
        assertEquals(false, queryTrace.get("query_result_captured"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("未知 Spring 工具调用应返回稳定 reject 并保留 query_trace")
    void unknownSpringToolCall_shouldReturnRejectWithTrace() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("生成一个复合查询脚本")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(anyList(), eq("trace-unknown-tool-1"), isNull(), any(ToolCallCollector.class)))
                .thenReturn(new ToolCallback[0]);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(
                new IllegalStateException("No ToolCallback found for tool name: dataset_compose_script"));

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-unknown-tool-1", null);

        assertEquals("reject", response.getType());
        assertEquals("UNKNOWN_TOOL_CALL", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("dataset_compose_script", detail.get("spring_tool_name"));
        assertEquals("dataset.compose.script", detail.get("tool_name"));
        assertEquals("unregistered_tool_call", detail.get("reason"));

        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("trace-unknown-tool-1", queryTrace.get("trace_id"));
        assertEquals("reject", queryTrace.get("result_type"));
        assertEquals(3, queryTrace.get("registered_tool_count"));
        assertEquals(0, queryTrace.get("tool_call_count"));
        assertEquals(false, queryTrace.get("query_result_captured"));
    }

    @Test
    @DisplayName("provider response parse 失败应重试一次")
    void providerResponseParseFailure_shouldRetryOnce() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("分析订单趋势")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(anyList(), eq("trace-provider-retry-1"), isNull(), any(ToolCallCollector.class)))
                .thenReturn(new ToolCallback[0]);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call())
                .thenThrow(new IllegalStateException("Error while extracting response for type [OpenAiApi$ChatCompletion]"))
                .thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("已完成分析，建议补充统计口径。");

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-provider-retry-1", null);

        assertEquals("clarify", response.getType());
        verify(requestSpec, times(2)).call();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("provider quota/cooldown 应返回稳定 PROVIDER_UNAVAILABLE 并保留 query_trace")
    void providerUnavailable_shouldReturnStableErrorWithTrace() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("分析订单趋势")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(anyList(), eq("trace-provider-unavailable-1"), isNull(), any(ToolCallCollector.class)))
                .thenReturn(new ToolCallback[0]);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new IllegalStateException(
                "HTTP 429 - {\"error\":{\"code\":\"model_cooldown\",\"message\":\"All credentials for model gemini-pro-agent are cooling down via provider antigravity\"}}"
        ));

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-provider-unavailable-1", null);

        assertEquals("error", response.getType());
        assertEquals("PROVIDER_UNAVAILABLE", response.getCode());
        assertTrue(response.getMsg().contains("temporarily unavailable"));
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("provider_unavailable", detail.get("reason"));
        assertTrue(String.valueOf(detail.get("original_error")).contains("model_cooldown"));

        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("trace-provider-unavailable-1", queryTrace.get("trace_id"));
        assertEquals("error", queryTrace.get("result_type"));
        assertEquals(3, queryTrace.get("registered_tool_count"));
        assertEquals(0, queryTrace.get("tool_call_count"));
        assertEquals(false, queryTrace.get("query_result_captured"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("工具失败预算耗尽应返回稳定 TOOL_FAILURE_BUDGET_EXCEEDED 并保留 query_trace")
    void toolFailureBudgetExceeded_shouldReturnStableErrorWithTrace() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("按品类统计销售额占比")
                .build();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(mcpToolDispatcher.getTool(anyString())).thenAnswer(invocation -> mockTool(invocation.getArgument(0)));
        when(toolCallbackFactory.createToolCallbacks(
                anyList(),
                eq("trace-tool-budget-1"),
                isNull(),
                any(ToolCallCollector.class)
        )).thenAnswer(invocation -> {
            ToolCallCollector collector = invocation.getArgument(3);
            for (int i = 0; i < 10; i++) {
                collector.recordToolCall(
                        "dataset.query_model",
                        "dataset_query_model",
                        Map.of(
                                "model", "FactOrderQueryModel",
                                "payload", Map.of("columns", List.of("sum(amount) as totalAmount", "totalShare"))
                        ),
                        RX.failB("CALCULATED_FIELD_EXPRESSION_INVALID"),
                        "[QUERY_MODEL_FAILED] CALCULATED_FIELD_EXPRESSION_INVALID",
                        3
                );
            }
            return new ToolCallback[0];
        });
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new IllegalStateException(
                McpToolCallbackFactory.TOOL_FAILURE_BUDGET_EXCEEDED_MARKER
                        + ": tool failure budget exceeded before executing dataset.query_model"));

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-tool-budget-1", null);

        assertEquals("error", response.getType());
        assertEquals("TOOL_FAILURE_BUDGET_EXCEEDED", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("tool_failure_budget_exceeded", detail.get("reason"));
        assertEquals(10, detail.get("tool_failure_budget"));

        Map<String, Object> queryTrace = (Map<String, Object>) response.getDebug().get("query_trace");
        assertEquals("trace-tool-budget-1", queryTrace.get("trace_id"));
        assertEquals("error", queryTrace.get("result_type"));
        assertEquals(10, queryTrace.get("tool_call_count"));
        assertEquals(10L, queryTrace.get("tool_failure_count"));
        assertEquals(true, queryTrace.get("tool_failure_budget_exceeded"));
        assertEquals(false, queryTrace.get("query_result_captured"));
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

    private static DatasetNLQueryRequest terminalClarifyRequest(
            String query,
            List<String> risks,
            List<String> appliedRules
    ) {
        return DatasetNLQueryRequest.builder()
                .query(query)
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routing_calibration_guard", Map.of(
                                        "raw_route", "CLARIFY",
                                        "calibrated_route", "CLARIFY",
                                        "raw_risks", risks,
                                        "calibrated_risks", risks,
                                        "applied_rules", appliedRules,
                                        "execution_allowed", true
                                )
                        ))
                        .build())
                .build();
    }

    private static void assertQuestionTextContains(DatasetNLQueryResponse response, String... expected) {
        assertNotNull(response.getQuestions());
        String text = String.join("\n", response.getQuestions());
        for (String value : expected) {
            assertTrue(text.contains(value), "expected clarify questions to contain: " + value + ", actual: " + text);
        }
    }

    private static void assertQuestionTextNotContains(DatasetNLQueryResponse response, String... unexpected) {
        assertNotNull(response.getQuestions());
        String text = String.join("\n", response.getQuestions());
        for (String value : unexpected) {
            assertFalse(text.contains(value), "expected clarify questions not to contain: " + value + ", actual: " + text);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertTerminalRouteWithoutTools(DatasetNLQueryResponse response) {
        assertEquals("ROUTING_TERMINAL_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("CLARIFY", detail.get("terminal_route"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        Map<String, Object> routing = (Map<String, Object>) response.getDebug().get("routing_calibration");
        assertEquals("TERMINAL_ROUTE", routing.get("action"));
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
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
