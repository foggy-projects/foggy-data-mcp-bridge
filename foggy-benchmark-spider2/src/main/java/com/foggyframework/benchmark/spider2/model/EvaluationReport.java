package com.foggyframework.benchmark.spider2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 评估报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationReport {

    /**
     * 报告生成时间
     */
    private Instant generatedAt;

    /**
     * 总测试用例数
     */
    private int totalTestCases;

    /**
     * 通过数量
     */
    private int passedCount;

    /**
     * 失败数量
     */
    private int failedCount;

    /**
     * 成功率
     */
    private double successRate;

    /**
     * 平均执行时间（毫秒）
     */
    private double avgDurationMs;

    /**
     * P50 执行时间
     */
    private long p50DurationMs;

    /**
     * P95 执行时间
     */
    private long p95DurationMs;

    /**
     * P99 执行时间
     */
    private long p99DurationMs;

    /**
     * 按模型分组的统计
     */
    private Map<String, ModelStats> modelStats;

    /**
     * 按数据库分组的统计
     */
    private Map<String, DatabaseStats> databaseStats;

    /**
     * 失败的测试用例列表
     */
    private List<FailedCase> failedCases;

    /**
     * 模型统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelStats {
        private String provider;
        private String modelName;
        private int totalCount;
        private int passedCount;
        private double successRate;
        private double avgDurationMs;
        private int totalTokens;
    }

    /**
     * 数据库统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseStats {
        private String database;
        private int totalCount;
        private int passedCount;
        private double successRate;
    }

    /**
     * 失败用例
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedCase {
        private String testCaseId;
        private String database;
        private String question;
        private String errorMessage;
        private String modelName;
    }
}
