package com.foggyframework.dataset.mcp.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试用例加载器
 *
 * 从 JSON 文件加载 AI 集成测试用例
 */
@Slf4j
public class TestCaseLoader {

    private final ObjectMapper objectMapper;

    public TestCaseLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public TestCaseLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 classpath 加载测试用例
     *
     * @param resourcePath 资源路径，例如 "ai-test-cases/ecommerce-tests.json"
     * @return 测试用例列表
     */
    public List<EcommerceTestCase> loadFromClasspath(String resourcePath) {
        try {
            Resource resource = new ClassPathResource(resourcePath);
            return loadFromInputStream(resource.getInputStream());
        } catch (IOException e) {
            log.error("Failed to load test cases from classpath: {}", resourcePath, e);
            throw new RuntimeException("Failed to load test cases: " + resourcePath, e);
        }
    }

    /**
     * 从输入流加载测试用例
     */
    public List<EcommerceTestCase> loadFromInputStream(InputStream inputStream) throws IOException {
        TestCaseFile file = objectMapper.readValue(inputStream, TestCaseFile.class);
        log.info("Loaded {} test cases (version: {})",
                file.getTestCases().size(), file.getVersion());
        return file.getTestCases();
    }

    /**
     * 加载并过滤启用的测试用例
     */
    public List<EcommerceTestCase> loadEnabled(String resourcePath) {
        return loadFromClasspath(resourcePath).stream()
                .filter(EcommerceTestCase::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * 按分类加载测试用例
     */
    public List<EcommerceTestCase> loadByCategory(String resourcePath,
                                                   EcommerceTestCase.TestCategory category) {
        return loadFromClasspath(resourcePath).stream()
                .filter(EcommerceTestCase::isEnabled)
                .filter(tc -> tc.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * 按难度加载测试用例
     */
    public List<EcommerceTestCase> loadByDifficulty(String resourcePath,
                                                     EcommerceTestCase.DifficultyLevel difficulty) {
        return loadFromClasspath(resourcePath).stream()
                .filter(EcommerceTestCase::isEnabled)
                .filter(tc -> tc.getDifficulty() == difficulty)
                .collect(Collectors.toList());
    }

    /**
     * 按 ID 加载单个测试用例
     */
    public EcommerceTestCase loadById(String resourcePath, String testCaseId) {
        return loadFromClasspath(resourcePath).stream()
                .filter(tc -> tc.getId().equals(testCaseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test case not found: " + testCaseId));
    }

    /**
     * 加载多个测试文件
     */
    public List<EcommerceTestCase> loadMultiple(String... resourcePaths) {
        List<EcommerceTestCase> allCases = new ArrayList<>();
        for (String path : resourcePaths) {
            allCases.addAll(loadFromClasspath(path));
        }
        return allCases;
    }

    /**
     * 测试用例文件结构
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestCaseFile {
        private String version;
        private String description;
        private List<EcommerceTestCase> testCases;
    }

    /**
     * 获取测试用例统计信息
     */
    public TestCaseStats getStats(List<EcommerceTestCase> testCases) {
        TestCaseStats stats = new TestCaseStats();
        stats.setTotal(testCases.size());
        stats.setEnabled((int) testCases.stream().filter(EcommerceTestCase::isEnabled).count());

        // 按分类统计
        stats.setByCategory(testCases.stream()
                .collect(Collectors.groupingBy(
                        EcommerceTestCase::getCategory,
                        Collectors.counting()
                )));

        // 按难度统计
        stats.setByDifficulty(testCases.stream()
                .collect(Collectors.groupingBy(
                        EcommerceTestCase::getDifficulty,
                        Collectors.counting()
                )));

        return stats;
    }

    /**
     * 测试用例统计信息
     */
    @Data
    public static class TestCaseStats {
        private int total;
        private int enabled;
        private Map<EcommerceTestCase.TestCategory, Long> byCategory;
        private Map<EcommerceTestCase.DifficultyLevel, Long> byDifficulty;

        public void print() {
            log.info("=== Test Case Statistics ===");
            log.info("Total: {}, Enabled: {}", total, enabled);
            log.info("By Category:");
            byCategory.forEach((cat, count) -> log.info("  {}: {}", cat, count));
            log.info("By Difficulty:");
            byDifficulty.forEach((diff, count) -> log.info("  {}: {}", diff, count));
        }
    }
}
