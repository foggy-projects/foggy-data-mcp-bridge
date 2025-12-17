package com.foggyframework.benchmark.spider2.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.benchmark.spider2.config.Spider2Properties;
import com.foggyframework.benchmark.spider2.model.Spider2TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spider2 测试用例加载器
 *
 * 从 Spider2-Lite JSONL 文件加载测试用例
 */
@Slf4j
@Component
public class Spider2TestCaseLoader {

    private final ObjectMapper objectMapper;
    private final Spider2Properties properties;

    public Spider2TestCaseLoader(ObjectMapper objectMapper, Spider2Properties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 加载所有测试用例
     */
    public List<Spider2TestCase> loadAllTestCases() throws IOException {
        Path jsonlPath = Path.of(properties.getJsonlPath());
        List<Spider2TestCase> testCases = new ArrayList<>();

        if (!Files.exists(jsonlPath)) {
            log.warn("Spider2 JSONL file not found: {}", jsonlPath);
            return testCases;
        }

        try (BufferedReader reader = Files.newBufferedReader(jsonlPath)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Spider2TestCase testCase = objectMapper.readValue(line, Spider2TestCase.class);
                    testCases.add(testCase);
                } catch (Exception e) {
                    log.warn("Failed to parse line {}: {}", lineNumber, e.getMessage());
                }
            }
        }

        log.info("Loaded {} test cases from Spider2", testCases.size());
        return testCases;
    }

    /**
     * 仅加载本地 SQLite 测试用例
     */
    public List<Spider2TestCase> loadLocalSqliteTestCases() throws IOException {
        return loadAllTestCases().stream()
                .filter(Spider2TestCase::isLocalSqlite)
                .collect(Collectors.toList());
    }

    /**
     * 加载指定数据库的测试用例
     */
    public List<Spider2TestCase> loadByDatabase(String database) throws IOException {
        return loadLocalSqliteTestCases().stream()
                .filter(tc -> database.equals(tc.getDatabase()))
                .collect(Collectors.toList());
    }

    /**
     * 加载启用的数据库测试用例
     */
    public List<Spider2TestCase> loadEnabledTestCases() throws IOException {
        List<Spider2TestCase> testCases = loadLocalSqliteTestCases();

        // 如果配置了启用数据库列表，则过滤
        List<String> enabledDbs = properties.getEnabledDatabases();
        if (enabledDbs != null && !enabledDbs.isEmpty()) {
            testCases = testCases.stream()
                    .filter(tc -> enabledDbs.contains(tc.getDatabase()))
                    .collect(Collectors.toList());
        }

        // 如果配置了最大数量，则截取
        int maxCases = properties.getMaxTestCases();
        if (maxCases > 0 && testCases.size() > maxCases) {
            testCases = testCases.subList(0, maxCases);
        }

        log.info("Loaded {} enabled test cases", testCases.size());
        return testCases;
    }

    /**
     * 按实例 ID 加载单个测试用例
     */
    public Spider2TestCase loadByInstanceId(String instanceId) throws IOException {
        return loadAllTestCases().stream()
                .filter(tc -> instanceId.equals(tc.getInstanceId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有唯一的数据库名称
     */
    public List<String> getUniqueDatabases() throws IOException {
        return loadLocalSqliteTestCases().stream()
                .map(Spider2TestCase::getDatabase)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 统计各数据库的测试用例数量
     */
    public Spider2Statistics getStatistics() throws IOException {
        List<Spider2TestCase> allCases = loadAllTestCases();

        long sqliteCount = allCases.stream()
                .filter(tc -> tc.getDatabaseType() == Spider2TestCase.DatabaseType.SQLITE)
                .count();
        long bigqueryCount = allCases.stream()
                .filter(tc -> tc.getDatabaseType() == Spider2TestCase.DatabaseType.BIGQUERY)
                .count();
        long snowflakeCount = allCases.stream()
                .filter(tc -> tc.getDatabaseType() == Spider2TestCase.DatabaseType.SNOWFLAKE)
                .count();

        List<String> uniqueDatabases = getUniqueDatabases();

        return Spider2Statistics.builder()
                .totalCount(allCases.size())
                .sqliteCount(sqliteCount)
                .bigqueryCount(bigqueryCount)
                .snowflakeCount(snowflakeCount)
                .uniqueDatabases(uniqueDatabases)
                .build();
    }

    /**
     * Spider2 统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class Spider2Statistics {
        private int totalCount;
        private long sqliteCount;
        private long bigqueryCount;
        private long snowflakeCount;
        private List<String> uniqueDatabases;

        public void print() {
            log.info("Spider2 Statistics:");
            log.info("  Total: {}", totalCount);
            log.info("  SQLite: {}", sqliteCount);
            log.info("  BigQuery: {}", bigqueryCount);
            log.info("  Snowflake: {}", snowflakeCount);
            log.info("  Unique SQLite Databases: {}", uniqueDatabases.size());
        }
    }

    /**
     * 检查 Spider2 数据是否已配置
     */
    public boolean isSpider2Configured() {
        return Files.exists(Path.of(properties.getJsonlPath()));
    }
}
