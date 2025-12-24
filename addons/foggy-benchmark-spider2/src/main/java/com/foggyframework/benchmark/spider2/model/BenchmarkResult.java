package com.foggyframework.benchmark.spider2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 基准测试结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkResult {

    /**
     * 测试用例 ID
     */
    private String testCaseId;

    /**
     * 数据库名称
     */
    private String database;

    /**
     * 原始问题
     */
    private String question;

    /**
     * AI 提供商
     */
    private String provider;

    /**
     * AI 模型名称
     */
    private String modelName;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * AI 生成的响应
     */
    private String aiResponse;

    /**
     * 工具调用记录
     */
    private List<ToolCallRecord> toolCalls;

    /**
     * 查询结果（如果有）
     */
    private Object queryResult;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * Token 使用量
     */
    private TokenUsage tokenUsage;

    /**
     * 开始时间
     */
    private Instant startTime;

    /**
     * 结束时间
     */
    private Instant endTime;

    /**
     * 工具调用记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRecord {
        private String toolName;
        private Map<String, Object> arguments;
        private Object result;
        private long durationMs;
        private boolean success;
        private String error;
    }

    /**
     * Token 使用量
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    /**
     * 创建成功结果
     */
    public static BenchmarkResult success(Spider2TestCase testCase, String provider, String modelName,
                                          String aiResponse, List<ToolCallRecord> toolCalls,
                                          Object queryResult, long durationMs) {
        return BenchmarkResult.builder()
                .testCaseId(testCase.getInstanceId())
                .database(testCase.getDatabase())
                .question(testCase.getQuestion())
                .provider(provider)
                .modelName(modelName)
                .success(true)
                .aiResponse(aiResponse)
                .toolCalls(toolCalls)
                .queryResult(queryResult)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static BenchmarkResult failure(Spider2TestCase testCase, String provider, String modelName,
                                          String errorMessage, long durationMs) {
        return BenchmarkResult.builder()
                .testCaseId(testCase.getInstanceId())
                .database(testCase.getDatabase())
                .question(testCase.getQuestion())
                .provider(provider)
                .modelName(modelName)
                .success(false)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 获取简要描述
     */
    public String getSummary() {
        String status = success ? "✓ PASS" : "✗ FAIL";
        String model = provider + "/" + modelName;
        return String.format("[%s] %s | %s | %dms | %s",
                status, testCaseId, model, durationMs,
                success ? "OK" : errorMessage);
    }
}
