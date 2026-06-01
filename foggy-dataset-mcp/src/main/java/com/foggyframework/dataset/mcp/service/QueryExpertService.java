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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询专家服务（M2）
 *
 * 使用 Spring AI 实现自然语言查询理解和执行
 * 对应 Python 版的 QueryExpertM2Service
 *
 * <p>工具定义从 schemas/ 目录加载，通过 McpToolCallbackFactory 转换为 Spring AI ToolCallback。
 */
@Slf4j
@Service
public class QueryExpertService {

    private final ChatClient.Builder chatClientBuilder;
    private final DatasetAccessor datasetAccessor;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;
    private final McpToolDispatcher mcpToolDispatcher;
    private final McpToolCallbackFactory toolCallbackFactory;
    private final RoutingCalibrationActionResolver routingCalibrationActionResolver;

    // 会话管理
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    /**
     * 线程级 query_model 执行结果捕获槽。
     *
     * <p>由 {@link McpToolCallbackFactory} 在执行 {@code dataset.query_model} 工具后写入，
     * 由 {@link #processQuery} 在 Spring AI call() 返回后读取，随后立即清理。
     * 这样无需修改 Spring AI 内部工具调用链，即可将结构化结果传递回调用方。
     */
    static final ThreadLocal<Map<String, Object>> LAST_QUERY_RESULT = new ThreadLocal<>();

    private static final String ROUTING_REPLAN_REQUIRED_CODE = "ROUTING_REPLAN_REQUIRED";
    private static final String QUERY_MODEL_FAILED_CODE = "QUERY_MODEL_FAILED";
    private static final String FIELD_NOT_FOUND_IN_QUERY_MODEL_CODE = "FIELD_NOT_FOUND_IN_QUERY_MODEL";
    private static final String INVALID_QUERY_MODEL_FILTER_CODE = "INVALID_QUERY_MODEL_FILTER";
    private static final String POST_AGGREGATE_ALIAS_EXPRESSION_UNSUPPORTED_CODE =
            "POST_AGGREGATE_ALIAS_EXPRESSION_UNSUPPORTED";
    private static final String UNKNOWN_TOOL_CALL_CODE = "UNKNOWN_TOOL_CALL";
    private static final String PROVIDER_UNAVAILABLE_CODE = "PROVIDER_UNAVAILABLE";
    private static final String PROVIDER_RESPONSE_PARSE_FAILED_CODE = "PROVIDER_RESPONSE_PARSE_FAILED";
    private static final String TOOL_FAILURE_BUDGET_EXCEEDED_CODE = "TOOL_FAILURE_BUDGET_EXCEEDED";
    private static final String REPLAN_RECOMMENDED_ACTION = "REPLAN_BY_CALIBRATED_ROUTE";
    private static final String PROVIDER_RESPONSE_PARSE_FAILED_MARKER = "Error while extracting response for type";
    private static final int QUERY_TRACE_TOOL_FAILURE_BUDGET = 10;
    private static final int QUERY_TRACE_PAYLOAD_SLICE_SIGNAL_LIMIT = 64;
    private static final List<String> PROVIDER_UNAVAILABLE_MARKERS = List.of(
            "QUOTA_EXHAUSTED",
            "RESOURCE_EXHAUSTED",
            "Individual quota reached",
            "model_cooldown",
            "All credentials for model",
            "provider antigravity"
    );
    private static final Pattern UNKNOWN_TOOL_CALL_PATTERN = Pattern.compile(
            "No ToolCallback found for tool name:\\s*([A-Za-z0-9_.-]+)");
    private static final Pattern FIELD_NOT_FOUND_IN_MODEL_PATTERN = Pattern.compile(
            "Field '([^']+)' not found in model '([^']+)'");
    private static final Pattern CALCULATED_FIELD_NAME_PATTERN = Pattern.compile(
            "编译计算字段表达式失败\\s*\\[([^\\]]+)]");
    private static final Pattern MISSING_CALCULATED_FIELD_COLUMN_PATTERN = Pattern.compile(
            "未能在查询模型[^中]*中找到列([A-Za-z_][A-Za-z0-9_$]*)");
    private static final List<String> STALE_PLAN_FIELDS = List.of(
            "dsl_params",
            "semantic_sql",
            "cte_plan",
            "memory_grid_plan",
            "clarifying_questions",
            "tool_calls"
    );

