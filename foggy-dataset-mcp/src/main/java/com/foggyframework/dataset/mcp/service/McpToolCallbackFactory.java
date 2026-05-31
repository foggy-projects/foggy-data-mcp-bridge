package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolExecutionContext;
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
    private static final int QUERY_TOOL_FAILURE_BUDGET = 10;
    private static final int REPEATED_QUERY_MODEL_FAILURE_SIGNATURE_BUDGET = 3;
    static final String TOOL_FAILURE_BUDGET_EXCEEDED_MARKER = "TOOL_FAILURE_BUDGET_EXCEEDED";

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
            Object recordedResult = null;
            String error = null;
            String errorType = null;
            ToolFailureBudgetExceededException deferredBudgetExceeded = null;

            try {
                // 解析输入参数
                @SuppressWarnings("unchecked")
                Map<String, Object> parsedArgs = toolInput != null && !toolInput.isBlank()
                        ? objectMapper.readValue(toolInput, Map.class)
                        : Map.of();
                arguments = parsedArgs;

                if (collector != null && collector.getFailureCount() >= QUERY_TOOL_FAILURE_BUDGET) {
                    errorType = TOOL_FAILURE_BUDGET_EXCEEDED_MARKER;
                    error = "tool failure budget exceeded before executing " + originalToolName;
                    throw new ToolFailureBudgetExceededException(error);
                }

                // 调用 MCP 工具
                ToolExecutionContext context = ToolExecutionContext.of(traceId, authorization);
                result = mcpTool.execute(arguments, context);
                recordedResult = result;

                // dataset.query_model 执行成功后，将结构化结果写入 ThreadLocal 捕获槽，
                // 供 QueryExpertService.processQuery() 在 Spring AI call() 返回后读取。
                Object toolResponse = result;
                if ("dataset.query_model".equals(originalToolName)) {
                    QueryExpertService.captureQueryResult(result);
                    String queryFailure = resolveQueryModelFailureMessage(result);
                    if (queryFailure != null) {
                        errorType = "QUERY_MODEL_FAILED";
                        error = queryFailure;
                        Map<String, Object> failureResponse = buildQueryModelFailureResponse(result, queryFailure);
                        int repeatedCount = repeatedQueryModelFailureSignatureCount(collector, queryFailure) + 1;
                        enrichRepeatedQueryModelFailureGuidance(failureResponse, queryFailure, repeatedCount);
                        toolResponse = failureResponse;
                        recordedResult = failureResponse;
                        if (repeatedCount >= REPEATED_QUERY_MODEL_FAILURE_SIGNATURE_BUDGET) {
                            deferredBudgetExceeded = new ToolFailureBudgetExceededException(
                                    "repeated dataset.query_model failure signature reached "
                                            + repeatedCount + "/" + REPEATED_QUERY_MODEL_FAILURE_SIGNATURE_BUDGET
                                            + ": " + queryModelFailureSignature(queryFailure));
                        }
                    }
                }

                // 转换结果为 JSON
                String jsonResult = objectMapper.writeValueAsString(toolResponse);
                log.info("[MCP Tool Result] {}: {} chars", springToolName, jsonResult.length());

                if (deferredBudgetExceeded != null) {
                    throw deferredBudgetExceeded;
                }
                return jsonResult;

            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // JSON 解析错误 - 通常是 AI 生成的参数格式不正确
                log.warn("[MCP Tool JSON Error] {}: {}", springToolName, e.getMessage());
                errorType = "JSON_PARSE_ERROR";
                error = "JSON参数格式错误: " + extractJsonErrorHint(e.getMessage());
                return buildJsonParseErrorResponse(toolInput, e);

            } catch (ToolFailureBudgetExceededException e) {
                log.warn("[MCP Tool Budget Exceeded] {}: {}", springToolName, e.getMessage());
                throw e;

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
                            recordedResult,
                            error != null ? "[" + errorType + "] " + error : null,
                            durationMs
                    );
                }
            }
        }

        private static class ToolFailureBudgetExceededException extends RuntimeException {
            ToolFailureBudgetExceededException(String message) {
                super(TOOL_FAILURE_BUDGET_EXCEEDED_MARKER + ": " + message);
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

        private static String resolveQueryModelFailureMessage(Object result) {
            if (result instanceof RX<?> rx) {
                if (rx._isSuccess()) {
                    return null;
                }
                return firstNonBlank(rx.getMsg(), rx.getUserTip(), rx.getExCode(), "dataset.query_model returned failed RX");
            }
            if (result instanceof Map<?, ?> map) {
                Object status = map.get("status");
                if ("failed".equals(status)) {
                    return firstNonBlank(map.get("msg"), map.get("message"), map.get("error"),
                            "dataset.query_model returned failed status");
                }
                Object code = map.get("code");
                if (code instanceof Number number && number.intValue() != RX.SUCCESS) {
                    return firstNonBlank(map.get("msg"), map.get("message"), map.get("error"),
                            "dataset.query_model returned code " + number.intValue());
                }
                Object error = map.get("error");
                if (Boolean.TRUE.equals(error)) {
                    return firstNonBlank(map.get("msg"), map.get("message"), error,
                            "dataset.query_model returned error");
                }
            }
            return null;
        }

        private static Map<String, Object> buildQueryModelFailureResponse(Object result, String message) {
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("error", true);
            response.put("error_type", "query_model_failed");
            response.put("message", firstNonBlank(message, "dataset.query_model returned a failed result"));
            response.put("retry_guidance", retryGuidanceForQueryModelFailure(message));
            response.put("original_result_summary", queryFailureResultSummary(result));
            return response;
        }

        private static Map<String, Object> retryGuidanceForQueryModelFailure(String message) {
            String text = message != null ? message : "";
            String normalized = text.toLowerCase(java.util.Locale.ROOT);

            if (text.contains("HAVING_REQUIRES_AGGREGATE_FIELD")) {
                return Map.of(
                        "action", "move_row_level_filter_to_slice",
                        "instruction", "having only accepts aggregate measures. Move ordinary field filters to slice and keep aggregate filters in having."
                );
            }
            if (text.contains("请指定查询字段") || normalized.contains("must specify query fields")) {
                return Map.of(
                        "action", "add_columns_before_retry",
                        "instruction", "Retry only after adding payload.columns. Use describe_model_internal when the available fields are unclear."
                );
            }
            if (normalized.contains("field '") && normalized.contains("not found in model")) {
                return Map.of(
                        "action", "use_existing_model_fields",
                        "instruction", "Do not retry with invented fields. Call describe_model_internal and either use existing fields or return a terminal reject/clarify."
                );
            }
            if (text.contains("CALCULATED_FIELD_NAME_COLLISION")) {
                return Map.of(
                        "action", "rename_calculated_field",
                        "instruction", "The calculated field name collides with an existing model column. Rename the calculated field before retrying."
                );
            }
            if (text.contains("POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED")
                    || normalized.contains("free-form post-aggregate expressions are not supported")) {
                return Map.of(
                        "action", "avoid_free_form_aggregate_alias_expression",
                        "instruction", "Do not repeatedly reference selected aggregate aliases inside free-form calculatedFields. Use governed postAggregateCalculations for share/rank/cumulative patterns, or return a terminal reject if the requested formula is unsupported."
                );
            }
            if (text.contains("CALCULATED_FIELD_EXPRESSION_INVALID")
                    && (text.contains("未能在查询模型") || normalized.contains("selected aggregate alias"))) {
                return Map.of(
                        "action", "avoid_free_form_aggregate_alias_expression",
                        "instruction", "Do not repeatedly reference selected aggregate aliases inside free-form calculatedFields. Use governed postAggregateCalculations for share/rank/cumulative patterns, or return a terminal reject if the requested formula is unsupported."
                );
            }
            if (text.contains("WINDOW_CALCULATED_FIELD_SLICE_NOT_SUPPORTED")) {
                return Map.of(
                        "action", "move_result_stage_filter",
                        "instruction", "Do not put window or post-aggregate aliases in slice. Use the supported result-stage filter shape or return a terminal clarify/reject."
                );
            }
            return Map.of(
                    "action", "repair_or_stop",
                    "instruction", "Retry only if a concrete schema or payload repair is available. Otherwise return a terminal clarify/reject instead of repeating the same invalid query_model call."
            );
        }

        private static Map<String, Object> queryFailureResultSummary(Object result) {
            Map<String, Object> summary = new java.util.LinkedHashMap<>();
            if (result instanceof RX<?> rx) {
                summary.put("type", "RX");
                summary.put("code", rx.getCode());
                summary.put("success", rx._isSuccess());
                String message = firstNonBlank(rx.getMsg(), rx.getUserTip(), rx.getExCode());
                if (message != null) {
                    summary.put("message", message);
                }
                return summary;
            }
            if (result instanceof Map<?, ?> map) {
                summary.put("type", "map");
                summary.put("keys", map.keySet().stream().map(String::valueOf).toList());
                Object code = map.get("code");
                if (code != null) {
                    summary.put("code", code);
                }
                Object message = firstNonBlank(map.get("msg"), map.get("message"), map.get("error"));
                if (message != null) {
                    summary.put("message", message);
                }
                return summary;
            }
            summary.put("type", result == null ? "null" : result.getClass().getSimpleName());
            return summary;
        }

        @SuppressWarnings("unchecked")
        private static void enrichRepeatedQueryModelFailureGuidance(
                Map<String, Object> failureResponse,
                String message,
                int repeatedCount
        ) {
            failureResponse.put("failure_signature", queryModelFailureSignature(message));
            failureResponse.put("failure_signature_repeat_count", repeatedCount);
            failureResponse.put("failure_signature_budget", REPEATED_QUERY_MODEL_FAILURE_SIGNATURE_BUDGET);

            Object guidance = failureResponse.get("retry_guidance");
            if (guidance instanceof Map<?, ?> guidanceMap) {
                Map<String, Object> enrichedGuidance = new java.util.LinkedHashMap<>((Map<String, Object>) guidanceMap);
                enrichedGuidance.put("failure_signature_repeat_count", repeatedCount);
                enrichedGuidance.put("failure_signature_budget", REPEATED_QUERY_MODEL_FAILURE_SIGNATURE_BUDGET);
                if (repeatedCount >= REPEATED_QUERY_MODEL_FAILURE_SIGNATURE_BUDGET - 1) {
                    enrichedGuidance.put("repeat_warning",
                            "This exact query_model failure signature is repeating. Stop retrying the same shape; use a different supported payload or return a terminal reject/clarify.");
                }
                failureResponse.put("retry_guidance", enrichedGuidance);
            }
        }

        private static int repeatedQueryModelFailureSignatureCount(ToolCallCollector collector, String message) {
            if (collector == null) {
                return 0;
            }
            String signature = queryModelFailureSignature(message);
            int count = 0;
            for (ToolCallCollector.ToolCallRecord call : collector.getCallsByTool("dataset.query_model")) {
                if (call.isSuccess()) {
                    continue;
                }
                String candidate = firstNonBlank(extractQueryModelFailureMessage(call.getResult()), call.getError());
                if (signature.equals(queryModelFailureSignature(candidate))) {
                    count++;
                }
            }
            return count;
        }

        private static String extractQueryModelFailureMessage(Object result) {
            if (result instanceof Map<?, ?> map) {
                Object message = firstNonBlank(map.get("message"), map.get("msg"), map.get("error"));
                if (message != null) {
                    return String.valueOf(message);
                }
                Object originalSummary = map.get("original_result_summary");
                if (originalSummary instanceof Map<?, ?> summary) {
                    Object originalMessage = firstNonBlank(summary.get("message"), summary.get("msg"), summary.get("error"));
                    if (originalMessage != null) {
                        return String.valueOf(originalMessage);
                    }
                }
            }
            if (result instanceof RX<?> rx) {
                return firstNonBlank(rx.getMsg(), rx.getUserTip(), rx.getExCode());
            }
            return null;
        }

        private static String queryModelFailureSignature(String message) {
            String normalized = message != null ? message.trim() : "";
            normalized = normalized.replaceAll("\\s+", " ");
            if (normalized.length() > 240) {
                normalized = normalized.substring(0, 240);
            }
            return retryGuidanceForQueryModelFailure(normalized).get("action") + ":" + normalized;
        }

        private static String firstNonBlank(Object... values) {
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                String text = String.valueOf(value);
                if (!text.isBlank()) {
                    return text;
                }
            }
            return null;
        }

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpToolCallback.class);
    }
}
