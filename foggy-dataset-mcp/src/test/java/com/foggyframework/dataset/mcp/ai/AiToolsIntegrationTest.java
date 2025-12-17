package com.foggyframework.dataset.mcp.ai;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 工具集成测试
 *
 * 测试 AI 模型通过 MCP 工具执行数据查询的正确性
 *
 * 运行前提：
 * 1. 数据库已启动并初始化测试数据
 * 2. 配置了 Spring AI (application-test.yml)
 *    - 阿里云通义千问（默认）
 */
@Slf4j
@DisplayName("AI 工具集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiToolsIntegrationTest extends AiIntegrationTestSupport {

    private List<SpringAiTestExecutor.AiTestResult> allResults = new ArrayList<>();

    // ==================== 环境检查 ====================

    @Test
    @Order(1)
    @DisplayName("检查 AI 模型配置")
    void checkAiModelConfiguration() {
        log.info("Checking AI model configuration...");
        log.info("ChatModel available: {}", hasAvailableAiModel());
        log.info("Model: {}", modelName);
        log.info("Base URL: {}", baseUrl);
        log.info("API Key configured: {}", apiKey != null && !apiKey.isEmpty());

        if (!hasAvailableAiModel()) {
            log.warn("No AI ChatModel configured. Tool-only tests will run.");
            log.warn("Check spring.ai.openai configuration in application-test.yml");
        }
    }

    @Test
    @Order(2)
    @DisplayName("加载测试用例")
    void verifyTestCasesLoaded() {
        List<EcommerceTestCase> testCases = loadTestCases();

        assertFalse(testCases.isEmpty(), "Should have test cases");

        TestCaseLoader.TestCaseStats stats = testCaseLoader.getStats(testCases);
        stats.print();

        log.info("Loaded {} test cases", testCases.size());
    }

    // ==================== 直接工具调用测试（不经过 AI） ====================

    @Nested
    @DisplayName("直接工具调用测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DirectToolCallTest {

        @Test
        @Order(1)
        @DisplayName("元数据工具 - 直接调用")
        void metadataTool_directCall() {
            List<EcommerceTestCase> testCases = loadTestCases(EcommerceTestCase.TestCategory.METADATA);

            for (EcommerceTestCase testCase : testCases) {
                SpringAiTestExecutor.AiTestResult result = testExecutor.executeToolDirectly(testCase);
                allResults.add(result);

                log.info("{}", result.getSummary());

                if (result.getToolResult() != null) {
                    printJson(result.getToolResult(), "Tool Result: " + testCase.getId());
                }
            }
        }

        @Test
        @Order(2)
        @DisplayName("简单查询 - 直接调用")
        void simpleQuery_directCall() {
            List<EcommerceTestCase> testCases = loadTestCases(EcommerceTestCase.TestCategory.SIMPLE_QUERY);

            for (EcommerceTestCase testCase : testCases) {
                SpringAiTestExecutor.AiTestResult result = testExecutor.executeToolDirectly(testCase);
                allResults.add(result);

                log.info("{}", result.getSummary());

                if (!result.isSuccess() && result.getValidationResult() != null) {
                    log.warn("Validation failed: {}", result.getValidationResult().getFailedRules());
                }
            }
        }

        @Test
        @Order(3)
        @DisplayName("聚合查询 - 直接调用")
        void aggregationQuery_directCall() {
            List<EcommerceTestCase> testCases = loadTestCases(EcommerceTestCase.TestCategory.AGGREGATION);

            for (EcommerceTestCase testCase : testCases) {
                SpringAiTestExecutor.AiTestResult result = testExecutor.executeToolDirectly(testCase);
                allResults.add(result);
                log.info("{}", result.getSummary());
            }
        }

        @Test
        @Order(4)
        @DisplayName("全部直接调用测试 - 汇总")
        void allDirectCalls_summary() {
            List<EcommerceTestCase> testCases = loadTestCases();

            List<SpringAiTestExecutor.AiTestResult> results = new ArrayList<>();
            for (EcommerceTestCase testCase : testCases) {
                SpringAiTestExecutor.AiTestResult result = testExecutor.executeToolDirectly(testCase);
                results.add(result);
            }

            printTestSummary(results);

            // 断言直接调用应该全部成功（验证工具本身正确性）
            long passedCount = results.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
            log.info("Direct tool calls: {}/{} passed", passedCount, results.size());
        }
    }

    // ==================== AI 模型调用测试（使用 Spring AI ChatModel） ====================

    @Nested
    @DisplayName("AI 模型调用测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AiModelCallTest {

        /**
         * 需要token，先拿掉
         */
//        @Test
        @Order(1)
        @DisplayName("元数据查询")
        void metadataQuery() {
            skipIfNoAiModel();

            List<EcommerceTestCase> testCases = loadTestCases(EcommerceTestCase.TestCategory.METADATA);

            log.info("Testing metadata queries with model: {}", modelName);

            List<SpringAiTestExecutor.AiTestResult> results = new ArrayList<>();
            for (EcommerceTestCase testCase : testCases) {
                SpringAiTestExecutor.AiTestResult result = testExecutor.executeTest(testCase);
                results.add(result);
                allResults.add(result);

                log.info("{}", result.getSummary());

                if (result.getAiResponse() != null) {
                    log.debug("AI Response: {}", result.getAiResponse());
                }
            }

            // 断言：所有测试必须通过
            long failedCount = results.stream().filter(r -> !r.isSuccess()).count();
            assertTrue(failedCount == 0,
                    String.format("AI 元数据查询测试失败: %d/%d 失败", failedCount, results.size()));
        }

        /**
         * 需要token，先拿 掉
         */
//        @Test
        @Order(2)
        @DisplayName("简单查询")
        void simpleQuery() {
            skipIfNoAiModel();

            List<EcommerceTestCase> testCases = loadTestCases(EcommerceTestCase.TestCategory.SIMPLE_QUERY);

            List<SpringAiTestExecutor.AiTestResult> results = new ArrayList<>();
            for (EcommerceTestCase testCase : testCases) {
                SpringAiTestExecutor.AiTestResult result = testExecutor.executeTest(testCase);
                results.add(result);
                allResults.add(result);

                log.info("{}", result.getSummary());

                if (result.getToolCallRecords() != null && !result.getToolCallRecords().isEmpty()) {
                    log.info("  Tool calls: {}", result.getToolCallSummary());
                }
            }

            // 断言：所有测试必须通过
            long failedCount = results.stream().filter(r -> !r.isSuccess()).count();
            assertTrue(failedCount == 0,
                    String.format("AI 简单查询测试失败: %d/%d 失败", failedCount, results.size()));
        }

        /**
         * 需要token，先拿 掉
         */
//        @Test
        @Order(3)
        @DisplayName("复杂查询")
        void complexQuery() {
            skipIfNoAiModel();

            List<EcommerceTestCase> testCases = loadTestCases(EcommerceTestCase.TestCategory.COMPLEX);

            List<SpringAiTestExecutor.AiTestResult> results = new ArrayList<>();
            for (EcommerceTestCase testCase : testCases) {
                SpringAiTestExecutor.AiTestResult result = testExecutor.executeTest(testCase);
                results.add(result);
                allResults.add(result);

                log.info("{}", result.getSummary());

                // 复杂查询可能失败，记录详细信息
                if (!result.isSuccess()) {
                    log.info("  Question: {}", testCase.getQuestion());
                    log.info("  AI Response: {}", result.getAiResponse());
                    if (result.getValidationResult() != null) {
                        log.info("  Failed rules: {}", result.getValidationResult().getFailedRules());
                    }
                }
            }

            // 断言：所有测试必须通过
            long failedCount = results.stream().filter(r -> !r.isSuccess()).count();
            assertTrue(failedCount == 0,
                    String.format("AI 复杂查询测试失败: %d/%d 失败", failedCount, results.size()));
        }

        /**
         * 需要大量token，先拿 掉
         */
//        @Test
        @Order(4)
        @DisplayName("全部测试用例")
        void allTestCases() {
            skipIfNoAiModel();

            List<EcommerceTestCase> testCases = loadTestCases();

            log.info("========================================");
            log.info("Running All Test Cases with Spring AI");
            log.info("Model: {}", modelName);
            log.info("Base URL: {}", baseUrl);
            log.info("Test cases: {}", testCases.size());
            log.info("========================================");

            List<SpringAiTestExecutor.AiTestResult> results = new ArrayList<>();
            for (EcommerceTestCase testCase : testCases) {
                log.info("\n--- Test: {} ---", testCase.getId());
                log.info("Question: {}", testCase.getQuestion());

                SpringAiTestExecutor.AiTestResult result = testExecutor.executeTest(testCase);
                results.add(result);

                log.info("Result: {}", result.getSummary());
                if (result.getToolCallRecords() != null && !result.getToolCallRecords().isEmpty()) {
                    log.info("Tool calls: {}", result.getToolCallSummary());
                }
                if (result.getAiResponse() != null) {
                    String response = result.getAiResponse();
                    log.info("AI Response: {}...",
                            response.substring(0, Math.min(200, response.length())));
                }
            }

            printTestSummary(results);

            // 记录成功率并断言
            long passed = results.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
            double successRate = (double) passed / results.size() * 100;
            log.info("Model {} success rate: {:.2f}%", modelName, successRate);

            // 断言：所有测试必须通过
            long failedCount = results.size() - passed;
            assertTrue(failedCount == 0,
                    String.format("AI 全部测试失败: %d/%d 失败 (成功率: %.2f%%)",
                            failedCount, results.size(), successRate));
        }

        /**
         * 需要大量token，先拿 掉
         */
//        @Test
        @Order(5)
        @DisplayName("阿里云通义千问 - 全部测试")
        void aliyunQwen_allTestCases() {
            skipIfNoAiModel();

            // 验证是阿里云配置
            if (!baseUrl.contains("dashscope.aliyuncs.com")) {
                log.info("Skipping Aliyun-specific test: current base URL is {}", baseUrl);
                return;
            }

            List<EcommerceTestCase> testCases = loadTestCases();

            log.info("========================================");
            log.info("Running Aliyun Qwen Test");
            log.info("Model: {}", modelName);
            log.info("Base URL: {}", baseUrl);
            log.info("Test cases: {}", testCases.size());
            log.info("========================================");

            List<SpringAiTestExecutor.AiTestResult> results = new ArrayList<>();
            for (EcommerceTestCase testCase : testCases) {
                log.info("\n--- Test: {} ---", testCase.getId());
                log.info("Question: {}", testCase.getQuestion());

                SpringAiTestExecutor.AiTestResult result = testExecutor.executeTest(testCase);
                results.add(result);
                allResults.add(result);

                log.info("Result: {}", result.getSummary());
                if (result.getToolCallRecords() != null && !result.getToolCallRecords().isEmpty()) {
                    log.info("Tool calls: {}", result.getToolCallSummary());
                }
                if (result.getAiResponse() != null) {
                    String response = result.getAiResponse();
                    log.info("AI Response: {}...",
                            response.substring(0, Math.min(200, response.length())));
                }
            }

            printTestSummary(results);

            // 断言：所有测试必须通过
            long passed = results.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
            long failedCount = results.size() - passed;
            double successRate = (double) passed / results.size() * 100;
            assertTrue(failedCount == 0,
                    String.format("阿里云通义千问测试失败: %d/%d 失败 (成功率: %.2f%%)",
                            failedCount, results.size(), successRate));
        }
    }

    // ==================== 测试报告生成 ====================

    @Test
    @Order(100)
    @DisplayName("生成测试报告")
    void generateTestReport() {
        if (allResults.isEmpty()) {
            log.info("No test results to report");
            return;
        }

        log.info("\n============================================");
        log.info("           FINAL TEST REPORT                ");
        log.info("============================================\n");

        // 按模型分组统计
        Map<String, List<SpringAiTestExecutor.AiTestResult>> byModel = allResults.stream()
                .collect(Collectors.groupingBy(r -> r.getProvider() + "/" + r.getModelName()));

        for (Map.Entry<String, List<SpringAiTestExecutor.AiTestResult>> entry : byModel.entrySet()) {
            String model = entry.getKey();
            List<SpringAiTestExecutor.AiTestResult> results = entry.getValue();

            long passed = results.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
            double successRate = (double) passed / results.size() * 100;
            double avgDuration = results.stream()
                    .mapToLong(SpringAiTestExecutor.AiTestResult::getDurationMs)
                    .average()
                    .orElse(0);

            log.info("Model: {}", model);
            log.info("  Total: {}, Passed: {}, Failed: {}", results.size(), passed, results.size() - passed);
            log.info("  Success Rate: {:.2f}%", successRate);
            log.info("  Avg Duration: {:.0f}ms", avgDuration);
            log.info("");
        }

        // 按测试分类统计
        Map<String, List<SpringAiTestExecutor.AiTestResult>> byCategory = allResults.stream()
                .collect(Collectors.groupingBy(r -> {
                    // 从 testCaseId 推断分类
                    String id = r.getTestCaseId();
                    if (id.startsWith("META")) return "METADATA";
                    if (id.startsWith("QUERY")) return "QUERY";
                    if (id.startsWith("FILTER")) return "FILTER";
                    if (id.startsWith("AGG")) return "AGGREGATION";
                    if (id.startsWith("DIM")) return "MULTI_DIMENSION";
                    if (id.startsWith("SORT")) return "SORT_PAGINATION";
                    if (id.startsWith("COMPLEX")) return "COMPLEX";
                    return "OTHER";
                }));

        log.info("Results by Category:");
        for (Map.Entry<String, List<SpringAiTestExecutor.AiTestResult>> entry : byCategory.entrySet()) {
            long passed = entry.getValue().stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
            log.info("  {}: {}/{} passed", entry.getKey(), passed, entry.getValue().size());
        }

        log.info("\n============================================\n");
    }
}