    /**
     * 由 {@link McpToolCallbackFactory} 调用，将 dataset.query_model 的结构化结果写入当前线程捕获槽。
     */
    @SuppressWarnings("unchecked")
    public static void captureQueryResult(Object result) {
        Map<String, Object> normalized = normalizeQueryResult(result);
        if (normalized != null) {
            LAST_QUERY_RESULT.set(normalized);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeQueryResult(Object result) {
        if (result instanceof RX<?> rx) {
            if (rx.getCode() != RX.SUCCESS) {
                return null;
            }
            return normalizeQueryResult(rx.getData());
        }
        if (result instanceof SemanticQueryResponse semanticResponse) {
            return semanticQueryResponseToMap(semanticResponse);
        }
        if (result instanceof Map<?, ?> map) {
            Object data = map.get("data");
            if (data != null) {
                Map<String, Object> normalizedData = normalizeQueryResult(data);
                if (normalizedData != null) {
                    return normalizedData;
                }
            }
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static Map<String, Object> semanticQueryResponseToMap(SemanticQueryResponse response) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("items", response.getItems() != null ? response.getItems() : List.of());
        Long total = response.getTotal();
        if (total == null && response.getPagination() != null && response.getPagination().getTotalCount() != null) {
            total = response.getPagination().getTotalCount();
        }
        if (total == null) {
            total = (long) (response.getItems() != null ? response.getItems().size() : 0);
        }
        normalized.put("total", total);
        if (response.getHasNext() != null) {
            normalized.put("hasNext", response.getHasNext());
        } else if (response.getPagination() != null && response.getPagination().getHasMore() != null) {
            normalized.put("hasNext", response.getPagination().getHasMore());
        }
        return normalized;
    }

    /**
     * 清理当前线程的捕获槽（call 结束后必须调用，防止线程池复用时数据残留）。
     */
    public static void clearCapture() {
        LAST_QUERY_RESULT.remove();
    }

    // 系统提示词
    private static final String SYSTEM_PROMPT = """
        你是一个专业的数据查询专家（M2），负责将用户的自然语言查询转换为结构化的数据查询。

        ## 工作流程
        1. 首先调用 dataset_list_models 获取可用的数据模型列表
        2. 根据用户查询意图，选择合适的数据模型
        3. 如需详细字段信息，调用 dataset_describe_model_internal 获取模型详情
        4. 构建查询参数，调用 dataset_query_model 执行查询
        5. 整理结果，返回给用户

        ## 查询参数说明
        - columns: 要查询的列，使用 $caption 后缀获取显示文本（如 team$caption）
        - slice: 过滤条件数组，每个条件包含 name/type/value
          - type 支持: =, !=, >, <, like, in, not in, [], is null, is not null
        - groupBy: 分组字段数组
        - orderBy: 排序规则数组，每项包含 name 和 dir（ASC/DESC）
        - limit: 返回记录数限制（默认20）

        ## 时间处理规则
        - "最近一周": 从当前日期往前7天
        - "本月": 当月1日到今天
        - "上个月": 上月1日到上月最后一天
        - 使用 [] 操作符处理日期范围，格式: ["2024-01-01", "2024-01-31"]

        ## 重要规则
        1. 必须先获取元数据了解可用模型
        2. 字段名必须准确，不确定时先调用 dataset_describe_model_internal
        3. 聚合查询必须包含 groupBy
        4. 返回结果要包含清晰的摘要说明
        """;

    public QueryExpertService(
            ChatClient.Builder chatClientBuilder,
            DatasetAccessor datasetAccessor,
            McpProperties mcpProperties,
            ObjectMapper objectMapper,
            McpToolDispatcher mcpToolDispatcher,
            McpToolCallbackFactory toolCallbackFactory,
            RoutingCalibrationActionResolver routingCalibrationActionResolver
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.datasetAccessor = datasetAccessor;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
        this.mcpToolDispatcher = mcpToolDispatcher;
        this.toolCallbackFactory = toolCallbackFactory;
        this.routingCalibrationActionResolver = routingCalibrationActionResolver;

        log.info("QueryExpertService initialized with DatasetAccessor: {}", datasetAccessor.getAccessMode());
    }

    /**
     * 处理自然语言查询（同步）
     *
     * @param request       查询请求
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 查询响应
     */
    public DatasetNLQueryResponse processQuery(DatasetNLQueryRequest request, String traceId, String authorization) {
        log.info("Processing query: {}, traceId={}", request.getQuery(), traceId);

        String sessionId = request.getSessionId() != null ?
                request.getSessionId() : UUID.randomUUID().toString();

        try {
            RoutingCalibrationAction calibrationAction = routingCalibrationActionResolver.resolve(request);
            if (calibrationAction.type() == RoutingCalibrationActionType.BLOCKED) {
                return routingCalibrationReplanRequiredResponse(calibrationAction, traceId);
            }

            // 获取或创建会话上下文
            SessionContext context = sessions.computeIfAbsent(sessionId,
                    k -> new SessionContext(sessionId, traceId));
            context.setTraceId(traceId);
            context.setAuthorization(authorization);
            context.setLastQueryResult(null);

            // 构建用户消息
            String userMessage = buildUserMessage(request, calibrationAction);

            // 获取核心查询工具并转换为 ToolCallback
            List<McpTool> queryTools = getQueryTools();
            ToolCallCollector collector = new ToolCallCollector(sessionId);
            ToolCallback[] toolCallbacks = toolCallbackFactory.createToolCallbacks(queryTools, traceId, authorization, collector);

            log.info("Registered {} tools for query, traceId={}", toolCallbacks.length, traceId);

            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(SYSTEM_PROMPT)
                    .build();

            // Spring AI 在单次 call() 内会自动驱动完整的工具调用链
            // （LLM 发起 tool call → 框架执行工具 → 结果回传 LLM → LLM 生成最终回复），
            // 无需外层 while 循环手动重试。
            String aiResponse;
            try {
                aiResponse = callChatContentWithProviderParseRetry(chatClient, userMessage, toolCallbacks, traceId);
            } catch (Exception e) {
                DatasetNLQueryResponse terminal = normalizeTerminalCallException(
                        e, traceId, sessionId, queryTools, collector);
                if (terminal != null) {
                    return terminal;
                }
                throw e;
            }

            // 读取 McpToolCallbackFactory 在工具执行期间写入的结构化查询结果
            Map<String, Object> captured = LAST_QUERY_RESULT.get();
            if (captured != null) {
                context.setLastQueryResult(captured);
            }

            log.debug("AI response, traceId={}, length={}", traceId, aiResponse != null ? aiResponse.length() : 0);
            DatasetNLQueryResponse response = parseResponse(aiResponse, context, traceId);
            response = enforceQueryToolFailureContract(response, collector);
            response = enforceRoutingReplanResultContract(response, calibrationAction);
            response = enforceInfoTerminalContract(response);
            response = attachQueryTraceDebug(response, traceId, sessionId, queryTools, collector, captured);
            response = attachRoutingReplanDispatchDebug(response, calibrationAction, traceId);
            return attachRoutingCalibrationDebug(response, calibrationAction, traceId);

        } catch (Exception e) {
            log.error("Query processing failed: {}, traceId={}", e.getMessage(), traceId, e);
            return DatasetNLQueryResponse.error("QUERY_FAILED", e.getMessage(), null);
        } finally {
            // 清理 ThreadLocal，防止线程池复用时数据残留
            clearCapture();
        }
    }

    /**
     * 处理自然语言查询（带进度流）
     *
     * @param request       查询请求
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 进度事件流
     */
    public Flux<ProgressEvent> processQueryWithProgress(DatasetNLQueryRequest request, String traceId, String authorization) {
        return Flux.create(sink -> {
            try {
                sink.next(ProgressEvent.progress("analyze", 10));

                RoutingCalibrationAction calibrationAction = routingCalibrationActionResolver.resolve(request);
                if (calibrationAction.type() == RoutingCalibrationActionType.BLOCKED) {
                    sink.next(routingCalibrationReplanRequiredEvent(calibrationAction, traceId));
                    sink.complete();
                    return;
                }

                String sessionId = request.getSessionId() != null ?
                        request.getSessionId() : UUID.randomUUID().toString();

                SessionContext context = sessions.computeIfAbsent(sessionId,
                        k -> new SessionContext(sessionId, traceId));
                context.setTraceId(traceId);
                context.setAuthorization(authorization);
                context.setLastQueryResult(null);

                String userMessage = buildUserMessage(request, calibrationAction);
                if (calibrationAction.type() == RoutingCalibrationActionType.REPLAN_REQUIRED) {
                    sink.next(routingCalibrationReplanDispatchEvent(calibrationAction, traceId));
                }
                sink.next(ProgressEvent.progress("plan", 20));

                List<McpTool> queryTools = getQueryTools();
                ToolCallCollector collector = new ToolCallCollector(sessionId);
                ToolCallback[] toolCallbacks = toolCallbackFactory.createToolCallbacks(queryTools, traceId, authorization, collector);

                ChatClient chatClient = chatClientBuilder
                        .defaultSystem(SYSTEM_PROMPT)
                        .build();

                sink.next(ProgressEvent.progress("tool_call", 40));

                // Spring AI 单次 call() 内自动驱动完整工具调用链
                String aiResponse;
                try {
                    aiResponse = callChatContentWithProviderParseRetry(chatClient, userMessage, toolCallbacks, traceId);
                } catch (Exception e) {
                    DatasetNLQueryResponse terminal = normalizeTerminalCallException(
                            e, traceId, sessionId, queryTools, collector);
                    if (terminal != null) {
                        sink.next(ProgressEvent.complete(terminal));
                        sink.complete();
                        return;
                    }
                    throw e;
                }

                // 读取工具执行期间捕获的结构化查询结果
                Map<String, Object> captured = LAST_QUERY_RESULT.get();
                if (captured != null) {
                    context.setLastQueryResult(captured);
                }

                sink.next(ProgressEvent.progress("format", 90));

                DatasetNLQueryResponse result = parseResponse(aiResponse, context, traceId);
                result = enforceQueryToolFailureContract(result, collector);
                result = enforceRoutingReplanResultContract(result, calibrationAction);
                result = enforceInfoTerminalContract(result);
                result = attachQueryTraceDebug(result, traceId, sessionId, queryTools, collector, captured);
                result = attachRoutingReplanDispatchDebug(result, calibrationAction, traceId);
                result = attachRoutingCalibrationDebug(result, calibrationAction, traceId);
                sink.next(ProgressEvent.complete(result));
                sink.complete();

            } catch (Exception e) {
                log.error("Query processing failed with progress: {}", e.getMessage(), e);
                sink.next(ProgressEvent.error("QUERY_FAILED", e.getMessage()));
                sink.complete();
            } finally {
                clearCapture();
            }
        });
    }

    /**
     * 获取用于查询的核心工具
     */
    private List<McpTool> getQueryTools() {
        List<McpTool> tools = new ArrayList<>();
        // 只获取核心查询相关的工具
        String[] toolNames = {
                "dataset.list_models",
                "dataset.describe_model_internal",
                "dataset.query_model"
        };

        for (String name : toolNames) {
            McpTool tool = mcpToolDispatcher.getTool(name);
            if (tool != null) {
                tools.add(tool);
            } else {
                log.warn("Tool not found: {}", name);
            }
        }

        return tools;
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(DatasetNLQueryRequest request) {
        return buildUserMessage(request, RoutingCalibrationAction.executeRaw());
    }

    private String buildUserMessage(DatasetNLQueryRequest request, RoutingCalibrationAction calibrationAction) {
        StringBuilder sb = new StringBuilder();

        // 时间上下文
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        sb.append(String.format(
                "[当前时间: %s 北京时间 | %d年 Q%d %d月 第%d周 周%s]\n\n",
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                now.getYear(),
                (now.getMonthValue() - 1) / 3 + 1,
                now.getMonthValue(),
                now.getDayOfYear() / 7 + 1,
                getDayOfWeekChinese(now.getDayOfWeek().getValue())
        ));

        sb.append("用户查询: ").append(request.getQuery());

        if (request.getHints() != null) {
            DatasetNLQueryRequest.QueryHints hints = request.getHints();
            if (hints.getTimeRange() != null && hints.getTimeRange().getPreset() != null) {
                sb.append("\n时间范围提示: ").append(hints.getTimeRange().getPreset());
            }
            if (hints.getPreferredModels() != null && !hints.getPreferredModels().isEmpty()) {
                sb.append("\n优先模型: ").append(String.join(", ", hints.getPreferredModels()));
            }
        }

        if (calibrationAction != null
                && calibrationAction.type() == RoutingCalibrationActionType.REPLAN_REQUIRED) {
            sb.append("\n\n[路由校准守卫]\n");
            sb.append("上游模型输出的路由已被校准，禁止复用旧 route 的计划、SQL、DSL、CTE、memory grid 或已生成的工具调用参数。\n");
            if (calibrationAction.rawRoute() != null) {
                sb.append("原始路由: ").append(calibrationAction.rawRoute()).append("\n");
            }
            sb.append("校准后路由: ").append(calibrationAction.calibratedRoute()).append("\n");
            if (!calibrationAction.calibratedRisks().isEmpty()) {
                sb.append("校准后风险: ").append(String.join(", ", calibrationAction.calibratedRisks())).append("\n");
            }
            if (!calibrationAction.appliedRules().isEmpty()) {
                sb.append("命中规则: ").append(String.join(", ", calibrationAction.appliedRules())).append("\n");
            }
            sb.append("必须按校准后路由重新规划并重新生成工具调用参数。");
        }

        return sb.toString();
    }

    private String getDayOfWeekChinese(int dayOfWeek) {
        return new String[]{"一", "二", "三", "四", "五", "六", "日"}[dayOfWeek - 1];
    }

    static DatasetNLQueryResponse attachRoutingCalibrationDebug(
            DatasetNLQueryResponse response,
            RoutingCalibrationAction calibrationAction,
            String traceId
    ) {
        if (response == null || calibrationAction == null || !shouldExposeCalibrationDebug(calibrationAction)) {
            return response;
        }

        Map<String, Object> debug = new LinkedHashMap<>();
        if (response.getDebug() != null) {
            debug.putAll(response.getDebug());
        }
        if (traceId != null && !traceId.isBlank()) {
            debug.put("trace_id", traceId);
        }
        debug.put("routing_calibration", calibrationAction.toAuditMap());
        response.setDebug(debug);
        return response;
    }

    static DatasetNLQueryResponse attachQueryTraceDebug(
            DatasetNLQueryResponse response,
            String traceId,
            String sessionId,
            List<McpTool> registeredTools,
            ToolCallCollector collector,
            Map<String, Object> capturedQueryResult
    ) {
        if (response == null) {
            return null;
        }

        Map<String, Object> debug = new LinkedHashMap<>();
        if (response.getDebug() != null) {
            debug.putAll(response.getDebug());
        }
        if (traceId != null && !traceId.isBlank()) {
            debug.put("trace_id", traceId);
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("trace_id", safeString(traceId));
        trace.put("session_id", safeString(sessionId));
        trace.put("result_type", safeString(response.getType()));
        trace.put("registered_tools", registeredToolNames(registeredTools));
        trace.put("registered_tool_count", registeredTools != null ? registeredTools.size() : 0);

        List<ToolCallCollector.ToolCallRecord> calls = collector != null ? collector.getToolCalls() : List.of();
        long successCount = calls.stream().filter(ToolCallCollector.ToolCallRecord::isSuccess).count();
        long failureCount = calls.size() - successCount;
        trace.put("tool_call_count", calls.size());
        trace.put("tool_success_count", successCount);
        trace.put("tool_failure_count", failureCount);
        trace.put("tool_failure_budget", QUERY_TRACE_TOOL_FAILURE_BUDGET);
        trace.put("tool_failure_budget_exceeded", failureCount >= QUERY_TRACE_TOOL_FAILURE_BUDGET);
        trace.put("all_tools_success", collector == null || collector.isAllSuccess());
        trace.put("query_result_captured", capturedQueryResult != null);
        if (capturedQueryResult != null) {
            trace.putAll(queryResultSummary(capturedQueryResult));
        }
        trace.put("tool_calls", calls.stream()
                .map(QueryExpertService::toolCallSummary)
                .toList());

        debug.put("query_trace", trace);
        response.setDebug(debug);
        return response;
    }

    private static String callChatContentWithProviderParseRetry(
            ChatClient chatClient,
            String userMessage,
            ToolCallback[] toolCallbacks,
            String traceId
    ) {
        try {
            return callChatContent(chatClient, userMessage, toolCallbacks);
        } catch (Exception e) {
            if (!isProviderResponseParseFailure(e)) {
                throw e;
            }
            log.warn("Provider response parse failed; retrying once, traceId={}, error={}", traceId, e.getMessage());
            return callChatContent(chatClient, userMessage, toolCallbacks);
        }
    }

    private static String callChatContent(
            ChatClient chatClient,
            String userMessage,
            ToolCallback[] toolCallbacks
    ) {
        return chatClient.prompt()
                .user(userMessage)
                .toolCallbacks(toolCallbacks)
                .call()
                .content();
    }

    private static DatasetNLQueryResponse normalizeTerminalCallException(
            Exception exception,
            String traceId,
            String sessionId,
            List<McpTool> queryTools,
            ToolCallCollector collector
    ) {
        DatasetNLQueryResponse response = unknownToolCallResponse(exception);
        if (response == null) {
            response = providerResponseParseFailureResponse(exception);
        }
        if (response == null) {
            response = providerUnavailableResponse(exception);
        }
        if (response == null) {
            response = toolFailureBudgetExceededResponse(exception);
        }
        if (response == null) {
            return null;
        }
        return attachQueryTraceDebug(response, traceId, sessionId, queryTools, collector, null);
    }

    private static DatasetNLQueryResponse unknownToolCallResponse(Exception exception) {
        String springToolName = extractUnknownSpringToolName(exception);
        if (springToolName == null) {
            return null;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tool_name", springToolName.replace('_', '.'));
        detail.put("spring_tool_name", springToolName);
        detail.put("reason", "unregistered_tool_call");
        detail.put("original_error", safeString(exception.getMessage()));

        return DatasetNLQueryResponse.reject(
                UNKNOWN_TOOL_CALL_CODE,
                "模型请求了当前 Java MCP 未注册的工具，已拒绝执行。",
                detail
        );
    }

    private static DatasetNLQueryResponse providerUnavailableResponse(Exception exception) {
        if (!isProviderUnavailable(exception)) {
            return null;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", "provider_unavailable");
        detail.put("original_error", safeString(exception.getMessage()));

        return DatasetNLQueryResponse.error(
                PROVIDER_UNAVAILABLE_CODE,
                "LLM provider is temporarily unavailable due to quota, cooldown, or upstream capacity limits.",
                detail
        );
    }

    private static DatasetNLQueryResponse providerResponseParseFailureResponse(Exception exception) {
        if (!isProviderResponseParseFailure(exception)) {
            return null;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", "provider_response_parse_failed");
        detail.put("original_error", safeString(exception.getMessage()));

        return DatasetNLQueryResponse.error(
                PROVIDER_RESPONSE_PARSE_FAILED_CODE,
                "LLM provider response could not be parsed after retry.",
                detail
        );
    }

    private static DatasetNLQueryResponse toolFailureBudgetExceededResponse(Exception exception) {
        if (!isToolFailureBudgetExceeded(exception)) {
            return null;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", "tool_failure_budget_exceeded");
        detail.put("tool_failure_budget", QUERY_TRACE_TOOL_FAILURE_BUDGET);
        detail.put("original_error", safeString(exception.getMessage()));

        return DatasetNLQueryResponse.error(
                TOOL_FAILURE_BUDGET_EXCEEDED_CODE,
                "Query tool failure budget was exceeded before a structured result was produced.",
                detail
        );
    }

    private static String extractUnknownSpringToolName(Throwable throwable) {
        for (String message : exceptionMessages(throwable)) {
            Matcher matcher = UNKNOWN_TOOL_CALL_PATTERN.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static boolean isProviderResponseParseFailure(Throwable throwable) {
        for (String message : exceptionMessages(throwable)) {
            if (message.contains(PROVIDER_RESPONSE_PARSE_FAILED_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProviderUnavailable(Throwable throwable) {
        for (String message : exceptionMessages(throwable)) {
            for (String marker : PROVIDER_UNAVAILABLE_MARKERS) {
                if (message.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isToolFailureBudgetExceeded(Throwable throwable) {
        for (String message : exceptionMessages(throwable)) {
            if (message.contains(McpToolCallbackFactory.TOOL_FAILURE_BUDGET_EXCEEDED_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> exceptionMessages(Throwable throwable) {
        List<String> messages = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && seen.add(current)) {
            if (current.getMessage() != null) {
                messages.add(current.getMessage());
            }
            current = current.getCause();
        }
        return messages;
    }

    static DatasetNLQueryResponse attachRoutingReplanDispatchDebug(
            DatasetNLQueryResponse response,
            RoutingCalibrationAction calibrationAction,
            String traceId
    ) {
        if (response == null
                || calibrationAction == null
                || calibrationAction.type() != RoutingCalibrationActionType.REPLAN_REQUIRED) {
            return response;
        }

        Map<String, Object> debug = new LinkedHashMap<>();
        if (response.getDebug() != null) {
            debug.putAll(response.getDebug());
        }
        if (traceId != null && !traceId.isBlank()) {
            debug.put("trace_id", traceId);
        }

        Map<String, Object> dispatch = new LinkedHashMap<>(routingCalibrationReplanDispatch(calibrationAction));
        dispatch.put("dispatched", true);
        dispatch.put("dispatch_mode", "actual_calibrated_route");
        debug.put("routing_replan_dispatch", dispatch);
        response.setDebug(debug);
        return response;
    }

    private static DatasetNLQueryResponse enforceRoutingReplanResultContract(
            DatasetNLQueryResponse response,
            RoutingCalibrationAction calibrationAction
    ) {
        if (response == null
                || calibrationAction == null
                || calibrationAction.type() != RoutingCalibrationActionType.REPLAN_REQUIRED
                || !"info".equals(response.getType())) {
            return response;
        }

        Map<String, Object> candidates = new LinkedHashMap<>();
        candidates.put("original_type", response.getType());
        candidates.put("original_topic", response.getTopic());
        candidates.put("original_note", response.getNote());
        candidates.put("original_data", response.getData());
        candidates.put("calibrated_route", safeString(calibrationAction.calibratedRoute()));
        candidates.put("reason", "replan dispatch did not produce a structured query result");

        return DatasetNLQueryResponse.clarify(
                List.of("路由已按校准结果重新规划，但本次未产生可验收的结构化查询结果。请确认是否按当前模型可用字段改写查询，或补充缺失字段后重试。"),
                candidates
        );
    }

    private static DatasetNLQueryResponse enforceQueryToolFailureContract(
            DatasetNLQueryResponse response,
            ToolCallCollector collector
    ) {
        if (response == null || collector == null) {
            return response;
        }

        ToolCallCollector.ToolCallRecord failedQueryCall = lastFailedQueryModelCall(collector);
        if (failedQueryCall == null) {
            return response;
        }

        Map<String, Object> detail = queryToolFailureDetail(response, failedQueryCall);
        MissingModelField missingField = extractMissingModelField(failedQueryCall);
        if (missingField != null && isUnstructuredTerminal(response)) {
            detail.put("reason", "model_field_not_found");
            detail.put("field_name", missingField.fieldName());
            detail.put("model_name", missingField.modelName());
            detail.put("terminal_contract", "field_not_found_in_query_model");
            return DatasetNLQueryResponse.reject(
                    FIELD_NOT_FOUND_IN_QUERY_MODEL_CODE,
                    "当前查询模型不包含请求字段，已拒绝执行。",
                    detail
            );
        }

        PostAggregateAliasFailure aliasFailure = extractPostAggregateAliasFailure(failedQueryCall);
        if (aliasFailure != null && isUnstructuredTerminal(response)) {
            detail.put("reason", "post_aggregate_alias_expression_unsupported");
            detail.put("terminal_contract", "post_aggregate_alias_expression_unsupported");
            if (aliasFailure.calculatedFieldName() != null) {
                detail.put("calculated_field_name", aliasFailure.calculatedFieldName());
            }
            if (aliasFailure.aliasName() != null) {
                detail.put("alias_name", aliasFailure.aliasName());
            }
            return DatasetNLQueryResponse.reject(
                    POST_AGGREGATE_ALIAS_EXPRESSION_UNSUPPORTED_CODE,
                    "当前查询包含尚不支持的聚合别名二次计算，已拒绝执行。",
                    detail
            );
        }

        if (isEmptyFilterFieldFailure(failedQueryCall) && isUnstructuredTerminal(response)) {
            detail.put("reason", "empty_filter_field");
            detail.put("terminal_contract", "empty_filter_field_in_query_model_filter");
            return DatasetNLQueryResponse.reject(
                    INVALID_QUERY_MODEL_FILTER_CODE,
                    "查询条件包含空字段，已拒绝执行。",
                    detail
            );
        }

        if (!"info".equals(response.getType())) {
            return response;
        }

        return DatasetNLQueryResponse.error(
                QUERY_MODEL_FAILED_CODE,
                "dataset.query_model 执行失败，未产生可验收的结构化查询结果。",
                detail
        );
    }

    private static boolean isUnstructuredTerminal(DatasetNLQueryResponse response) {
        return "info".equals(response.getType()) || "clarify".equals(response.getType());
    }

    private static Map<String, Object> queryToolFailureDetail(
            DatasetNLQueryResponse response,
            ToolCallCollector.ToolCallRecord failedQueryCall
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tool_name", safeString(failedQueryCall.getToolName()));
        detail.put("sequence", failedQueryCall.getSequence());
        detail.put("duration_ms", failedQueryCall.getDurationMs());
        detail.put("tool_error", safeString(failedQueryCall.getError()));
        detail.put("arguments_summary", argumentsSummary(failedQueryCall.getToolName(), failedQueryCall.getArguments()));
        detail.put("result_summary", resultSummary(failedQueryCall.getResult()));
        detail.put("original_type", response.getType());
        detail.put("original_topic", response.getTopic());
        detail.put("original_note", response.getNote());
        detail.put("original_data", response.getData());
        detail.put("original_questions", response.getQuestions());
        detail.put("original_candidates", response.getCandidates());
        return detail;
    }

    private static MissingModelField extractMissingModelField(ToolCallCollector.ToolCallRecord failedQueryCall) {
        List<String> candidates = new ArrayList<>();
        if (failedQueryCall.getError() != null) {
            candidates.add(failedQueryCall.getError());
        }
        Map<String, Object> resultSummary = resultSummary(failedQueryCall.getResult());
        Object message = resultSummary.get("message");
        if (message != null) {
            candidates.add(String.valueOf(message));
        }
        Object error = resultSummary.get("error");
        if (error != null) {
            candidates.add(String.valueOf(error));
        }
        for (String candidate : candidates) {
            Matcher matcher = FIELD_NOT_FOUND_IN_MODEL_PATTERN.matcher(candidate);
            if (matcher.find()) {
                return new MissingModelField(matcher.group(1), matcher.group(2));
            }
        }
        return null;
    }

    private record MissingModelField(String fieldName, String modelName) {
    }

    private static boolean isEmptyFilterFieldFailure(ToolCallCollector.ToolCallRecord failedQueryCall) {
        List<String> candidates = queryFailureCandidates(failedQueryCall);
        for (String candidate : candidates) {
            String normalized = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT);
            if (candidate != null && candidate.contains("field字段不能为空")) {
                return true;
            }
            if (normalized.contains("field field must not be blank")) {
                return true;
            }
        }
        return false;
    }

    private static PostAggregateAliasFailure extractPostAggregateAliasFailure(
            ToolCallCollector.ToolCallRecord failedQueryCall
    ) {
        List<String> candidates = queryFailureCandidates(failedQueryCall);
        for (String candidate : candidates) {
            String normalized = candidate == null ? "" : candidate;
            if (normalized.contains("POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED")
                    || normalized.contains("selected aggregate alias")
                    || (normalized.contains("CALCULATED_FIELD_EXPRESSION_INVALID")
                    && normalized.contains("未能在查询模型")
                    && normalized.contains("找到列"))) {
                return new PostAggregateAliasFailure(
                        firstMatch(CALCULATED_FIELD_NAME_PATTERN, normalized),
                        firstMatch(MISSING_CALCULATED_FIELD_COLUMN_PATTERN, normalized)
                );
            }
        }
        return null;
    }

    private static List<String> queryFailureCandidates(ToolCallCollector.ToolCallRecord failedQueryCall) {
        List<String> candidates = new ArrayList<>();
        if (failedQueryCall.getError() != null) {
            candidates.add(failedQueryCall.getError());
        }
        Map<String, Object> resultSummary = resultSummary(failedQueryCall.getResult());
        Object message = resultSummary.get("message");
        if (message != null) {
            candidates.add(String.valueOf(message));
        }
        Object error = resultSummary.get("error");
        if (error != null) {
            candidates.add(String.valueOf(error));
        }
        return candidates;
    }

    private static String firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record PostAggregateAliasFailure(String calculatedFieldName, String aliasName) {
    }

    private static DatasetNLQueryResponse enforceInfoTerminalContract(DatasetNLQueryResponse response) {
        if (response == null || !"info".equals(response.getType())) {
            return response;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("original_type", response.getType());
        detail.put("original_topic", response.getTopic());
        detail.put("original_note", response.getNote());
        detail.put("original_data", response.getData());
        detail.put("reason", "nl response did not produce a structured terminal result");

        String text = infoResponseText(response);
        if (looksLikeUnsupported(text)) {
            detail.put("terminal_contract", "unsupported_by_current_model_catalog");
            return DatasetNLQueryResponse.reject(
                    "UNSUPPORTED_BY_CURRENT_MODEL_CATALOG",
                    "当前模型目录或查询能力不支持该自然语言请求。",
                    detail
            );
        }

        detail.put("terminal_contract", "clarification_required_for_unstructured_response");
        return DatasetNLQueryResponse.clarify(
                List.of("本次自然语言查询没有产生可验收的结构化结果。请补充模型、字段或分析口径后重试。"),
                detail
        );
    }

    private static String infoResponseText(DatasetNLQueryResponse response) {
        List<String> parts = new ArrayList<>();
        if (response.getTopic() != null) {
            parts.add(response.getTopic());
        }
        if (response.getNote() != null) {
            parts.add(response.getNote());
        }
        Object data = response.getData();
        if (data instanceof Map<?, ?> map) {
            Object analysis = map.get("analysis");
            if (analysis != null) {
                parts.add(String.valueOf(analysis));
            }
        } else if (data != null) {
            parts.add(String.valueOf(data));
        }
        return String.join("\n", parts);
    }

    private static boolean looksLikeUnsupported(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return containsAny(text,
                "无法", "不能", "不支持", "没有接入", "未接入", "暂未接入", "没有配置",
                "未找到", "缺少", "不包含", "没有包含", "超出当前", "缺乏", "不存在")
                || containsAny(normalized, "unsupported", "not supported", "not found", "missing model", "missing field");
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static ToolCallCollector.ToolCallRecord lastFailedQueryModelCall(ToolCallCollector collector) {
        List<ToolCallCollector.ToolCallRecord> queryCalls = collector.getCallsByTool("dataset.query_model");
        for (int i = queryCalls.size() - 1; i >= 0; i--) {
            ToolCallCollector.ToolCallRecord call = queryCalls.get(i);
            if (call != null && !call.isSuccess()) {
                return call;
            }
        }
        return null;
    }

    private static boolean shouldExposeCalibrationDebug(RoutingCalibrationAction calibrationAction) {
        return calibrationAction.type() != RoutingCalibrationActionType.EXECUTE_RAW
                || calibrationAction.rawRoute() != null
                || calibrationAction.calibratedRoute() != null
                || !calibrationAction.appliedRules().isEmpty();
    }

    private static DatasetNLQueryResponse routingCalibrationReplanRequiredResponse(
            RoutingCalibrationAction calibrationAction,
            String traceId
    ) {
        String message = routingCalibrationReplanRequiredMessage(calibrationAction);
        return attachRoutingCalibrationDebug(DatasetNLQueryResponse.error(
                ROUTING_REPLAN_REQUIRED_CODE,
                message,
                routingCalibrationReplanRequiredDetail(calibrationAction)
        ), calibrationAction, traceId);
    }

    private static ProgressEvent routingCalibrationReplanRequiredEvent(RoutingCalibrationAction calibrationAction, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", ROUTING_REPLAN_REQUIRED_CODE);
        data.put("message", routingCalibrationReplanRequiredMessage(calibrationAction));
        data.put("recommended_action", REPLAN_RECOMMENDED_ACTION);
        data.put("replan_required", true);
        data.put("replan_dispatch", routingCalibrationReplanDispatch(calibrationAction));
        data.put("debug", Map.of(
                "trace_id", traceId != null ? traceId : "",
                "routing_calibration", calibrationAction.toAuditMap()
        ));

        return ProgressEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("error")
                .data(data)
                .build();
    }

    private static ProgressEvent routingCalibrationReplanDispatchEvent(RoutingCalibrationAction calibrationAction, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phase", "routing_replan");
        data.put("percent", 15);
        data.put("message", "按校准后路由重新规划");
        data.put("recommended_action", REPLAN_RECOMMENDED_ACTION);
        data.put("replan_required", true);
        data.put("replan_dispatch", routingCalibrationReplanDispatch(calibrationAction));
        data.put("debug", Map.of(
                "trace_id", traceId != null ? traceId : "",
                "routing_calibration", calibrationAction.toAuditMap()
        ));

        return ProgressEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("progress")
                .data(data)
                .build();
    }

    private static Map<String, Object> routingCalibrationReplanRequiredDetail(RoutingCalibrationAction calibrationAction) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", "REPLAN_REQUIRED");
        detail.put("recommended_action", REPLAN_RECOMMENDED_ACTION);
        detail.put("replan_required", true);
        detail.put("calibrated_route", safeString(calibrationAction.calibratedRoute()));
        detail.put("execution_allowed", false);
        detail.put("replan_dispatch", routingCalibrationReplanDispatch(calibrationAction));
        return detail;
    }

    private static Map<String, Object> routingCalibrationReplanDispatch(RoutingCalibrationAction calibrationAction) {
        Map<String, Object> dispatch = new LinkedHashMap<>();
        dispatch.put("route", safeString(calibrationAction.calibratedRoute()));
        dispatch.put("raw_route", safeString(calibrationAction.rawRoute()));
        dispatch.put("blocked_stale_plan", true);
        dispatch.put("allowed_after_replan", true);
        dispatch.put("stale_plan_fields", STALE_PLAN_FIELDS);
        dispatch.put("applied_rules", calibrationAction.appliedRules() != null ? calibrationAction.appliedRules() : List.of());
        dispatch.put("reason", safeString(calibrationAction.reason()));
        return dispatch;
    }

    private static List<String> registeredToolNames(List<McpTool> registeredTools) {
        if (registeredTools == null || registeredTools.isEmpty()) {
            return List.of();
        }
        return registeredTools.stream()
                .map(tool -> tool != null ? safeString(tool.getName()) : "")
                .toList();
    }

    private static Map<String, Object> toolCallSummary(ToolCallCollector.ToolCallRecord call) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sequence", call.getSequence());
        summary.put("tool_name", safeString(call.getToolName()));
        summary.put("spring_tool_name", safeString(call.getSpringToolName()));
        summary.put("success", call.isSuccess());
        summary.put("duration_ms", call.getDurationMs());
        if (call.getError() != null && !call.getError().isBlank()) {
            summary.put("error", call.getError());
        }
        summary.put("arguments_summary", argumentsSummary(call.getToolName(), call.getArguments()));
        summary.put("result_summary", resultSummary(call.getResult()));
        return summary;
    }

    private static Map<String, Object> argumentsSummary(String toolName, Map<String, Object> arguments) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (arguments == null || arguments.isEmpty()) {
            summary.put("argument_keys", List.of());
            return summary;
        }
        summary.put("argument_keys", new ArrayList<>(arguments.keySet()));
        Object model = arguments.get("model");
        if (model != null) {
            summary.put("model", model);
        }
        Object format = arguments.get("format");
        if (format != null) {
            summary.put("format", format);
        }
        Object mode = arguments.get("mode");
        if (mode != null) {
            summary.put("mode", mode);
        }
        Object payload = arguments.get("payload");
        if (payload instanceof Map<?, ?> payloadMap) {
            summary.put("payload_keys", payloadMap.keySet().stream().map(String::valueOf).toList());
            copyPayloadValue(summary, payloadMap, "columns", "payload_columns");
            copyPayloadValue(summary, payloadMap, "groupBy", "payload_group_by");
            copyPayloadValue(summary, payloadMap, "limit", "payload_limit");
            addPayloadCalculatedFieldsSummary(summary, payloadMap.get("calculatedFields"));
            summary.put("payload_has_slice", payloadMap.containsKey("slice"));
            addPayloadSliceSummary(summary, payloadMap.get("slice"));
        }
        if ("dataset.query_model".equals(toolName) || "dataset_query_model".equals(toolName)) {
            summary.putIfAbsent("payload_keys", List.of());
        }
        return summary;
    }

    private static void copyPayloadValue(
            Map<String, Object> summary,
            Map<?, ?> payloadMap,
            String sourceKey,
            String targetKey
    ) {
        Object value = payloadMap.get(sourceKey);
        if (value != null) {
            summary.put(targetKey, value);
        }
    }

    private static void addPayloadCalculatedFieldsSummary(Map<String, Object> summary, Object calculatedFields) {
        if (calculatedFields == null) {
            return;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        LinkedHashSet<String> expressions = new LinkedHashSet<>();
        collectPayloadCalculatedFieldSignals(calculatedFields, names, expressions);
        putSignalList(summary, "payload_calculated_field_names", names);
        putSignalList(summary, "payload_calculated_field_expressions", expressions);
    }

    private static void collectPayloadCalculatedFieldSignals(
            Object node,
            LinkedHashSet<String> names,
            LinkedHashSet<String> expressions
    ) {
        if (node instanceof Map<?, ?> map) {
            addStringSignal(names, firstMapValue(map, "name", "alias"));
            addStringSignal(expressions, firstMapValue(map, "expression", "formula"));
            return;
        }
        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectPayloadCalculatedFieldSignals(item, names, expressions);
            }
        }
    }

    private static void addPayloadSliceSummary(Map<String, Object> summary, Object slice) {
        if (slice == null) {
            return;
        }
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        LinkedHashSet<String> operators = new LinkedHashSet<>();
        LinkedHashSet<String> fieldRefs = new LinkedHashSet<>();
        LinkedHashSet<String> booleanGroups = new LinkedHashSet<>();
        collectPayloadSliceSignals(slice, fields, operators, fieldRefs, booleanGroups);
        putSignalList(summary, "payload_slice_fields", fields);
        putSignalList(summary, "payload_slice_ops", operators);
        putSignalList(summary, "payload_slice_field_refs", fieldRefs);
        putSignalList(summary, "payload_slice_boolean_groups", booleanGroups);
    }

    private static void collectPayloadSliceSignals(
            Object node,
            LinkedHashSet<String> fields,
            LinkedHashSet<String> operators,
            LinkedHashSet<String> fieldRefs,
            LinkedHashSet<String> booleanGroups
    ) {
        if (node instanceof Map<?, ?> map) {
            addStringSignal(fields, firstMapValue(map, "field", "left", "leftField", "column"));
            addStringSignal(operators, firstMapValue(map, "op", "operator", "operation"));

            Object value = firstMapValue(map, "value", "right", "rightValue");
            collectFieldRef(value, fieldRefs);

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalizedKey = normalizeBooleanGroupKey(key);
                if (normalizedKey != null) {
                    addStringSignal(booleanGroups, normalizedKey);
                }
                collectPayloadSliceSignals(entry.getValue(), fields, operators, fieldRefs, booleanGroups);
            }
            return;
        }
        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectPayloadSliceSignals(item, fields, operators, fieldRefs, booleanGroups);
            }
        }
    }

    private static Object firstMapValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static void collectFieldRef(Object value, LinkedHashSet<String> fieldRefs) {
        if (value instanceof Map<?, ?> map) {
            addStringSignal(fieldRefs, firstMapValue(map, "$field", "fieldRef", "field_ref"));
            for (Object nestedValue : map.values()) {
                collectFieldRef(nestedValue, fieldRefs);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectFieldRef(item, fieldRefs);
            }
        }
    }

    private static String normalizeBooleanGroupKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "$or", "or", "any" -> "or";
            case "$and", "and", "all" -> "and";
            default -> null;
        };
    }

    private static void addStringSignal(LinkedHashSet<String> target, Object value) {
        if (value == null || target.size() >= QUERY_TRACE_PAYLOAD_SLICE_SIGNAL_LIMIT) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            target.add(text);
        }
    }

    private static void putSignalList(Map<String, Object> summary, String key, LinkedHashSet<String> values) {
        if (!values.isEmpty()) {
            summary.put(key, new ArrayList<>(values));
        }
    }

    private static Map<String, Object> resultSummary(Object result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (result == null) {
            summary.put("type", "null");
            return summary;
        }
        if (result instanceof Map<?, ?> map) {
            summary.put("type", "map");
            summary.put("keys", map.keySet().stream().map(String::valueOf).toList());
            Object error = map.get("error");
            if (error != null) {
                summary.put("error", error);
            }
            Object errorType = map.get("error_type");
            if (errorType != null) {
                summary.put("error_type", errorType);
            }
            Object message = map.get("message");
            if (message != null) {
                summary.put("message", message);
            }
            Object retryGuidance = map.get("retry_guidance");
            if (retryGuidance instanceof Map<?, ?> guidanceMap) {
                Object action = guidanceMap.get("action");
                if (action != null) {
                    summary.put("retry_guidance_action", action);
                }
                Object repeatCount = guidanceMap.get("failure_signature_repeat_count");
                if (repeatCount != null) {
                    summary.put("failure_signature_repeat_count", repeatCount);
                }
            }
            Object failureSignature = map.get("failure_signature");
            if (failureSignature != null) {
                summary.put("failure_signature", failureSignature);
            }
            Object total = map.get("total");
            if (total != null) {
                summary.put("total", total);
            }
            Object items = map.get("items");
            if (items instanceof List<?> itemList) {
                summary.put("item_count", itemList.size());
            }
            return summary;
        }
        if (result instanceof RX<?> rx) {
            summary.put("type", "RX");
            summary.put("code", rx.getCode());
            summary.put("success", rx._isSuccess());
            String message = firstNonBlank(rx.getMsg(), rx.getUserTip(), rx.getExCode());
            if (message != null) {
                summary.put("message", message);
            }
            Object data = rx.getData();
            if (data instanceof SemanticQueryResponse semanticResponse) {
                summary.put("data_type", data.getClass().getSimpleName());
                summary.putAll(queryResultSummary(semanticQueryResponseToMap(semanticResponse)));
            } else if (data != null) {
                summary.put("data_type", data.getClass().getSimpleName());
            }
            return summary;
        }
        summary.put("type", result.getClass().getSimpleName());
        summary.put("string_length", String.valueOf(result).length());
        return summary;
    }

    private static Map<String, Object> queryResultSummary(Map<String, Object> capturedQueryResult) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Object total = capturedQueryResult.get("total");
        if (total != null) {
            summary.put("query_result_total", total);
        }
        Object items = capturedQueryResult.get("items");
        if (items instanceof List<?> itemList) {
            summary.put("query_result_item_count", itemList.size());
        }
        Object error = capturedQueryResult.get("error");
        if (error != null) {
            summary.put("query_result_error", error);
        }
        return summary;
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String routingCalibrationReplanRequiredMessage(RoutingCalibrationAction calibrationAction) {
        if (calibrationAction.calibratedRoute() == null || calibrationAction.calibratedRoute().isBlank()) {
            return "路由校准要求重新规划，但缺少 calibrated_route，已阻断旧计划执行。";
        }
        return "路由校准要求按 " + calibrationAction.calibratedRoute()
                + " 重新规划，当前入口已阻断旧计划分析和工具执行。";
    }

    /**
     * 解析 AI 响应为结构化结果
     */
    private DatasetNLQueryResponse parseResponse(String aiResponse, SessionContext context, String traceId) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return DatasetNLQueryResponse.error("EMPTY_RESPONSE", "AI 未返回有效响应", null);
        }

        // 尝试从上下文获取查询结果
        if (context.getLastQueryResult() != null) {
            return buildResultFromContext(context, aiResponse);
        }

        // 检查是否是澄清请求
        if (aiResponse.contains("请问") || aiResponse.contains("您是指") ||
            aiResponse.contains("需要确认") || aiResponse.contains("请选择")) {
            return DatasetNLQueryResponse.clarify(
                    List.of(aiResponse),
                    null
            );
        }

        // 默认返回 AI 分析结果
        return DatasetNLQueryResponse.info(
                "ai_analysis",
                Map.of("analysis", aiResponse),
                "AI 分析完成"
        );
    }

    /**
     * 从上下文构建结果响应
     */
    @SuppressWarnings("unchecked")
    private DatasetNLQueryResponse buildResultFromContext(SessionContext context, String summary) {
        Map<String, Object> queryResult = context.getLastQueryResult();

        List<Map<String, Object>> items = (List<Map<String, Object>>) queryResult.getOrDefault("items", List.of());
        Long total = queryResult.get("total") != null ?
                ((Number) queryResult.get("total")).longValue() : (long) items.size();

        return DatasetNLQueryResponse.builder()
                .type("result")
                .items(items)
                .total(total)
                .summary(summary)
                .hasNext(items.size() >= 20)
                .build();
    }

    /**
     * 清理会话
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    // ========== 内部类 ==========

    /**
     * 会话上下文
     *
     * <p>{@code lastQueryResult} 由 {@link #captureQueryResult} + {@link #processQuery} 协作写入，
     * 记录本次对话中最后一次 dataset.query_model 调用的结构化返回值，
     * 供 {@link #parseResponse} 构建结构化响应。
     */
    public static class SessionContext {
        private final String sessionId;
        private String traceId;
        private String authorization;
        private Map<String, Object> lastQueryResult;

        public SessionContext(String sessionId, String traceId) {
            this.sessionId = sessionId;
            this.traceId = traceId;
        }

        public String getSessionId() { return sessionId; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getAuthorization() { return authorization; }
        public void setAuthorization(String authorization) { this.authorization = authorization; }
        public Map<String, Object> getLastQueryResult() { return lastQueryResult; }
        public void setLastQueryResult(Map<String, Object> result) { this.lastQueryResult = result; }
    }

    // ========== 工具调用方法（供 AiFunctionConfig 调用，保留兼容性）==========

    /**
     * 获取元数据
     *
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 元数据
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchMetadata(String traceId, String authorization) {
        log.info("Fetching metadata via DatasetAccessor, traceId={}", traceId);
        Object response = datasetAccessor.getMetadata(traceId, authorization, null);
        if (response instanceof Map) {
            return (Map<String, Object>) response;
        }
        return Map.of("data", response);
    }

    /**
     * 获取模型详情
     *
     * @param model         模型名称
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 模型详情
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchModelDescription(String model, String traceId, String authorization) {
        log.info("Describing model via DatasetAccessor: {}, traceId={}", model, traceId);
        Object response = datasetAccessor.describeModel(model, "json", traceId, authorization, null);
        if (response instanceof Map) {
            return (Map<String, Object>) response;
        }
        return Map.of("data", response);
    }

    /**
     * 执行查询
     *
     * @param model         模型名称
     * @param payload       查询参数
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 查询结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeQuery(String model, Map<String, Object> payload, String traceId, String authorization) {
        log.info("Querying model via DatasetAccessor: {}, traceId={}", model, traceId);
        Object response = datasetAccessor.queryModel(model, payload, "execute", traceId, authorization, null);
        if (response instanceof Map) {
            return (Map<String, Object>) response;
        }
        return Map.of("data", response);
    }
}
