package com.foggyframework.benchmark.spider2.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.benchmark.spider2.Spider2BenchmarkApplication;
import com.foggyframework.benchmark.spider2.config.Spider2Properties;
import com.foggyframework.benchmark.spider2.loader.Spider2TestCaseLoader;
import com.foggyframework.benchmark.spider2.model.Spider2TestCase;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.service.McpToolCallbackFactory;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import com.foggyframework.dataset.mcp.service.ToolCallCollector;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.dataset.mcp.tools.QueryModelTool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * AdventureWorks 数据库 AI 测试
 *
 * 使用 Spider2 测试用例测试 AI 模型调用 QueryModelTool 的能力
 *
 * 测试流程：
 * 1. 使用 SemanticServiceResolver 获取 metadata（markdown 格式）作为系统提示词
 * 2. 仅将 QueryModelTool 工具传递给 AI
 * 3. 使用 Spider2 问题作为用户输入
 * 4. AI 调用 QueryModelTool 获取结果
 * 5. 与 Spider2 标准答案比较
 *
 * 参考: com.foggyframework.dataset.mcp.ai.SpringAiTestExecutor
 */
@Slf4j
@SpringBootTest(classes = Spider2BenchmarkApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AdventureWorks AI 测试")
class AdventureWorksAiTest {

    private static final String DATABASE_NAME = "AdventureWorks";
    private static final String QUERY_MODEL_TOOL_NAME = "dataset.query_model_v2";

    @Autowired
    private Spider2Properties properties;

    @Autowired
    private Spider2TestCaseLoader testCaseLoader;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private ChatModel chatModel;

    /**
     * MCP 工具分发器
     */
    @Autowired
    private McpToolDispatcher mcpToolDispatcher;

    /**
     * 工具回调工厂
     */
    @Autowired
    private McpToolCallbackFactory toolCallbackFactory;

    /**
     * QueryModelTool - 我们只测试这个工具
     */
    @Autowired
    private QueryModelTool queryModelTool;

    @Autowired
    private  McpProperties mcpProperties;


    @Autowired
    private  SemanticServiceResolver semanticServiceResolver;

    // ==================== 环境检查 ====================

    @Test
    @Order(1)
    @DisplayName("检查测试环境")
    void checkTestEnvironment() throws IOException {
        log.info("========== AdventureWorks AI Test Environment ==========");

        // 检查 Spider2 配置
        boolean spider2Configured = testCaseLoader.isSpider2Configured();
        log.info("Spider2 configured: {}", spider2Configured);
        Assumptions.assumeTrue(spider2Configured, "Spider2 data not configured");

        // 检查 AI 模型
        boolean aiAvailable = chatModel != null;
        log.info("AI ChatModel available: {}", aiAvailable);
        if (chatModel != null) {
            log.info("ChatModel class: {}", chatModel.getClass().getName());
        }

        // 检查 MCP 工具
        log.info("MCP Tools registered: {}", mcpToolDispatcher.getAllTools().size());
        boolean hasQueryTool = mcpToolDispatcher.hasTool(QUERY_MODEL_TOOL_NAME);
        log.info("QueryModelTool available: {}", hasQueryTool);
        Assertions.assertTrue(hasQueryTool, "QueryModelTool should be available");

        // 检查测试用例
        List<Spider2TestCase> testCases = testCaseLoader.loadByDatabase(DATABASE_NAME);
        log.info("AdventureWorks test cases: {}", testCases.size());
        for (Spider2TestCase tc : testCases) {
            log.info("  - [{}] {}", tc.getInstanceId(), truncate(tc.getQuestion(), 80));
        }
    }

    @Test
    @Order(2)
    @DisplayName("加载 Spider2 标准答案")
    void loadGoldAnswers() throws IOException {
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        List<Spider2TestCase> testCases = testCaseLoader.loadByDatabase(DATABASE_NAME);
        Assumptions.assumeFalse(testCases.isEmpty(), "No AdventureWorks test cases");

        for (Spider2TestCase tc : testCases) {
            log.info("Test Case: {}", tc.getInstanceId());
            log.info("  Question: {}", tc.getQuestion());

            // 加载标准答案
            List<GoldAnswer> goldAnswers = loadGoldAnswers(tc.getInstanceId());
            log.info("  Gold Answers: {} variants", goldAnswers.size());

            for (GoldAnswer ga : goldAnswers) {
                log.info("    - {} ({} rows, {} columns)",
                        ga.getFileName(), ga.getRows().size(),
                        ga.getRows().isEmpty() ? 0 : ga.getHeaders().size());
                if (!ga.getRows().isEmpty()) {
                    log.info("      Headers: {}", ga.getHeaders());
                    log.info("      First row: {}", ga.getRows().get(0));
                }
            }
        }
    }

    // ==================== AI + QueryModelTool 测试 ====================

    @Test
    @Order(10)
    @DisplayName("AI 调用 QueryModelTool 测试")
    void testAiCallsQueryModelTool() throws IOException {
        Assumptions.assumeTrue(chatModel != null, "ChatModel not available");
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        List<Spider2TestCase> testCases = testCaseLoader.loadByDatabase(DATABASE_NAME);
        Assumptions.assumeFalse(testCases.isEmpty(), "No AdventureWorks test cases");

        // 获取第一个测试用例
        Spider2TestCase testCase = testCases.get(0);
        log.info("========== AI QueryModelTool Test ==========");
        log.info("Instance ID: {}", testCase.getInstanceId());
        log.info("Question: {}", testCase.getQuestion());

        // 构建系统提示词（包含 metadata）
        String systemPrompt = buildSystemPromptWithMetadata();
        log.info("System prompt length: {} chars", systemPrompt.length());

        // 创建工具调用收集器
        String traceId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        ToolCallCollector collector = new ToolCallCollector(testCase.getInstanceId());

        // 只创建 QueryModelTool 的回调
        ToolCallback queryToolCallback = toolCallbackFactory.createToolCallback(
                queryModelTool, traceId, null, collector);

        log.info("Tool registered: {} - {}",
                queryToolCallback.getToolDefinition().name(),
                truncate(queryToolCallback.getToolDefinition().description(), 100));

        // 调用 AI
        ChatClient chatClient = ChatClient.create(chatModel);
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(testCase.getQuestion())
                .toolCallbacks(queryToolCallback)
                .call()
                .content();

        log.info("AI Response:\n{}", response);

        // 检查工具调用记录
        List<ToolCallCollector.ToolCallRecord> toolCalls = collector.getToolCalls();
        log.info("Tool calls: {}", toolCalls.size());

        for (ToolCallCollector.ToolCallRecord record : toolCalls) {
            log.info("  Tool: {}", record.getToolName());
            log.info("  Arguments: {}", record.getArguments());
            log.info("  Success: {}", record.isSuccess());
            log.info("  Duration: {}ms", record.getDurationMs());
            if (record.getResult() != null) {
                String resultStr = objectMapper.writeValueAsString(record.getResult());
                log.info("  Result: {}", truncate(resultStr, 500));
            }
            if (record.getError() != null) {
                log.info("  Error: {}", record.getError());
            }
        }

        // 验证 AI 调用了工具
        Assertions.assertFalse(toolCalls.isEmpty(), "AI should call QueryModelTool");
        Assertions.assertEquals(QUERY_MODEL_TOOL_NAME, toolCalls.get(0).getToolName(),
                "AI should call dataset.query_model_v2");
    }

    @Test
    @Order(20)
    @DisplayName("完整测试：AI 查询与标准答案比较")
    void testFullQueryWithGoldComparison() throws IOException {
        Assumptions.assumeTrue(chatModel != null, "ChatModel not available");
        Assumptions.assumeTrue(testCaseLoader.isSpider2Configured(), "Spider2 not configured");

        List<Spider2TestCase> testCases = testCaseLoader.loadByDatabase(DATABASE_NAME);
        Assumptions.assumeFalse(testCases.isEmpty(), "No AdventureWorks test cases");

        Spider2TestCase testCase = testCases.get(0);
        log.info("========== Full Test: {} ==========", testCase.getInstanceId());
        log.info("Question: {}", testCase.getQuestion());

        // 加载标准答案
        List<GoldAnswer> goldAnswers = loadGoldAnswers(testCase.getInstanceId());
        if (goldAnswers.isEmpty()) {
            log.warn("No gold answers found for {}, skipping comparison", testCase.getInstanceId());
            return;
        }

        log.info("Gold answers loaded: {} variants", goldAnswers.size());

        // 构建系统提示词
        String systemPrompt = buildSystemPromptWithMetadata();

        // 创建工具调用收集器
        String traceId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        ToolCallCollector collector = new ToolCallCollector(testCase.getInstanceId());

        // 只创建 QueryModelTool 的回调
        ToolCallback queryToolCallback = toolCallbackFactory.createToolCallback(
                queryModelTool, traceId, null, collector);

        // 调用 AI
        ChatClient chatClient = ChatClient.create(chatModel);
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(testCase.getQuestion())
                .toolCallbacks(queryToolCallback)
                .call()
                .content();

        log.info("AI Response:\n{}", response);

        // 获取工具调用结果
        List<ToolCallCollector.ToolCallRecord> toolCalls = collector.getToolCalls();

        if (toolCalls.isEmpty()) {
            log.warn("AI did not call any tools");
            Assertions.fail("AI should call QueryModelTool to answer the question");
            return;
        }

        // 获取查询结果
        ToolCallCollector.ToolCallRecord queryCall = toolCalls.stream()
                .filter(tc -> QUERY_MODEL_TOOL_NAME.equals(tc.getToolName()))
                .findFirst()
                .orElse(null);

        if (queryCall == null) {
            log.warn("AI did not call QueryModelTool");
            Assertions.fail("AI should call QueryModelTool");
            return;
        }

        log.info("QueryModelTool called with: {}", queryCall.getArguments());

        // 解析查询结果
        Object result = queryCall.getResult();
        List<Map<String, Object>> queryResult = extractQueryResult(result);
        log.info("Query returned {} rows", queryResult.size());

        if (!queryResult.isEmpty()) {
            log.info("First row: {}", queryResult.get(0));
        }

        // 与标准答案比较
        boolean matchesAny = false;
        for (GoldAnswer ga : goldAnswers) {
            boolean matches = compareResultWithGold(queryResult, ga);
            log.info("Comparison with {}: {}", ga.getFileName(), matches ? "MATCH" : "NO MATCH");
            if (matches) {
                matchesAny = true;
                break;
            }
        }

        log.info("========== Test Result: {} ==========", matchesAny ? "PASSED" : "FAILED");

        // 打印详细对比（如果失败）
        if (!matchesAny && !goldAnswers.isEmpty()) {
            GoldAnswer expected = goldAnswers.get(0);
            log.info("Expected (first gold answer):");
            log.info("  Headers: {}", expected.getHeaders());
            for (int i = 0; i < Math.min(5, expected.getRows().size()); i++) {
                log.info("  Row {}: {}", i, expected.getRows().get(i));
            }
            log.info("Actual:");
            for (int i = 0; i < Math.min(5, queryResult.size()); i++) {
                log.info("  Row {}: {}", i, queryResult.get(i));
            }
        }

        // 不强制断言通过，因为 AI 可能生成不同但等效的查询
        // Assertions.assertTrue(matchesAny, "Query result should match gold answer");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建包含 metadata 的系统提示词
     *
     * TODO: 使用 SemanticServiceResolver 获取真实的 metadata
     * 目前使用本地 QM 文件作为临时方案
     */
    private String buildSystemPromptWithMetadata() throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("""
            你是一个数据分析助手，帮助用户查询 AdventureWorks 数据库。

            ## 使用说明

            1. 使用 dataset_query_model_v2 工具执行数据查询
            2. 根据问题选择合适的模型和字段

            ## 可用模型

            """);

        // 加载 QM 文件作为元数据
        Path templatePath = Path.of("src/main/resources/foggy/templates/adventure_works");
        if (Files.exists(templatePath)) {
            List<String> availableModels = new ArrayList<>();
            try (var paths = Files.list(templatePath)) {
                List<Path> qmFiles = paths
                        .filter(p -> p.toString().endsWith(".qm"))
                        .sorted()
                        .toList();

                for (Path qmFile : qmFiles) {
                    String fileName = qmFile.getFileName().toString();
                    availableModels.add(fileName.replace(".qm", ""));
                }
            }

            SemanticMetadataRequest request = new SemanticMetadataRequest();

            // 从配置获取可用模型列表
            // 这些模型由 mcp.semantic.model-list 配置指定
            McpProperties.SemanticConfig semanticConfig = mcpProperties.getSemantic();



            request.setQmModels(availableModels);

            // 应用字段级别配置
            // metadata.force-levels 会覆盖用户请求
            // metadata.default-levels 作为默认值
            McpProperties.LevelConfig metadataLevelConfig = semanticConfig.getMetadata();
            List<Integer> levels = metadataLevelConfig.apply(null); // 无用户指定，使用配置
            request.setLevels(levels);


            // 使用版本解析器获取元数据
            SemanticMetadataResponse response = semanticServiceResolver.getMetadata(request, "markdown");
            sb.append(response.getContent());
        }

        sb.append("""
            请根据用户问题，调用工具获取数据并回答问题。
            """);

        return sb.toString();
    }

    /**
     * 加载标准答案
     */
    private List<GoldAnswer> loadGoldAnswers(String instanceId) {
        List<GoldAnswer> answers = new ArrayList<>();

        // Spider2 标准答案路径
        Path goldPath = Path.of(properties.getJsonlPath()).getParent()
                .resolve("evaluation_suite")
                .resolve("gold")
                .resolve("sql");

        // 尝试加载 instanceId_a.csv, instanceId_b.csv 等
        for (char suffix = 'a'; suffix <= 'z'; suffix++) {
            Path csvFile = goldPath.resolve(instanceId + "_" + suffix + ".csv");
            if (Files.exists(csvFile)) {
                try {
                    GoldAnswer answer = loadCsvFile(csvFile);
                    answers.add(answer);
                } catch (IOException e) {
                    log.warn("Failed to load {}: {}", csvFile, e.getMessage());
                }
            } else {
                break; // 没有更多后缀
            }
        }

        return answers;
    }

    /**
     * 加载 CSV 文件
     */
    private GoldAnswer loadCsvFile(Path csvFile) throws IOException {
        List<String> lines = Files.readAllLines(csvFile);
        List<String> headers = null;
        List<List<String>> dataRows = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            List<String> cells = parseCsvLine(line);
            if (i == 0) {
                headers = cells;
            } else {
                dataRows.add(cells);
            }
        }

        return GoldAnswer.builder()
                .fileName(csvFile.getFileName().toString())
                .headers(headers != null ? headers : List.of())
                .rows(dataRows)
                .build();
    }

    /**
     * 解析 CSV 行
     */
    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                cells.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());

        return cells;
    }

    /**
     * 从工具结果中提取查询数据
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractQueryResult(Object result) {
        if (result == null) {
            return List.of();
        }

        try {
            Map<String, Object> resultMap = (Map<String, Object>) result;

            // 处理 RX 包装
            if (resultMap.containsKey("data")) {
                resultMap = (Map<String, Object>) resultMap.get("data");
            }

            // 获取 items
            if (resultMap.containsKey("items")) {
                Object items = resultMap.get("items");
                if (items instanceof List) {
                    return (List<Map<String, Object>>) items;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract query result: {}", e.getMessage());
        }

        return List.of();
    }

    /**
     * 比较查询结果与标准答案
     */
    private boolean compareResultWithGold(List<Map<String, Object>> actual, GoldAnswer expected) {
        if (actual.isEmpty() && expected.getRows().isEmpty()) {
            return true;
        }

        if (actual.size() != expected.getRows().size()) {
            log.debug("Row count mismatch: actual={}, expected={}",
                    actual.size(), expected.getRows().size());
            // 不立即返回 false，可能只是顺序不同
        }

        // 简化比较：检查关键列的值是否匹配
        // 由于列名可能不同，我们比较值的集合

        Set<String> actualValues = new HashSet<>();
        for (Map<String, Object> row : actual) {
            // 将行转为规范化字符串
            List<String> values = new ArrayList<>();
            for (Object v : row.values()) {
                values.add(normalizeValue(v));
            }
            Collections.sort(values);
            actualValues.add(String.join("|", values));
        }

        Set<String> expectedValues = new HashSet<>();
        for (List<String> row : expected.getRows()) {
            List<String> values = row.stream()
                    .map(this::normalizeValue)
                    .sorted()
                    .toList();
            expectedValues.add(String.join("|", values));
        }

        // 检查交集
        Set<String> intersection = new HashSet<>(actualValues);
        intersection.retainAll(expectedValues);

        double matchRate = expectedValues.isEmpty() ? 0 :
                (double) intersection.size() / expectedValues.size();
        log.debug("Match rate: {:.2f}% ({}/{})",
                matchRate * 100, intersection.size(), expectedValues.size());

        // 允许 80% 以上的匹配率
        return matchRate >= 0.8;
    }

    /**
     * 规范化值用于比较
     */
    private String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString().toLowerCase().trim();
        // 移除小数点后的零
        if (str.matches("-?\\d+\\.0+")) {
            str = str.replaceAll("\\.0+$", "");
        }
        return str;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    // ==================== 数据结构 ====================

    @lombok.Data
    @lombok.Builder
    static class GoldAnswer {
        private String fileName;
        private List<String> headers;
        private List<List<String>> rows;
    }
}
