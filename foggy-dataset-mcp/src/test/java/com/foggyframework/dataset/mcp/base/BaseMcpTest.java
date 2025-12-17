package com.foggyframework.dataset.mcp.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.dataset.mcp.tools.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

/**
 * MCP 测试基类
 *
 * 提供通用的测试工具方法和 Mock 对象创建
 */
@ActiveProfiles("test")
public abstract class BaseMcpTest {

    @Autowired(required = false)
    protected ObjectMapper objectMapper;

    @BeforeEach
    void baseSetUp() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
    }

    // ==================== McpRequest 构建工具 ====================

    /**
     * 创建 initialize 请求
     */
    protected McpRequest createInitializeRequest() {
        return McpRequest.builder()
                .id(generateRequestId())
                .method("initialize")
                .params(Map.of())
                .build();
    }

    /**
     * 创建 tools/list 请求
     */
    protected McpRequest createToolsListRequest() {
        return McpRequest.builder()
                .id(generateRequestId())
                .method("tools/list")
                .params(Map.of())
                .build();
    }

    /**
     * 创建 tools/call 请求
     */
    protected McpRequest createToolsCallRequest(String toolName, Map<String, Object> arguments) {
        return McpRequest.builder()
                .id(generateRequestId())
                .method("tools/call")
                .params(Map.of(
                        "name", toolName,
                        "arguments", arguments != null ? arguments : Map.of()
                ))
                .build();
    }

    /**
     * 创建 ping 请求
     */
    protected McpRequest createPingRequest() {
        return McpRequest.builder()
                .id(generateRequestId())
                .method("ping")
                .params(Map.of())
                .build();
    }

    // ==================== Mock Tool 创建工具 ====================

    /**
     * 创建指定分类的 Mock 工具
     */
    protected McpTool createMockTool(String name, ToolCategory... categories) {
        return new MockTool(name, Set.of(categories));
    }

    /**
     * 创建带描述的 Mock 工具
     */
    protected McpTool createMockTool(String name, String description, ToolCategory... categories) {
        return new MockTool(name, description, Set.of(categories));
    }

    /**
     * 创建混合分类的工具列表（用于角色过滤测试）
     */
    protected List<McpTool> createMixedCategoryTools() {
        return List.of(
                createMockTool("dataset.get_metadata", "Get metadata", ToolCategory.METADATA),
                createMockTool("dataset.query_model_v2", "Query model", ToolCategory.QUERY),
                createMockTool("dataset_nl.query", "NL Query", ToolCategory.NATURAL_LANGUAGE),
                createMockTool("chart.generate", "Generate chart", ToolCategory.VISUALIZATION),
                createMockTool("dataset.export_with_chart", "Export with chart",
                        ToolCategory.QUERY, ToolCategory.VISUALIZATION, ToolCategory.EXPORT)
        );
    }

    // ==================== 断言辅助方法 ====================

    /**
     * 断言响应成功
     */
    protected void assertResponseSuccess(McpResponse response) {
        assert response != null : "Response should not be null";
        assert response.getError() == null : "Response should not have error: " +
                (response.getError() != null ? response.getError().getMessage() : "");
        assert response.getResult() != null : "Response should have result";
    }

    /**
     * 断言响应失败
     */
    protected void assertResponseError(McpResponse response, int expectedErrorCode) {
        assert response != null : "Response should not be null";
        assert response.getError() != null : "Response should have error";
        assert response.getError().getCode() == expectedErrorCode :
                "Expected error code " + expectedErrorCode + " but got " + response.getError().getCode();
    }

    /**
     * 从响应中提取结果 Map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractResultMap(McpResponse response) {
        assertResponseSuccess(response);
        return (Map<String, Object>) response.getResult();
    }

    /**
     * 从响应中提取工具列表
     */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> extractToolsList(McpResponse response) {
        Map<String, Object> result = extractResultMap(response);
        return (List<Map<String, Object>>) result.get("tools");
    }

    // ==================== 工具方法 ====================

    /**
     * 生成唯一请求 ID
     */
    protected String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成 Trace ID
     */
    protected String generateTraceId() {
        return "trace-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 将对象转换为 JSON 字符串
     */
    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    /**
     * 从 JSON 字符串解析对象
     */
    protected <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    // ==================== 内部 Mock 类 ====================

    /**
     * Mock 工具实现
     */
    protected static class MockTool implements McpTool {
        private final String name;
        private final String description;
        private final Set<ToolCategory> categories;
        private Object executeResult = Map.of("status", "success");
        private RuntimeException executeException;

        public MockTool(String name, Set<ToolCategory> categories) {
            this(name, "Mock tool: " + name, categories);
        }

        public MockTool(String name, String description, Set<ToolCategory> categories) {
            this.name = name;
            this.description = description;
            this.categories = categories;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Map<String, Object> getInputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of()
            );
        }

        @Override
        public Set<ToolCategory> getCategories() {
            return categories;
        }

        @Override
        public Object execute(Map<String, Object> arguments, String traceId, String authorization) {
            if (executeException != null) {
                throw executeException;
            }
            return executeResult;
        }

        /**
         * 设置执行返回结果
         */
        public MockTool withResult(Object result) {
            this.executeResult = result;
            return this;
        }

        /**
         * 设置执行抛出异常
         */
        public MockTool withException(RuntimeException exception) {
            this.executeException = exception;
            return this;
        }
    }
}
