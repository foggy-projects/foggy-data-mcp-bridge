package com.foggyframework.dataset.mcp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.integration.McpIntegrationTestApplication;
import com.foggyframework.dataset.mcp.integration.config.McpIntegrationTestConfig;
import com.foggyframework.dataset.mcp.service.McpToolCallbackFactory;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import com.foggyframework.dataset.mcp.tools.MetadataTool;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

/**
 * AI 集成测试基类
 *
 * 提供 AI 模型调用 MCP 工具的测试支持
 * 使用 Spring AI 自动配置的 ChatModel
 *
 * <p>测试使用业务代码的工具定义（schemas/ 目录），确保测试和业务代码一致。
 */
@Slf4j
@SpringBootTest(
        classes = McpIntegrationTestApplication.class,
        properties = {
                "spring.ai.openai.api-key=${AI_TEST_OPENAI_API_KEY:sk-test-key-disabled}",
                "spring.ai.openai.chat.api-key=${AI_TEST_OPENAI_API_KEY:sk-test-key-disabled}",
                "spring.ai.openai.base-url=${AI_TEST_OPENAI_BASE_URL:http://127.0.0.1:65535}",
                "spring.ai.openai.chat.options.model=${AI_TEST_OPENAI_MODEL:test-model-disabled}",
                "spring.ai.openai.chat.options.temperature=${AI_TEST_OPENAI_TEMPERATURE:0.1}",
                "spring.ai.openai.chat.options.max-tokens=${AI_TEST_OPENAI_MAX_TOKENS:4096}"
        }
)
@Import(McpIntegrationTestConfig.class)
@ActiveProfiles({"integration", "test"})
public abstract class AiIntegrationTestSupport {

    // ==================== Spring 注入 ====================

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected List<McpTool> mcpTools;

    @Autowired
    protected MetadataTool metadataTool;

    /**
     * MCP 工具分发器 - 管理所有 MCP 工具
     */
    @Autowired
    protected McpToolDispatcher mcpToolDispatcher;

    /**
     * 工具回调工厂 - 将 McpTool 转换为 Spring AI ToolCallback
     * 工具描述和 Schema 从 schemas/ 目录加载
     */
    @Autowired
    protected McpToolCallbackFactory toolCallbackFactory;

    /**
     * Spring AI 自动配置的 ChatModel
     * 根据 application-test.yml 配置自动创建
     */
    @Autowired(required = false)
    protected ChatModel chatModel;

    // ==================== AI 配置 ====================

    @Value("${spring.ai.openai.api-key:}")
    protected String apiKey;

    @Value("${spring.ai.openai.base-url:}")
    protected String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    protected String modelName;

    @Value("${AI_TEST_LLM_ENABLED:false}")
    protected boolean llmEnabled;

    @Value("${AI_TEST_CASE_IDS:}")
    protected String llmCaseIds;

    @Value("${AI_TEST_CATEGORIES:}")
    protected String llmCategories;

    @Value("${AI_TEST_MAX_CASES:0}")
    protected int llmMaxCases;

    @Value("${AI_TEST_LLM_FAIL_ON_MISMATCH:false}")
    protected boolean llmFailOnMismatch;

    @Value("${AI_TEST_CASE_FILES:}")
    protected String aiTestCaseFiles;

    // ==================== 测试工具 ====================

    protected TestCaseLoader testCaseLoader;
    protected ResultValidator resultValidator;
    protected SpringAiTestExecutor testExecutor;

    // ==================== 测试用例路径 ====================

    protected static final String ECOMMERCE_TESTS = "ai-test-cases/ecommerce-tests.json";

    @BeforeEach
    void setUpAiTest() {
        log.info("============================================");
        log.info("AI Integration Test Environment");
        log.info("============================================");
        log.info("ChatModel available: {}", chatModel != null);
        if (chatModel != null) {
            log.info("ChatModel class: {}", chatModel.getClass().getName());
        }
        log.info("Model: {}", modelName);
        log.info("Base URL: {}", baseUrl);
        log.info("LLM enabled: {}", llmEnabled);
        log.info("API Key configured: {}", apiKey != null && !apiKey.isEmpty());
        log.info("API Key (masked): {}", apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 6) + "..." + apiKey.substring(apiKey.length() - 4)
                : "too short or null");

        // 输出完整的请求 URL（Spring AI OpenAI 会追加 /chat/completions）
        String expectedChatUrl = baseUrl + "/chat/completions";
        log.info("Expected Chat URL: {}", expectedChatUrl);

        // 显示已注册的 MCP 工具
        log.info("Registered MCP Tools: {}", mcpToolDispatcher.getAllTools().size());
        for (McpTool tool : mcpToolDispatcher.getAllTools()) {
            log.info("  - {}", tool.getName());
        }

        // 初始化测试工具
        testCaseLoader = new TestCaseLoader(objectMapper);
        resultValidator = new ResultValidator();

        // 获取可用模型描述
        String availableModels = fetchAvailableModels();

        // 创建测试执行器 - 使用业务代码的工具定义
        String traceId = generateTraceId();
        testExecutor = new SpringAiTestExecutor(
                mcpToolDispatcher,
                toolCallbackFactory,
                availableModels,
                chatModel,
                traceId,
                null  // authorization
        );

