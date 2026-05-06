package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryRequest;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryResponse;
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

    /**
     * 由 {@link McpToolCallbackFactory} 调用，将 dataset.query_model 的结构化结果写入当前线程捕获槽。
     */
    @SuppressWarnings("unchecked")
    public static void captureQueryResult(Object result) {
        if (result instanceof Map<?, ?> map) {
            LAST_QUERY_RESULT.set((Map<String, Object>) map);
        }
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
            McpToolCallbackFactory toolCallbackFactory
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.datasetAccessor = datasetAccessor;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
        this.mcpToolDispatcher = mcpToolDispatcher;
        this.toolCallbackFactory = toolCallbackFactory;

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
            // 获取或创建会话上下文
            SessionContext context = sessions.computeIfAbsent(sessionId,
                    k -> new SessionContext(sessionId, traceId));
            context.setTraceId(traceId);
            context.setAuthorization(authorization);

            // 构建用户消息
            String userMessage = buildUserMessage(request);

            // 获取核心查询工具并转换为 ToolCallback
            List<McpTool> queryTools = getQueryTools();
            ToolCallback[] toolCallbacks = toolCallbackFactory.createToolCallbacks(queryTools, traceId, authorization);

            log.info("Registered {} tools for query, traceId={}", toolCallbacks.length, traceId);

            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(SYSTEM_PROMPT)
                    .build();

            // Spring AI 在单次 call() 内会自动驱动完整的工具调用链
            // （LLM 发起 tool call → 框架执行工具 → 结果回传 LLM → LLM 生成最终回复），
            // 无需外层 while 循环手动重试。
            String aiResponse = chatClient.prompt()
                    .user(userMessage)
                    .tools(toolCallbacks)
                    .call()
                    .content();

            // 读取 McpToolCallbackFactory 在工具执行期间写入的结构化查询结果
            Map<String, Object> captured = LAST_QUERY_RESULT.get();
            if (captured != null) {
                context.setLastQueryResult(captured);
            }

            log.debug("AI response, traceId={}, length={}", traceId, aiResponse != null ? aiResponse.length() : 0);
            return parseResponse(aiResponse, context, traceId);

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

                String sessionId = request.getSessionId() != null ?
                        request.getSessionId() : UUID.randomUUID().toString();

                SessionContext context = sessions.computeIfAbsent(sessionId,
                        k -> new SessionContext(sessionId, traceId));
                context.setTraceId(traceId);
                context.setAuthorization(authorization);

                String userMessage = buildUserMessage(request);
                sink.next(ProgressEvent.progress("plan", 20));

                List<McpTool> queryTools = getQueryTools();
                ToolCallback[] toolCallbacks = toolCallbackFactory.createToolCallbacks(queryTools, traceId, authorization);

                ChatClient chatClient = chatClientBuilder
                        .defaultSystem(SYSTEM_PROMPT)
                        .build();

                sink.next(ProgressEvent.progress("tool_call", 40));

                // Spring AI 单次 call() 内自动驱动完整工具调用链
                String aiResponse = chatClient.prompt()
                        .user(userMessage)
                        .tools(toolCallbacks)
                        .call()
                        .content();

                // 读取工具执行期间捕获的结构化查询结果
                Map<String, Object> captured = LAST_QUERY_RESULT.get();
                if (captured != null) {
                    context.setLastQueryResult(captured);
                }

                sink.next(ProgressEvent.progress("format", 90));

                DatasetNLQueryResponse result = parseResponse(aiResponse, context, traceId);
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

        return sb.toString();
    }

    private String getDayOfWeekChinese(int dayOfWeek) {
        return new String[]{"一", "二", "三", "四", "五", "六", "日"}[dayOfWeek - 1];
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
