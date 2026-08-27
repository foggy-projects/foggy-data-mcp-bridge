package com.foggyframework.dataset.mcp.service;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpRequestContext;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.mcp.spi.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 核心业务服务
 *
 * 封装 MCP 协议的核心业务逻辑，供不同的 Controller 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpService {

    private final McpToolDispatcher toolDispatcher;
    private final ToolFilterService toolFilterService;
    private final NamespaceToolPolicyService namespaceToolPolicyService;

    /**
     * 处理 MCP initialize 请求
     */
    public McpResponse handleInitialize(McpRequest request, UserRole userRole) {
        Map<String, Object> result = new HashMap<>();
        Object requestedVersion = request.getParams() == null
                ? null
                : request.getParams().get("protocolVersion");
        result.put("protocolVersion", McpProtocolVersions.negotiateLegacy(requestedVersion));
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", true),
                "logging", Map.of()
        ));
        result.put("serverInfo", Map.of(
                "name", "mcp-data-model-java",
                "version", "1.0.0",
                "userRole", userRole.name(),
                "roleDescription", userRole.getDescription()
        ));

        log.info("MCP initialized successfully for role: {}", userRole);
        return McpResponse.success(request.getId(), result);
    }

    /** Stateless discovery used by MCP 2026-07-28 clients before direct requests. */
    public McpResponse handleServerDiscover(McpRequest request, UserRole userRole) {
        Map<String, Object> result = new HashMap<>();
        result.put("supportedVersions", McpProtocolVersions.SUPPORTED);
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", true),
                "logging", Map.of()));
        result.put("serverInfo", Map.of(
                "name", "foggy-data-mcp",
                "version", "current-main",
                "userRole", userRole.name(),
                "roleDescription", userRole.getDescription()));
        result.put("ttlMs", 30_000);
        result.put("cacheScope", "role:" + userRole.name().toLowerCase());
        return McpResponse.success(request.getId(), result);
    }

    /**
     * 处理 tools/list 请求（根据用户角色过滤）
     */
    public McpResponse handleToolsList(McpRequest request, UserRole userRole) {
        return handleToolsList(request, McpRequestContext.builder().userRole(userRole).build());
    }

    /**
     * 处理 tools/list 请求，并应用 namespace 级的动态工具策略。
     */
    public McpResponse handleToolsList(McpRequest request, McpRequestContext context) {
        UserRole userRole = context.getUserRole();
        // 获取所有工具定义
        List<Map<String, Object>> allToolDefinitions = toolDispatcher.getToolDefinitions();

        // 获取所有工具对象（用于过滤）
        List<McpTool> allTools = toolDispatcher.getAllTools();

        // 根据用户角色过滤
        List<Map<String, Object>> filteredDefinitions = toolFilterService.filterToolDefinitionsByRole(
                allToolDefinitions,
                allTools,
                userRole
        );

        List<String> registeredNames = allTools.stream().map(McpTool::getName).toList();
        var availableNames = namespaceToolPolicyService.resolveAvailableTools(
                registeredNames,
                context.getNamespace(),
                context.getAuthorization(),
                userRole != null ? userRole.name() : null,
                context.getTraceId(),
                context.getHeaders());
        filteredDefinitions = filteredDefinitions.stream()
                .filter(definition -> availableNames.contains(String.valueOf(definition.get("name"))))
                .toList();

        log.info("tools/list for role {}: {} tools available", userRole, filteredDefinitions.size());

        return McpResponse.success(request.getId(), Map.of("tools", filteredDefinitions));
    }

    /**
     * 处理 tools/call 请求
     *
     * @param request MCP请求
     * @param context 请求上下文
     * @return MCP响应
     */
    public McpResponse handleToolsCall(McpRequest request, McpRequestContext context) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("name")) {
            return McpResponse.error(
                    request.getId(),
                    McpError.INVALID_PARAMS,
                    "Missing tool name"
            );
        }

        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());

        // 检查用户是否有权限访问该工具
        if (!canAccessTool(toolName, context)) {
            log.warn("User role {} attempted to access unauthorized tool: {}", context.getUserRole(), toolName);
            return McpResponse.error(
                    request.getId(),
                    McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: " + toolName
            );
        }

        log.info("Executing tool: name={}, role={}, traceId={}, requestId={}, namespace={}",
                toolName, context.getUserRole(), context.getTraceId(), context.getRequestId(), context.getNamespace());

        try {
            Object result = toolDispatcher.executeTool(toolName, arguments, context.getTraceId(), context.getRequestId(),
                    context.getAuthorization(), context.getUserRole().name(), context.getNamespace(),
                    context.getHeaders());

            return McpResponse.success(request.getId(), buildToolsCallResult(toolName, result));

        } catch (Exception e) {
            log.error("Tool execution failed: name={}, role={}, error={}", toolName, context.getUserRole(), e.getMessage(), e);
            return McpResponse.error(
                    request.getId(),
                    McpError.TOOL_EXECUTION_ERROR,
                    "Tool execution failed: " + e.getMessage()
            );
        }
    }

    /**
     * 处理 tools/call 请求（兼容旧接口）
     *
     * @deprecated 使用 {@link #handleToolsCall(McpRequest, McpRequestContext)} 替代
     */
    @Deprecated
    public McpResponse handleToolsCall(McpRequest request, UserRole userRole, String traceId,
                                        String requestId, String authorization, String namespace) {
        McpRequestContext context = McpRequestContext.of(traceId, requestId, authorization, userRole, namespace);
        return handleToolsCall(request, context);
    }

    /**
     * 处理直接工具调用（方法名即工具名）
     *
     * @param request MCP请求
     * @param context 请求上下文
     * @return MCP响应
     */
    public McpResponse handleDirectToolCall(McpRequest request, McpRequestContext context) {
        String toolName = request.getMethod();
        Map<String, Object> arguments = request.getParams() != null ? request.getParams() : new HashMap<>();

        // 检查用户是否有权限访问该工具
        if (!canAccessTool(toolName, context)) {
            log.warn("User role {} attempted to access unauthorized tool: {}", context.getUserRole(), toolName);
            return McpResponse.error(
                    request.getId(),
                    McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: " + toolName
            );
        }

        log.info("Direct tool call: name={}, role={}, traceId={}, requestId={}, namespace={}",
                toolName, context.getUserRole(), context.getTraceId(), context.getRequestId(), context.getNamespace());

        try {
            Object result = toolDispatcher.executeTool(toolName, arguments, context.getTraceId(), context.getRequestId(),
                    context.getAuthorization(), context.getUserRole().name(), context.getNamespace(),
                    context.getHeaders());
            return McpResponse.success(request.getId(), result);
        } catch (Exception e) {
            log.error("Direct tool call failed: name={}, role={}, error={}", toolName, context.getUserRole(), e.getMessage(), e);
            return McpResponse.error(
                    request.getId(),
                    McpError.TOOL_EXECUTION_ERROR,
                    e.getMessage()
            );
        }
    }

    /**
     * 处理直接工具调用（兼容旧接口）
     *
     * @deprecated 使用 {@link #handleDirectToolCall(McpRequest, McpRequestContext)} 替代
     */
    @Deprecated
    public McpResponse handleDirectToolCall(McpRequest request, UserRole userRole, String traceId,
                                             String requestId, String authorization, String namespace) {
        McpRequestContext context = McpRequestContext.of(traceId, requestId, authorization, userRole, namespace);
        return handleDirectToolCall(request, context);
    }

    /**
     * 处理直接工具调用（兼容旧接口，无 requestId）
     *
     * @deprecated 使用 {@link #handleDirectToolCall(McpRequest, McpRequestContext)} 替代
     */
    @Deprecated
    public McpResponse handleDirectToolCall(McpRequest request, UserRole userRole, String traceId, String authorization) {
        return handleDirectToolCall(request, userRole, traceId, null, authorization, null);
    }

    /**
     * 处理 ping 请求
     */
    public McpResponse handlePing(McpRequest request) {
        return McpResponse.success(request.getId(), Map.of("status", "pong"));
    }

    /**
     * 检查用户角色是否可以访问指定工具
     */
    private boolean canAccessTool(String toolName, McpRequestContext context) {
        if (!toolDispatcher.hasTool(toolName)) {
            return false;
        }

        UserRole userRole = context.getUserRole();

        // 管理员可以访问所有工具
        // 获取工具对象
        McpTool tool = toolDispatcher.getTool(toolName);
        if (tool == null) {
            return false;
        }

        // 先保持既有角色过滤，再应用 namespace 级动态策略。管理员仍受
        // namespace 配置约束，这是管理员主动配置的工具暴露边界。
        boolean roleAllowed = userRole == UserRole.ADMIN || toolFilterService.canAccessTool(tool, userRole);
        if (!roleAllowed) {
            return false;
        }
        List<String> registeredNames = toolDispatcher.getAllTools().stream().map(McpTool::getName).toList();
        return namespaceToolPolicyService.isAvailable(
                toolName,
                registeredNames,
                context.getNamespace(),
                context.getAuthorization(),
                userRole != null ? userRole.name() : null,
                context.getTraceId(),
                context.getHeaders());
    }

    /**
     * 构建 MCP tools/call 结果。
     *
     * <p>仅对 dataset.query_model 注入结构化状态，其他工具保持现有 content 兼容行为。</p>
     */
    private Map<String, Object> buildToolsCallResult(String toolName, Object result) {
        Map<String, Object> toolCallResult = new HashMap<>();
        toolCallResult.put("content", List.of(Map.of(
                "type", "text",
                "text", resolveContentText(toolName, result)
        )));

        if (isQueryModelTool(toolName)) {
            toolCallResult.put("status", resolveQueryModelStatus(result));
        }

        return toolCallResult;
    }

    private boolean isQueryModelTool(String toolName) {
        return "dataset.query_model".equals(toolName);
    }

    private String resolveQueryModelStatus(Object result) {
        if (result instanceof RX<?> rx) {
            return rx._isSuccess() ? "success" : "failed";
        }
        if (result instanceof Map<?, ?> resultMap) {
            Object status = resultMap.get("status");
            if ("failed".equals(status)) {
                return "failed";
            }
            if ("success".equals(status)) {
                return "success";
            }

            Object code = resultMap.get("code");
            if (code instanceof Number number) {
                return number.intValue() == RX.SUCCESS ? "success" : "failed";
            }
        }
        return "success";
    }

    private String resolveContentText(String toolName, Object result) {
        if (isQueryModelTool(toolName) && result instanceof RX<?> rx && !rx._isSuccess()) {
            String errorText = firstNonBlank(rx.getMsg(), rx.getUserTip());
            if (errorText != null) {
                return errorText;
            }
        }
        return result instanceof String ? result.toString() : toJsonString(result);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 将对象转换为 JSON 字符串
     */
    private String toJsonString(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