        log.info("============================================");
    }

    /**
     * 获取可用模型描述
     */
    @SuppressWarnings("unchecked")
    protected String fetchAvailableModels() {
        try {
            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object metadata = metadataTool.execute(Map.of(), context);

            // 从元数据中提取模型列表
            StringBuilder sb = new StringBuilder();
            sb.append("可用模型列表:\n");

            if (metadata instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) metadata;
                // 处理 RX 包装
                if (map.containsKey("data")) {
                    map = (Map<String, Object>) map.get("data");
                }
                if (map.containsKey("models")) {
                    List<Map<String, Object>> models = (List<Map<String, Object>>) map.get("models");
                    for (Map<String, Object> model : models) {
                        String name = (String) model.get("name");
                        String caption = (String) model.get("caption");
                        sb.append("- ").append(name);
                        if (caption != null) {
                            sb.append(" (").append(caption).append(")");
                        }
                        sb.append("\n");
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to fetch available models: {}", e.getMessage());
            return "FactSalesQueryModel (销售事实表)\nFactOrderQueryModel (订单事实表)";
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成唯一的 trace ID
     */
    protected String generateTraceId() {
        return "ai-test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 加载测试用例
     */
    protected List<EcommerceTestCase> loadTestCases() {
        return testCaseLoader.loadMultiple(testCaseResourcePaths().toArray(String[]::new)).stream()
                .filter(EcommerceTestCase::isEnabled)
                .toList();
    }

    /**
     * 按分类加载测试用例
     */
    protected List<EcommerceTestCase> loadTestCases(EcommerceTestCase.TestCategory category) {
        return loadTestCases().stream()
                .filter(tc -> tc.getCategory() == category)
                .toList();
    }

    /**
     * 选择真实 LLM 对比样本。
     */
    protected List<EcommerceTestCase> loadSelectedLlmTestCases() {
        List<EcommerceTestCase> testCases = new ArrayList<>(loadTestCases());

        Set<String> caseIds = parseCsv(llmCaseIds).stream()
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!caseIds.isEmpty()) {
            testCases = testCases.stream()
                    .filter(testCase -> caseIds.contains(testCase.getId().toUpperCase(Locale.ROOT)))
                    .toList();
        }

        Set<EcommerceTestCase.TestCategory> categories = parseCategories(llmCategories);
        if (!categories.isEmpty()) {
            testCases = testCases.stream()
                    .filter(testCase -> categories.contains(testCase.getCategory()))
                    .toList();
        }

        if (llmMaxCases > 0 && testCases.size() > llmMaxCases) {
            testCases = testCases.subList(0, llmMaxCases);
        }

        return testCases;
    }

    protected List<String> testCaseResourcePaths() {
        Set<String> configuredPaths = parseCsv(aiTestCaseFiles);
        if (configuredPaths.isEmpty()) {
            return List.of(ECOMMERCE_TESTS);
        }
        return List.copyOf(configuredPaths);
    }

    private Set<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<EcommerceTestCase.TestCategory> parseCategories(String value) {
        Set<EcommerceTestCase.TestCategory> categories = new LinkedHashSet<>();
        for (String raw : parseCsv(value)) {
            try {
                categories.add(EcommerceTestCase.TestCategory.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown AI test category: " + raw, e);
            }
        }
        return categories;
    }

    /**
     * 打印测试结果摘要
     */
    protected void printTestSummary(List<SpringAiTestExecutor.AiTestResult> results) {
        log.info("\n========== Test Summary ==========");

        long passed = results.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
        long failed = results.size() - passed;

        log.info("Total: {}, Passed: {}, Failed: {}", results.size(), passed, failed);
        log.info("Success Rate: {:.2f}%", (double) passed / results.size() * 100);

        log.info("\nDetails:");
        for (SpringAiTestExecutor.AiTestResult result : results) {
            log.info("  {}", result.getSummary());
        }

        if (failed > 0) {
            log.info("\nFailed Tests:");
            results.stream()
                    .filter(r -> !r.isSuccess())
                    .forEach(r -> {
                        log.info("  [{}] {}", r.getTestCaseId(), r.getQuestion());
                        if (r.getErrorMessage() != null) {
                            log.info("    Error: {}", r.getErrorMessage());
                        }
                        if (r.getValidationResult() != null) {
                            r.getValidationResult().getFailedRules().forEach(rule ->
                                    log.info("    Failed: {}", rule));
                        }
                    });
        }

        log.info("==================================\n");
    }

    /**
     * 打印 JSON 对象
     */
    protected void printJson(Object obj, String description) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            log.info("========== {} ==========", description);
            log.info("\n{}", json);
            log.info("==========================================");
        } catch (Exception e) {
            log.warn("Failed to serialize object: {}", e.getMessage());
            log.info("{}: {}", description, obj);
        }
    }

    /**
     * 检查是否有可用的 AI 模型
     */
    protected boolean hasAvailableAiModel() {
        return llmEnabled
                && chatModel != null
                && apiKey != null
                && !apiKey.isBlank()
                && !apiKey.contains("disabled")
                && !apiKey.contains("placeholder")
                && baseUrl != null
                && !baseUrl.isBlank()
                && !baseUrl.contains("127.0.0.1:65535")
                && modelName != null
                && !modelName.isBlank()
                && !modelName.contains("disabled");
    }

    /**
     * 跳过测试（如果没有 AI 模型可用）
     */
    protected void skipIfNoAiModel() {
        if (!hasAvailableAiModel()) {
            log.warn("No AI ChatModel configured, skipping AI test");
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "ChatModel not available. Check spring.ai.openai configuration.");
        }
    }
}
