package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.audit.ToolAuditService;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.tools.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 工具分发器
 *
 * 负责：
 * - 注册和管理 MCP 工具
 * - 分发工具调用请求
 * - 生成工具定义列表（从配置文件加载描述和Schema）
 * - 审计日志记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolDispatcher {

    private final List<McpTool> tools;
    private final ToolConfigLoader toolConfigLoader;

    /**
     * 审计日志服务（可选，仅在启用审计时注入）
     */
    @Autowired(required = false)
    private ToolAuditService auditService;

    private final Map<String, McpTool> toolRegistry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 注册所有工具（根据配置过滤）
        int skipped = 0;
        for (McpTool tool : tools) {
            String toolName = tool.getName();
            // 检查工具是否在配置中启用
            if (!toolConfigLoader.isEnabled(toolName)) {
                log.info("Skipped disabled MCP tool: {}", toolName);
                skipped++;
                continue;
            }
            toolRegistry.put(toolName, tool);
            log.info("Registered MCP tool: {}", toolName);
        }
        log.info("Total {} MCP tools registered ({} skipped)", toolRegistry.size(), skipped);
    }

    /**
     * 获取所有工具定义
     *
     * 优先从配置文件加载描述和Schema，如果配置不存在则使用工具类的默认实现
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();

        for (McpTool tool : toolRegistry.values()) {
            String toolName = tool.getName();

            // 获取描述：优先从配置加载，否则使用工具的默认描述
            String description = toolConfigLoader.getDescription(toolName);
            if (description == null || description.isBlank()) {
                description = tool.getDescription();
            }
            if (description == null || description.isBlank()) {
                description = "No description available for " + toolName;
            }

            // 获取Schema：优先从配置加载，否则使用工具的默认Schema
            Map<String, Object> inputSchema = toolConfigLoader.getSchema(toolName);
            if (inputSchema == null || inputSchema.isEmpty()) {
                inputSchema = tool.getInputSchema();
            }
            if (inputSchema == null) {
                inputSchema = Map.of("type", "object", "properties", Map.of());
            }

            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("name", toolName);
            definition.put("description", description);
            definition.put("inputSchema", inputSchema);
            definitions.add(definition);
        }

        return definitions;
    }

    /**
     * 执行工具（同步）- 简化版
     *
     * @param toolName      工具名称
     * @param arguments     工具参数
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 执行结果
     */
    public Object executeTool(String toolName, Map<String, Object> arguments, String traceId, String authorization) {
        return executeTool(toolName, arguments, traceId, null, authorization, null);
    }

    /**
     * 执行工具（同步）- 完整版，支持审计
     *
     * @param toolName      工具名称
     * @param arguments     工具参数
     * @param traceId       AI 会话追踪 ID
     * @param requestId     HTTP 请求 ID
     * @param authorization 授权令牌
     * @param userRole      用户角色
     * @return 执行结果
     */
    public Object executeTool(String toolName, Map<String, Object> arguments, String traceId,
                              String requestId, String authorization, String userRole) {
        McpTool tool = toolRegistry.get(toolName);

        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        log.info("Executing tool: name={}, traceId={}, requestId={}", toolName, traceId, requestId);
        long startTime = System.currentTimeMillis();

        try {
            Object result = tool.execute(arguments, traceId, authorization);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Tool executed successfully: name={}, duration={}ms, traceId={}",
                    toolName, duration, traceId);

            // 审计日志 - 成功
            if (auditService != null) {
                String resultSummary = buildResultSummary(result);
                auditService.logSuccess(traceId, requestId, toolName, arguments,
                        authorization, userRole, duration, resultSummary);
            }

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Tool execution failed: name={}, duration={}ms, error={}, traceId={}",
                    toolName, duration, e.getMessage(), traceId, e);

            // 审计日志 - 失败
            if (auditService != null) {
                auditService.logFailure(traceId, requestId, toolName, arguments,
                        authorization, userRole, duration, "EXECUTION_ERROR", e.getMessage());
            }

            throw e;
        }
    }

    /**
     * 构建结果摘要
     */
    @SuppressWarnings("unchecked")
    private String buildResultSummary(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            // 尝试提取常见的结果信息
            if (map.containsKey("items")) {
                Object items = map.get("items");
                if (items instanceof List) {
                    int count = ((List<?>) items).size();
                    Object total = map.get("total");
                    if (total != null) {
                        return "返回 " + count + " 条数据（共 " + total + " 条）";
                    }
                    return "返回 " + count + " 条数据";
                }
            }
            if (map.containsKey("error") && Boolean.TRUE.equals(map.get("error"))) {
                return "错误: " + map.get("message");
            }
            if (map.containsKey("success")) {
                return "success=" + map.get("success");
            }
        }
        String str = result.toString();
        if (str.length() > 100) {
            return str.substring(0, 100) + "...";
        }
        return str;
    }

    /**
     * 执行工具（带进度流）
     *
     * @param request       MCP请求
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 进度事件流
     */
    public Flux<ProgressEvent> executeWithProgress(McpRequest request, String traceId, String authorization) {
        Map<String, Object> params = request.getParams();
        if (params == null) {
            return Flux.just(ProgressEvent.error("INVALID_PARAMS", "Missing params"));
        }

        String toolName;
        Map<String, Object> arguments;

        // 处理 tools/call 格式
        if (params.containsKey("name")) {
            toolName = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());
            arguments = args;
        } else {
            // 直接调用格式
            toolName = request.getMethod();
            arguments = params;
        }

        McpTool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return Flux.just(ProgressEvent.error("TOOL_NOT_FOUND", "Unknown tool: " + toolName));
        }

        // 检查工具是否支持流式执行
        if (tool.supportsStreaming()) {
            return tool.executeWithProgress(arguments, traceId, authorization);
        }

        // 不支持流式的工具，包装为单个完成事件
        return Flux.create(sink -> {
            try {
                sink.next(ProgressEvent.progress("executing", 50));
                Object result = tool.execute(arguments, traceId, authorization);
                sink.next(ProgressEvent.complete(result));
                sink.complete();
            } catch (Exception e) {
                sink.next(ProgressEvent.error("EXECUTION_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return false;
        }
        return toolRegistry.containsKey(toolName);
    }

    /**
     * 获取指定工具对象
     */
    public McpTool getTool(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return null;
        }
        return toolRegistry.get(toolName);
    }

    /**
     * 获取所有工具对象列表
     */
    public List<McpTool> getAllTools() {
        return new ArrayList<>(toolRegistry.values());
    }
    public List<McpTool> getDataAnalysisTools() {
        return toolRegistry.values().stream().filter(tool -> !tool.getName().equals("dataset_nl.query")).collect(Collectors.toList());
    }
}
