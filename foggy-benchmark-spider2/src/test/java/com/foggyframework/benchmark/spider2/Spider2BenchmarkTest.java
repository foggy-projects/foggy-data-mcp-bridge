package com.foggyframework.benchmark.spider2;

import com.foggyframework.benchmark.spider2.config.Spider2DataSourceConfig;
import com.foggyframework.benchmark.spider2.config.Spider2Properties;
import com.foggyframework.benchmark.spider2.evaluator.ReportGenerator;
import com.foggyframework.benchmark.spider2.evaluator.ResultEvaluator;
import com.foggyframework.benchmark.spider2.executor.BenchmarkExecutor;
import com.foggyframework.benchmark.spider2.loader.Spider2DatabaseInspector;
import com.foggyframework.benchmark.spider2.loader.Spider2TestCaseLoader;
import com.foggyframework.benchmark.spider2.model.BenchmarkResult;
import com.foggyframework.benchmark.spider2.model.EvaluationReport;
import com.foggyframework.benchmark.spider2.model.Spider2TestCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spider2 基准测试
 */
@Slf4j
@SpringBootTest(classes = Spider2BenchmarkApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Spider2 基准测试")
class Spider2BenchmarkTest {

    @Autowired
    private Spider2Properties properties;

    @Autowired
    private Spider2TestCaseLoader testCaseLoader;

    @Autowired
    private Spider2DataSourceConfig dataSourceConfig;

    @Autowired
    private Spider2DatabaseInspector databaseInspector;

    @Autowired
    private BenchmarkExecutor benchmarkExecutor;

    @Autowired
    private ResultEvaluator resultEvaluator;

    @Autowired
    private ReportGenerator reportGenerator;

    // ==================== 环境检查 ====================

    @Test
    @Order(1)
    @DisplayName("检查 Spider2 数据配置")
    void checkSpider2Configuration() {
        log.info("Spider2 JSONL Path: {}", properties.getJsonlPath());
        log.info("Spider2 Database Path: {}", properties.getDatabaseBasePath());

        boolean configured = testCaseLoader.isSpider2Configured();
        log.info("Spider2 configured: {}", configured);

        if (!configured) {
            log.warn("Spider2 data not found. Please download from:");
            log.warn("  https://github.com/xlang-ai/Spider2");
        }

        // 不强制要求配置，让测试可以跳过
        Assumptions.assumeTrue(configured, "Spider2 data not configured");
    }

    @Test
    @Order(2)
    @DisplayName("加载测试用例统计")
    void loadTestCasesStatistics() throws IOException {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        Spider2TestCaseLoader.Spider2Statistics stats = testCaseLoader.getStatistics();
        stats.print();

        assertTrue(stats.getSqliteCount() > 0, "Should have SQLite test cases");
        log.info("Available SQLite databases: {}", stats.getUniqueDatabases());
    }

    // ==================== 元数据加载 ====================

    @Test
    @Order(5)
    @DisplayName("从 Spider2 JSON 加载表元数据")
    void loadMetadataFromSpider2Json() {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        // 测试 E_commerce 数据库的元数据加载
        String testDb = "E_commerce";
        Spider2DatabaseInspector.DatabaseSchema schema = databaseInspector.loadFromMetadata(testDb);

        if (schema != null) {
            log.info("Successfully loaded metadata for database: {}", testDb);
            log.info("Tables count: {}", schema.getTables().size());

            for (Spider2DatabaseInspector.TableSchema table : schema.getTables()) {
                log.info("\nTable: {}", table.getTableName());
                log.info("  Columns: {}", table.getColumns().size());
                log.info("  Sample rows: {}", table.getSampleRows() != null ? table.getSampleRows().size() : 0);

                // 打印列信息（包括描述）
                for (Spider2DatabaseInspector.ColumnSchema col : table.getColumns()) {
                    String desc = col.getDescription() != null && !col.getDescription().isEmpty()
                            ? " - " + col.getDescription()
                            : "";
                    log.info("    - {} ({}){}", col.getName(), col.getType(), desc);
                }
            }

            assertFalse(schema.getTables().isEmpty(), "Should have tables");
        } else {
            log.warn("Metadata not found for {}. Check metadataBasePath configuration.", testDb);
            log.info("Configured metadataBasePath: {}", properties.getMetadataBasePath());
        }
    }

    @Test
    @Order(6)
    @DisplayName("生成 JM 模型（基于元数据）")
    void generateJmModelFromMetadata() {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        String testDb = "E_commerce";
        Spider2DatabaseInspector.DatabaseSchema schema = databaseInspector.loadFromMetadata(testDb);

        Assumptions.assumeTrue(schema != null, "Metadata not available for " + testDb);

        String jmModel = databaseInspector.generateJmModel(schema);
        log.info("\n========== Generated JM Model ==========\n{}", jmModel);

        assertNotNull(jmModel);
        assertTrue(jmModel.contains("export const"), "Should contain JM model definition");
        assertTrue(jmModel.contains("properties:"), "Should contain properties array");
    }

    // ==================== 数据库探测 ====================

    @Test
    @Order(10)
    @DisplayName("探测 SQLite 数据库结构")
    void inspectDatabaseSchema() throws IOException {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        List<String> databases = testCaseLoader.getUniqueDatabases();
        log.info("Found {} unique databases", databases.size());

        // 探测前 3 个数据库
        int count = 0;
        for (String db : databases) {
            if (count >= 3) break;

            if (dataSourceConfig.isDatabaseAvailable(db)) {
                log.info("\n========== Database: {} ==========", db);

                Spider2DatabaseInspector.DatabaseSchema schema = databaseInspector.inspectSchema(db);
                for (Spider2DatabaseInspector.TableSchema table : schema.getTables()) {
                    log.info("  Table: {} ({} columns, {} rows)",
                            table.getTableName(),
                            table.getColumns().size(),
                            table.getRowCount());
                }
                count++;
            }
        }
    }

    @Test
    @Order(11)
    @DisplayName("生成 JM 模型（从数据库直接探测）")
    void generateJmModelFromDatabase() throws IOException {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        List<String> databases = testCaseLoader.getUniqueDatabases();

        // 找第一个可用的数据库
        for (String db : databases) {
            if (dataSourceConfig.isDatabaseAvailable(db)) {
                Spider2DatabaseInspector.DatabaseSchema schema = databaseInspector.inspectSchemaFromDatabase(db);
                String jmModel = databaseInspector.generateJmModel(schema);

                log.info("\n========== JM Model for {} (from DB) ==========\n{}", db, jmModel);
                break;
            }
        }
    }

    // ==================== AI 测试 ====================

    @Test
    @Order(20)
    @DisplayName("检查 AI 模型配置")
    void checkAiModelConfiguration() {
        boolean available = benchmarkExecutor.isAiModelAvailable();
        log.info("AI Model available: {}", available);

        if (!available) {
            log.warn("No AI model configured. Set DASHSCOPE_API_KEY or configure spring.ai.openai");
        }
    }

    @Test
    @Order(21)
    @DisplayName("执行单个测试用例")
    void executeSingleTestCase() throws IOException {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");
        Assumptions.assumeTrue(benchmarkExecutor.isAiModelAvailable(), "AI model not available");

        List<Spider2TestCase> testCases = testCaseLoader.loadLocalSqliteTestCases();
        Assumptions.assumeFalse(testCases.isEmpty(), "No test cases available");

        Spider2TestCase testCase = testCases.get(0);
        log.info("Testing: {} - {}", testCase.getInstanceId(), testCase.getQuestion());

        BenchmarkResult result = benchmarkExecutor.execute(testCase, null);

        log.info("Result: {}", result.getSummary());
        if (result.getAiResponse() != null) {
            log.info("AI Response: {}", result.getAiResponse().substring(0,
                    Math.min(500, result.getAiResponse().length())));
        }
    }

    // ==================== 批量测试 ====================

    @Test
    @Order(30)
    @DisplayName("批量执行测试并生成报告")
    void executeBatchAndGenerateReport() throws IOException {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");
        Assumptions.assumeTrue(benchmarkExecutor.isAiModelAvailable(), "AI model not available");

        // 加载测试用例（受 max-test-cases 限制）
        List<Spider2TestCase> testCases = testCaseLoader.loadEnabledTestCases();
        Assumptions.assumeFalse(testCases.isEmpty(), "No test cases available");

        log.info("Executing {} test cases...", testCases.size());

        // 执行测试
        List<BenchmarkResult> results = benchmarkExecutor.executeBatch(testCases, null);

        // 评估结果
        EvaluationReport report = resultEvaluator.evaluate(results);

        // 打印报告
        reportGenerator.printReport(report);

        // 保存报告
        Path reportPath = Path.of("target/reports/spider2-benchmark-report.md");
        reportGenerator.saveReport(report, reportPath);

        // 断言
        assertTrue(report.getTotalTestCases() > 0, "Should have test results");
        log.info("Success rate: {}%", String.format("%.2f", report.getSuccessRate()));
    }
}
