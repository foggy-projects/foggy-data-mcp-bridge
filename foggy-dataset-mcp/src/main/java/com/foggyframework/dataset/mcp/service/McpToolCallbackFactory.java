package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.tools.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具回调工厂
 *
 * 将 McpTool 转换为 Spring AI 的 ToolCallback，
 * 工具描述和参数 Schema 从 ToolConfigLoader 获取（即 schemas/ 目录下的配置文件）
 *
 * <p>这样测试代码和业务代码可以共用相同的工具定义。
 *
 * <p>支持工具调用收集器 {@link ToolCallCollector}，用于记录工具调用的详细信息。
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolCallbackFactory {

    private final ToolConfigLoader toolConfigLoader;
    private final ObjectMapper objectMapper;

    /**
     * 将 McpTool 转换为 Spring AI ToolCallback
     *
     * @param mcpTool       MCP 工具实例
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return Spring AI ToolCallback
     */
    public ToolCallback createToolCallback(McpTool mcpTool, String traceId, String authorization) {
        return createToolCallback(mcpTool, traceId, authorization, null);
    }

    /**
     * 将 McpTool 转换为 Spring AI ToolCallback（带收集器）
     *
     * @param mcpTool       MCP 工具实例
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @param collector     工具调用收集器（可选）
     * @return Spring AI ToolCallback
     */
    public ToolCallback createToolCallback(McpTool mcpTool, String traceId, String authorization, ToolCallCollector collector) {
        String toolName = mcpTool.getName();

        // 从配置加载描述，fallback 到工具默认描述
        String description = toolConfigLoader.getDescription(toolName);
        if (description == null || description.isBlank()) {
            description = mcpTool.getDescription();
        }
        if (description == null || description.isBlank()) {
            description = "No description for " + toolName;
        }

        // 从配置加载 Schema，fallback 到工具默认 Schema
        Map<String, Object> inputSchema = toolConfigLoader.getSchema(toolName);
        if (inputSchema == null || inputSchema.isEmpty()) {
            inputSchema = mcpTool.getInputSchema();
        }
        if (inputSchema == null) {
            inputSchema = Map.of("type", "object", "properties", Map.of());
        }

        // 将简化的工具名转换为 Spring AI 友好的名称（去掉点号）
        String springToolName = toolName.replace(".", "_");

        final String finalDescription = description;
        final Map<String, Object> finalSchema = inputSchema;

        return new McpToolCallback(springToolName, toolName, finalDescription, finalSchema, mcpTool, traceId, authorization, objectMapper, collector);
    }

    /**
     * 将多个 McpTool 转换为 ToolCallback 数组
     *
     * @param mcpTools      MCP 工具列表
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return Spring AI ToolCallback 数组
     */
    public ToolCallback[] createToolCallbacks(List<McpTool> mcpTools, String traceId, String authorization) {
        return createToolCallbacks(mcpTools, traceId, authorization, null);
    }

    /**
     * 将多个 McpTool 转换为 ToolCallback 数组（带收集器）
     *
     * @param mcpTools      MCP 工具列表
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @param collector     工具调用收集器（可选）
     * @return Spring AI ToolCallback 数组
     */
    public ToolCallback[] createToolCallbacks(List<McpTool> mcpTools, String traceId, String authorization, ToolCallCollector collector) {
        return mcpTools.stream()
                .map(tool -> createToolCallback(tool, traceId, authorization, collector))
                .toArray(ToolCallback[]::new);
    }

    /**
     * MCP 工具回调实现
     */
    private static class McpToolCallback implements ToolCallback {

        private final String springToolName;
        private final String originalToolName;
        private final String description;
        private final Map<String, Object> inputSchema;
        private final McpTool mcpTool;
        private final String traceId;
        private final String authorization;
        private final ObjectMapper objectMapper;
        private final ToolCallCollector collector;
        private final ToolDefinition toolDefinition;

        public McpToolCallback(
                String springToolName,
                String originalToolName,
                String description,
                Map<String, Object> inputSchema,
                McpTool mcpTool,
                String traceId,
                String authorization,
                ObjectMapper objectMapper,
                ToolCallCollector collector) {
            this.springToolName = springToolName;
            this.originalToolName = originalToolName;
            this.description = description;
            this.inputSchema = inputSchema;
            this.mcpTool = mcpTool;
            this.traceId = traceId;
            this.authorization = authorization;
            this.objectMapper = objectMapper;
            this.collector = collector;

            // 构建 ToolDefinition
            String schemaJson;
            try {
                schemaJson = objectMapper.writeValueAsString(inputSchema);
            } catch (JsonProcessingException e) {
                schemaJson = "{\"type\":\"object\",\"properties\":{}}";
            }

            this.toolDefinition = DefaultToolDefinition.builder()
                    .name(springToolName)
                    .description(truncateDescription(description))
                    .inputSchema(schemaJson)
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            log.info("[MCP Tool Call] {} with input: {}", springToolName, toolInput);

            long startTime = System.currentTimeMillis();
            Map<String, Object> arguments = null;
            Object result = null;
            String error = null;
            String errorType = null;

            try {
                // 解析输入参数
                @SuppressWarnings("unchecked")
                Map<String, Object> parsedArgs = toolInput != null && !toolInput.isBlank()
                        ? objectMapper.readValue(toolInput, Map.class)
                        : Map.of();
                arguments = parsedArgs;

                // 调用 MCP 工具
                result = mcpTool.execute(arguments, traceId, authorization);

                // 转换结果为 JSON
                String jsonResult = objectMapper.writeValueAsString(result);
                log.info("[MCP Tool Result] {}: {} chars", springToolName, jsonResult.length());

                return jsonResult;

            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // JSON 解析错误 - 通常是 AI 生成的参数格式不正确
                log.warn("[MCP Tool JSON Error] {}: {}", springToolName, e.getMessage());
                errorType = "JSON_PARSE_ERROR";
                error = "JSON参数格式错误: " + extractJsonErrorHint(e.getMessage());
                return buildJsonParseErrorResponse(toolInput, e);

            } catch (Exception e) {
                log.error("[MCP Tool Error] {}: {}", springToolName, e.getMessage(), e);
                errorType = "EXECUTION_ERROR";
                error = e.getMessage();
                return "{\"error\": true, \"error_type\": \"execution_error\", \"message\": \"" + escapeJson(e.getMessage()) + "\"}";

            } finally {
                // 记录工具调用到收集器
                long durationMs = System.currentTimeMillis() - startTime;
                if (collector != null) {
                    collector.recordToolCall(
                            originalToolName,
                            springToolName,
                            arguments,
                            result,
                            error != null ? "[" + errorType + "] " + error : null,
                            durationMs
                    );
                }
            }
        }

        /**
         * 构建 JSON 解析错误响应，帮助 AI 理解并修正
         */
        private String buildJsonParseErrorResponse(String toolInput, Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"error\": true, ");
            sb.append("\"error_type\": \"json_parse_error\", ");
            sb.append("\"message\": \"JSON参数格式不正确，请检查JSON语法\", ");
            sb.append("\"hint\": \"").append(escapeJson(extractJsonErrorHint(e.getMessage()))).append("\", ");

            // 显示收到的输入（截断以避免过长）
            String truncatedInput = toolInput;
            if (truncatedInput != null && truncatedInput.length() > 200) {
                truncatedInput = truncatedInput.substring(0, 200) + "...(truncated)";
            }
            sb.append("\"received_input\": \"").append(escapeJson(truncatedInput != null ? truncatedInput : "null")).append("\"");

            sb.append("}");
            return sb.toString();
        }

        /**
         * 从 Jackson 错误消息中提取简洁的提示
         */
        private String extractJsonErrorHint(String errorMessage) {
            if (errorMessage == null) {
                return "未知JSON错误";
            }
            // 常见的 JSON 错误模式
            if (errorMessage.contains("Unexpected end-of-input")) {
                return "JSON未正确闭合，可能缺少 } 或 ]";
            }
            if (errorMessage.contains("Unexpected character")) {
                return "JSON中存在意外字符";
            }
            if (errorMessage.contains("Unrecognized token")) {
                return "JSON中存在无法识别的内容";
            }
            if (errorMessage.contains("Missing value")) {
                return "JSON中缺少值";
            }
            // 提取位置信息
            if (errorMessage.contains("line:") && errorMessage.contains("column:")) {
                int lineIdx = errorMessage.indexOf("line:");
                int endIdx = errorMessage.indexOf("]", lineIdx);
                if (endIdx > lineIdx) {
                    return "错误位置: " + errorMessage.substring(lineIdx, endIdx);
                }
            }
            // 截断过长的错误消息
            if (errorMessage.length() > 100) {
                return errorMessage.substring(0, 100) + "...";
            }
            return errorMessage;
        }

        /**
         * 截断描述到合理长度（避免 token 过多）
         * 不做截断避免描述丢失
         */
        private String truncateDescription(String desc) {
            return desc.trim();
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpToolCallback.class);
    }
}
