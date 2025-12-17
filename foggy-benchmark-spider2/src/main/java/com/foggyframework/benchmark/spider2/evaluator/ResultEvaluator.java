package com.foggyframework.benchmark.spider2.evaluator;

import com.foggyframework.benchmark.spider2.model.BenchmarkResult;
import com.foggyframework.benchmark.spider2.model.EvaluationReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 结果评估器
 */
@Slf4j
@Component
public class ResultEvaluator {

    /**
     * 评估测试结果
     */
    public EvaluationReport evaluate(List<BenchmarkResult> results) {
        if (results == null || results.isEmpty()) {
            return EvaluationReport.builder()
                    .generatedAt(Instant.now())
                    .totalTestCases(0)
                    .build();
        }

        int total = results.size();
        int passed = (int) results.stream().filter(BenchmarkResult::isSuccess).count();
        int failed = total - passed;
        double successRate = (double) passed / total * 100;

        // 计算执行时间统计
        List<Long> durations = results.stream()
                .map(BenchmarkResult::getDurationMs)
                .sorted()
                .collect(Collectors.toList());

        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = getPercentile(durations, 50);
        long p95 = getPercentile(durations, 95);
        long p99 = getPercentile(durations, 99);

        // 按模型分组统计
        Map<String, EvaluationReport.ModelStats> modelStats = new HashMap<>();
        Map<String, List<BenchmarkResult>> byModel = results.stream()
                .collect(Collectors.groupingBy(r -> r.getProvider() + "/" + r.getModelName()));

        for (Map.Entry<String, List<BenchmarkResult>> entry : byModel.entrySet()) {
            String key = entry.getKey();
            List<BenchmarkResult> modelResults = entry.getValue();

            int modelPassed = (int) modelResults.stream().filter(BenchmarkResult::isSuccess).count();
            double modelSuccessRate = (double) modelPassed / modelResults.size() * 100;
            double modelAvgDuration = modelResults.stream()
                    .mapToLong(BenchmarkResult::getDurationMs)
                    .average()
                    .orElse(0);
            int totalTokens = modelResults.stream()
                    .filter(r -> r.getTokenUsage() != null)
                    .mapToInt(r -> r.getTokenUsage().getTotalTokens())
                    .sum();

            String[] parts = key.split("/");
            modelStats.put(key, EvaluationReport.ModelStats.builder()
                    .provider(parts[0])
                    .modelName(parts.length > 1 ? parts[1] : "unknown")
                    .totalCount(modelResults.size())
                    .passedCount(modelPassed)
                    .successRate(modelSuccessRate)
                    .avgDurationMs(modelAvgDuration)
                    .totalTokens(totalTokens)
                    .build());
        }

        // 按数据库分组统计
        Map<String, EvaluationReport.DatabaseStats> databaseStats = new HashMap<>();
        Map<String, List<BenchmarkResult>> byDatabase = results.stream()
                .collect(Collectors.groupingBy(BenchmarkResult::getDatabase));

        for (Map.Entry<String, List<BenchmarkResult>> entry : byDatabase.entrySet()) {
            String db = entry.getKey();
            List<BenchmarkResult> dbResults = entry.getValue();

            int dbPassed = (int) dbResults.stream().filter(BenchmarkResult::isSuccess).count();
            double dbSuccessRate = (double) dbPassed / dbResults.size() * 100;

            databaseStats.put(db, EvaluationReport.DatabaseStats.builder()
                    .database(db)
                    .totalCount(dbResults.size())
                    .passedCount(dbPassed)
                    .successRate(dbSuccessRate)
                    .build());
        }

        // 收集失败用例
        List<EvaluationReport.FailedCase> failedCases = results.stream()
                .filter(r -> !r.isSuccess())
                .map(r -> EvaluationReport.FailedCase.builder()
                        .testCaseId(r.getTestCaseId())
                        .database(r.getDatabase())
                        .question(r.getQuestion())
                        .errorMessage(r.getErrorMessage())
                        .modelName(r.getProvider() + "/" + r.getModelName())
                        .build())
                .collect(Collectors.toList());

        return EvaluationReport.builder()
                .generatedAt(Instant.now())
                .totalTestCases(total)
                .passedCount(passed)
                .failedCount(failed)
                .successRate(successRate)
                .avgDurationMs(avgDuration)
                .p50DurationMs(p50)
                .p95DurationMs(p95)
                .p99DurationMs(p99)
                .modelStats(modelStats)
                .databaseStats(databaseStats)
                .failedCases(failedCases)
                .build();
    }

    /**
     * 计算百分位数
     */
    private long getPercentile(List<Long> sortedList, int percentile) {
        if (sortedList.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedList.size()) - 1;
        return sortedList.get(Math.max(0, Math.min(index, sortedList.size() - 1)));
    }
}
