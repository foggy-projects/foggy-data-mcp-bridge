package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.dataset.mcp.tools.McpTool;
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

    /**
     * 处理 MCP initialize 请求
     */
    public McpResponse handleInitialize(McpRequest request, UserRole userRole) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
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

    /**
     * 处理 tools/list 请求（根据用户角色过滤）
     */
    public McpResponse handleToolsList(McpRequest request, UserRole userRole) {
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

        log.info("tools/list for role {}: {} tools available", userRole, filteredDefinitions.size());

        return McpResponse.success(request.getId(), Map.of("tools", filteredDefinitions));
    }

    /**
     * 处理 tools/call 请求
     *
     * @param request       MCP请求
     * @param userRole      用户角色
     * @param traceId       AI 会话追踪 ID
     * @param requestId     HTTP 请求 ID
     * @param authorization 授权令牌
     * @return MCP响应
     */
    public McpResponse handleToolsCall(McpRequest request, UserRole userRole, String traceId,
                                        String requestId, String authorization) {
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
        if (!canAccessTool(toolName, userRole)) {
            log.warn("User role {} attempted to access unauthorized tool: {}", userRole, toolName);
            return McpResponse.error(
                    request.getId(),
                    McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: " + toolName
            );
        }

        log.info("Executing tool: name={}, role={}, traceId={}, requestId={}", toolName, userRole, traceId, requestId);

        try {
            Object result = toolDispatcher.executeTool(toolName, arguments, traceId, requestId,
                    authorization, userRole.name());

            // MCP 规范要求返回 content 数组
            return McpResponse.success(request.getId(), Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", result instanceof String ? result : toJsonString(result)
                    ))
            ));

        } catch (Exception e) {
            log.error("Tool execution failed: name={}, role={}, error={}", toolName, userRole, e.getMessage(), e);
            return McpResponse.error(
                    request.getId(),
                    McpError.TOOL_EXECUTION_ERROR,
                    "Tool execution failed: " + e.getMessage()
            );
        }
    }

    /**
     * 处理 tools/call 请求（兼容旧接口，无 requestId）
     */
    public McpResponse handleToolsCall(McpRequest request, UserRole userRole, String traceId, String authorization) {
        return handleToolsCall(request, userRole, traceId, null, authorization);
    }

    /**
     * 处理直接工具调用（方法名即工具名）
     *
     * @param request       MCP请求
     * @param userRole      用户角色
     * @param traceId       AI 会话追踪 ID
     * @param requestId     HTTP 请求 ID
     * @param authorization 授权令牌
     * @return MCP响应
     */
    public McpResponse handleDirectToolCall(McpRequest request, UserRole userRole, String traceId,
                                             String requestId, String authorization) {
        String toolName = request.getMethod();
        Map<String, Object> arguments = request.getParams() != null ? request.getParams() : new HashMap<>();

        // 检查用户是否有权限访问该工具
        if (!canAccessTool(toolName, userRole)) {
            log.warn("User role {} attempted to access unauthorized tool: {}", userRole, toolName);
            return McpResponse.error(
                    request.getId(),
                    McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: " + toolName
            );
        }

        log.info("Direct tool call: name={}, role={}, traceId={}, requestId={}", toolName, userRole, traceId, requestId);

        try {
            Object result = toolDispatcher.executeTool(toolName, arguments, traceId, requestId,
                    authorization, userRole.name());
            return McpResponse.success(request.getId(), result);
        } catch (Exception e) {
            log.error("Direct tool call failed: name={}, role={}, error={}", toolName, userRole, e.getMessage(), e);
            return McpResponse.error(
                    request.getId(),
                    McpError.TOOL_EXECUTION_ERROR,
                    e.getMessage()
            );
        }
    }

    /**
     * 处理直接工具调用（兼容旧接口，无 requestId）
     */
    public McpResponse handleDirectToolCall(McpRequest request, UserRole userRole, String traceId, String authorization) {
        return handleDirectToolCall(request, userRole, traceId, null, authorization);
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
    private boolean canAccessTool(String toolName, UserRole userRole) {
        if (!toolDispatcher.hasTool(toolName)) {
            return false;
        }

        // 管理员可以访问所有工具
        if (userRole == UserRole.ADMIN) {
            return true;
        }

        // 获取工具对象
        McpTool tool = toolDispatcher.getTool(toolName);
        if (tool == null) {
            return false;
        }

        // 使用过滤服务检查权限
        return toolFilterService.canAccessTool(tool, userRole);
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
