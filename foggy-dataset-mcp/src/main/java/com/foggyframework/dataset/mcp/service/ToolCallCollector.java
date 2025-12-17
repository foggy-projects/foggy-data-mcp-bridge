package com.foggyframework.dataset.mcp.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具调用收集器
 *
 * <p>用于收集 MCP 工具的调用信息，包括工具名称、参数、结果、耗时等。
 * 每次 AI 对话开始前创建一个新的 Collector，对话结束后获取完整的调用链。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 1. 创建收集器
 * ToolCallCollector collector = new ToolCallCollector();
 *
 * // 2. 创建带收集器的工具回调
 * ToolCallback[] callbacks = toolCallbackFactory.createToolCallbacks(tools, traceId, auth, collector);
 *
 * // 3. 执行 AI 对话（工具调用会被自动记录）
 * String response = chatClient.prompt()...call().content();
 *
 * // 4. 获取工具调用信息
 * List<ToolCallRecord> calls = collector.getToolCalls();
 * }</pre>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
public class ToolCallCollector {

    /**
     * 工具调用记录列表（线程安全）
     */
    private final List<ToolCallRecord> toolCalls = new CopyOnWriteArrayList<>();

    /**
     * 收集器创建时间
     */
    private final Instant createdAt = Instant.now();

    /**
     * 会话标识（可选）
     */
    private String sessionId;

    /**
     * 创建空收集器
     */
    public ToolCallCollector() {
    }

    /**
     * 创建带会话ID的收集器
     *
     * @param sessionId 会话标识
     */
    public ToolCallCollector(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 记录工具调用
     *
     * @param toolName      工具名称（原始名称，如 dataset.query_model_v2）
     * @param springToolName Spring AI 使用的名称（如 dataset_query_model_v2）
     * @param arguments     调用参数
     * @param result        执行结果（成功时）
     * @param error         错误信息（失败时）
     * @param durationMs    执行耗时（毫秒）
     */
    public void recordToolCall(
            String toolName,
            String springToolName,
            Map<String, Object> arguments,
            Object result,
            String error,
            long durationMs) {

        ToolCallRecord record = ToolCallRecord.builder()
                .toolName(toolName)
                .springToolName(springToolName)
                .arguments(arguments != null ? Map.copyOf(arguments) : Map.of())
                .result(result)
                .error(error)
                .success(error == null)
                .durationMs(durationMs)
                .timestamp(Instant.now())
                .sequence(toolCalls.size())
                .build();

        toolCalls.add(record);

        log.debug("[ToolCallCollector] Recorded call #{}: {} ({}ms, success={})",
                record.getSequence(), toolName, durationMs, record.isSuccess());
    }

    /**
     * 获取所有工具调用记录
     *
     * @return 不可变的工具调用列表
     */
    public List<ToolCallRecord> getToolCalls() {
        return Collections.unmodifiableList(new ArrayList<>(toolCalls));
    }

    /**
     * 获取工具调用次数
     */
    public int getCallCount() {
        return toolCalls.size();
    }

    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * 获取第一次工具调用（如果有）
     */
    public ToolCallRecord getFirstCall() {
        return toolCalls.isEmpty() ? null : toolCalls.get(0);
    }

    /**
     * 获取最后一次工具调用（如果有）
     */
    public ToolCallRecord getLastCall() {
        return toolCalls.isEmpty() ? null : toolCalls.get(toolCalls.size() - 1);
    }

    /**
     * 获取指定工具的所有调用
     *
     * @param toolName 工具名称
     * @return 该工具的调用记录列表
     */
    public List<ToolCallRecord> getCallsByTool(String toolName) {
        return toolCalls.stream()
                .filter(r -> toolName.equals(r.getToolName()) || toolName.equals(r.getSpringToolName()))
                .toList();
    }

    /**
     * 获取总执行时间（所有工具调用的耗时之和）
     */
    public long getTotalDurationMs() {
        return toolCalls.stream()
                .mapToLong(ToolCallRecord::getDurationMs)
                .sum();
    }

    /**
     * 是否所有调用都成功
     */
    public boolean isAllSuccess() {
        return toolCalls.stream().allMatch(ToolCallRecord::isSuccess);
    }

    /**
     * 获取失败的调用
     */
    public List<ToolCallRecord> getFailedCalls() {
        return toolCalls.stream()
                .filter(r -> !r.isSuccess())
                .toList();
    }

    /**
     * 清空收集器
     */
    public void clear() {
        toolCalls.clear();
    }

    /**
     * 获取收集器摘要信息
     */
    public String getSummary() {
        if (toolCalls.isEmpty()) {
            return "No tool calls recorded";
        }

        long successCount = toolCalls.stream().filter(ToolCallRecord::isSuccess).count();
        long failedCount = toolCalls.size() - successCount;
        long totalMs = getTotalDurationMs();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tool calls: %d (success=%d, failed=%d, total=%dms)",
                toolCalls.size(), successCount, failedCount, totalMs));

        for (ToolCallRecord call : toolCalls) {
            sb.append("\n  ").append(call.getSequence()).append(". ")
                    .append(call.getToolName())
                    .append(" - ")
                    .append(call.isSuccess() ? "OK" : "FAILED")
                    .append(" (").append(call.getDurationMs()).append("ms)");
        }

        return sb.toString();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 工具调用记录
     */
    @Data
    @Builder
    public static class ToolCallRecord {
        /**
         * 工具原始名称（如 dataset.query_model_v2）
         */
        private String toolName;

        /**
         * Spring AI 使用的名称（如 dataset_query_model_v2）
         */
        private String springToolName;

        /**
         * 调用参数
         */
        private Map<String, Object> arguments;

        /**
         * 执行结果（成功时）
         */
        private Object result;

        /**
         * 错误信息（失败时）
         */
        private String error;

        /**
         * 是否成功
         */
        private boolean success;

        /**
         * 执行耗时（毫秒）
         */
        private long durationMs;

        /**
         * 调用时间戳
         */
        private Instant timestamp;

        /**
         * 调用序号（从0开始）
         */
        private int sequence;

        /**
         * 获取结果的字符串表示（用于日志）
         */
        public String getResultSummary() {
            if (!success) {
                return "ERROR: " + error;
            }
            if (result == null) {
                return "null";
            }
            String str = result.toString();
            if (str.length() > 200) {
                return str.substring(0, 200) + "... (" + str.length() + " chars)";
            }
            return str;
        }
    }
}
