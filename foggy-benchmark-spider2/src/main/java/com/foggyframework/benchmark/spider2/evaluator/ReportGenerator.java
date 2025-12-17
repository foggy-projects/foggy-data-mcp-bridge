package com.foggyframework.benchmark.spider2.evaluator;

import com.foggyframework.benchmark.spider2.model.EvaluationReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 报告生成器
 */
@Slf4j
@Component
public class ReportGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 生成 Markdown 报告
     */
    public String generateMarkdownReport(EvaluationReport report) {
        StringBuilder sb = new StringBuilder();

        // 标题
        sb.append("# Spider2 基准测试报告\n\n");
        sb.append("生成时间: ").append(DATE_FORMATTER.format(report.getGeneratedAt())).append("\n\n");

        // 概览
        sb.append("## 概览\n\n");
        sb.append("| 指标 | 值 |\n");
        sb.append("|------|----|\n");
        sb.append("| 测试用例数 | ").append(report.getTotalTestCases()).append(" |\n");
        sb.append("| 通过数量 | ").append(report.getPassedCount()).append(" |\n");
        sb.append("| 失败数量 | ").append(report.getFailedCount()).append(" |\n");
        sb.append(String.format("| 成功率 | %.2f%% |\n", report.getSuccessRate()));
        sb.append(String.format("| 平均耗时 | %.0fms |\n", report.getAvgDurationMs()));
        sb.append("| P50 耗时 | ").append(report.getP50DurationMs()).append("ms |\n");
        sb.append("| P95 耗时 | ").append(report.getP95DurationMs()).append("ms |\n");
        sb.append("| P99 耗时 | ").append(report.getP99DurationMs()).append("ms |\n");
        sb.append("\n");

        // 模型统计
        if (report.getModelStats() != null && !report.getModelStats().isEmpty()) {
            sb.append("## 模型统计\n\n");
            sb.append("| 模型 | 测试数 | 通过数 | 成功率 | 平均耗时 | Token消耗 |\n");
            sb.append("|------|--------|--------|--------|----------|----------|\n");

            for (var entry : report.getModelStats().entrySet()) {
                var stats = entry.getValue();
                sb.append(String.format("| %s/%s | %d | %d | %.2f%% | %.0fms | %d |\n",
                        stats.getProvider(),
                        stats.getModelName(),
                        stats.getTotalCount(),
                        stats.getPassedCount(),
                        stats.getSuccessRate(),
                        stats.getAvgDurationMs(),
                        stats.getTotalTokens()));
            }
            sb.append("\n");
        }

        // 数据库统计
        if (report.getDatabaseStats() != null && !report.getDatabaseStats().isEmpty()) {
            sb.append("## 数据库统计\n\n");
            sb.append("| 数据库 | 测试数 | 通过数 | 成功率 |\n");
            sb.append("|--------|--------|--------|--------|\n");

            for (var entry : report.getDatabaseStats().entrySet()) {
                var stats = entry.getValue();
                sb.append(String.format("| %s | %d | %d | %.2f%% |\n",
                        stats.getDatabase(),
                        stats.getTotalCount(),
                        stats.getPassedCount(),
                        stats.getSuccessRate()));
            }
            sb.append("\n");
        }

        // 失败用例
        if (report.getFailedCases() != null && !report.getFailedCases().isEmpty()) {
            sb.append("## 失败用例\n\n");

            int count = 0;
            for (var failedCase : report.getFailedCases()) {
                if (count >= 20) {
                    sb.append("... 更多失败用例省略（共 ")
                            .append(report.getFailedCases().size())
                            .append(" 个）\n");
                    break;
                }

                sb.append("### ").append(failedCase.getTestCaseId()).append("\n\n");
                sb.append("- **数据库**: ").append(failedCase.getDatabase()).append("\n");
                sb.append("- **模型**: ").append(failedCase.getModelName()).append("\n");
                sb.append("- **问题**: ").append(failedCase.getQuestion()).append("\n");
                sb.append("- **错误**: ").append(failedCase.getErrorMessage()).append("\n\n");

                count++;
            }
        }

        return sb.toString();
    }

    /**
     * 保存报告到文件
     */
    public void saveReport(EvaluationReport report, Path outputPath) throws IOException {
        String markdown = generateMarkdownReport(report);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, markdown);
        log.info("Report saved to: {}", outputPath);
    }

    /**
     * 打印报告到控制台
     */
    public void printReport(EvaluationReport report) {
        log.info("\n{}", generateMarkdownReport(report));
    }
}
