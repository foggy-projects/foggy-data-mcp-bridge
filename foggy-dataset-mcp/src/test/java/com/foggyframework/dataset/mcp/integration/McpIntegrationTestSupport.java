package com.foggyframework.dataset.mcp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.integration.config.McpIntegrationTestConfig;
import com.foggyframework.dataset.mcp.tools.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 集成测试基类
 *
 * 提供真实数据库环境下的测试支持
 */
@Slf4j
@SpringBootTest(classes = McpIntegrationTestApplication.class)
@Import(McpIntegrationTestConfig.class)
@ActiveProfiles({"integration"})
public abstract class McpIntegrationTestSupport {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected List<McpTool> mcpTools;

    @BeforeEach
    void setUp() {
        log.info("============================================");
        log.info("MCP Integration Test - Database Environment");
        log.info("Available MCP Tools: {}", mcpTools.stream().map(McpTool::getName).toList());
        log.info("============================================");
    }

    // ==================== 工具辅助方法 ====================

    /**
     * 根据名称获取 MCP 工具
     */
    protected McpTool getTool(String toolName) {
        return mcpTools.stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolName));
    }

    /**
     * 生成唯一的 trace ID
     */
    protected String generateTraceId() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 执行工具并返回结果
     */
    protected Object executeTool(String toolName, Map<String, Object> arguments) {
        McpTool tool = getTool(toolName);
        String traceId = generateTraceId();
        log.info("Executing tool: {} with traceId: {}", toolName, traceId);
        return tool.execute(arguments, traceId, null);
    }

    /**
     * 执行工具并返回结果（带 authorization）
     */
    protected Object executeTool(String toolName, Map<String, Object> arguments, String authorization) {
        McpTool tool = getTool(toolName);
        String traceId = generateTraceId();
        log.info("Executing tool: {} with traceId: {} and authorization", toolName, traceId);
        return tool.execute(arguments, traceId, authorization);
    }

    // ==================== 数据库辅助方法 ====================

    /**
     * 执行 SQL 查询
     */
    protected List<Map<String, Object>> executeQuery(String sql) {
        log.debug("Executing SQL: {}", sql);
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 获取表记录数
     */
    protected Long getTableCount(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /**
     * 验证数据库连接和基本数据
     */
    protected void verifyDatabaseConnection() {
        try {
            Long productCount = getTableCount("dim_product");
            Long customerCount = getTableCount("dim_customer");
            Long salesCount = getTableCount("fact_sales");

            log.info("Database verification:");
            log.info("  - Products: {}", productCount);
            log.info("  - Customers: {}", customerCount);
            log.info("  - Sales records: {}", salesCount);

            if (productCount == 0 || customerCount == 0) {
                log.warn("WARNING: Test data may not be loaded!");
            }
        } catch (Exception e) {
            log.error("Database connection failed: {}", e.getMessage());
            throw new RuntimeException("Database not available", e);
        }
    }

    // ==================== 结果打印辅助方法 ====================

    /**
     * 打印查询结果
     */
    protected void printResults(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            log.info("查询结果为空");
            return;
        }

        log.info("查询结果数量: {}", results.size());
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            log.info("Row {}: {}", i + 1, results.get(i));
        }

        if (results.size() > 10) {
            log.info("... 还有 {} 条记录未显示", results.size() - 10);
        }
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
}
