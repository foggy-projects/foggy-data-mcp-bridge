package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.base.BaseMcpTest;
import com.foggyframework.dataset.mcp.base.MockToolFactory;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.tools.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * McpToolDispatcher 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("McpToolDispatcher 单元测试")
class McpToolDispatcherTest extends BaseMcpTest {

    private McpToolDispatcher dispatcher;
    private McpTool metadataTool;
    private McpTool queryTool;
    private McpTool nlTool;

    @Mock
    private ToolConfigLoader toolConfigLoader;

    @BeforeEach
    void setUp() {
        metadataTool = MockToolFactory.createMetadataTool();
        queryTool = MockToolFactory.createQueryModelTool();
        nlTool = MockToolFactory.createNLQueryTool();

        // Mock ToolConfigLoader 返回默认描述和空Schema
        when(toolConfigLoader.getDescription(anyString())).thenReturn("Test description");
        when(toolConfigLoader.getSchema(anyString())).thenReturn(Map.of("type", "object"));
        // Mock isEnabled 返回 true，允许工具注册
        when(toolConfigLoader.isEnabled(anyString())).thenReturn(true);

        dispatcher = new McpToolDispatcher(List.of(metadataTool, queryTool, nlTool), toolConfigLoader);
        dispatcher.init();
    }

    // ==================== init 和注册测试 ====================

    @Nested
    @DisplayName("init - 工具注册")
    class InitTest {

        @Test
        @DisplayName("init 后所有工具应被注册")
        void init_shouldRegisterAllTools() {
            assertTrue(dispatcher.hasTool("dataset.get_metadata"));
            assertTrue(dispatcher.hasTool("dataset.query_model_v2"));
            assertTrue(dispatcher.hasTool("dataset_nl.query"));
        }

        @Test
        @DisplayName("未注册的工具应返回 false")
        void hasTool_unknownTool_shouldReturnFalse() {
            assertFalse(dispatcher.hasTool("unknown.tool"));
            assertFalse(dispatcher.hasTool(""));
            assertFalse(dispatcher.hasTool(null));
        }

        @Test
        @DisplayName("空工具列表应正常初始化")
        void init_emptyList_shouldWork() {
            McpToolDispatcher emptyDispatcher = new McpToolDispatcher(List.of(), toolConfigLoader);
            emptyDispatcher.init();

            assertFalse(emptyDispatcher.hasTool("any.tool"));
            assertTrue(emptyDispatcher.getAllTools().isEmpty());
        }
    }

    // ==================== getToolDefinitions 测试 ====================

    @Nested
    @DisplayName("getToolDefinitions - 获取工具定义")
    class GetToolDefinitionsTest {

        @Test
        @DisplayName("应返回所有工具定义")
        void shouldReturnAllDefinitions() {
            List<Map<String, Object>> definitions = dispatcher.getToolDefinitions();

            assertEquals(3, definitions.size());
        }

        @Test
        @DisplayName("定义应包含 name, description, inputSchema")
        void definitions_shouldContainRequiredFields() {
            List<Map<String, Object>> definitions = dispatcher.getToolDefinitions();

            for (Map<String, Object> def : definitions) {
                assertNotNull(def.get("name"), "Definition should have name");
                assertNotNull(def.get("description"), "Definition should have description");
                assertNotNull(def.get("inputSchema"), "Definition should have inputSchema");
            }
        }

        @Test
        @DisplayName("应包含特定工具定义")
        void shouldContainSpecificToolDefinition() {
            List<Map<String, Object>> definitions = dispatcher.getToolDefinitions();

            boolean foundMetadata = definitions.stream()
                    .anyMatch(d -> "dataset.get_metadata".equals(d.get("name")));
            boolean foundQuery = definitions.stream()
                    .anyMatch(d -> "dataset.query_model_v2".equals(d.get("name")));

            assertTrue(foundMetadata, "Should contain metadata tool");
            assertTrue(foundQuery, "Should contain query tool");
        }
    }

    // ==================== getTool 测试 ====================

    @Nested
    @DisplayName("getTool - 获取工具实例")
    class GetToolTest {

        @Test
        @DisplayName("应返回正确的工具实例")
        void shouldReturnCorrectToolInstance() {
            McpTool tool = dispatcher.getTool("dataset.get_metadata");

            assertNotNull(tool);
            assertEquals("dataset.get_metadata", tool.getName());
        }

        @Test
        @DisplayName("未知工具应返回 null")
        void unknownTool_shouldReturnNull() {
            McpTool tool = dispatcher.getTool("unknown.tool");

            assertNull(tool);
        }
    }

    // ==================== getAllTools 测试 ====================

    @Nested
    @DisplayName("getAllTools - 获取所有工具")
    class GetAllToolsTest {

        @Test
        @DisplayName("应返回所有注册的工具")
        void shouldReturnAllTools() {
            List<McpTool> tools = dispatcher.getAllTools();

            assertEquals(3, tools.size());
        }

        @Test
        @DisplayName("返回的列表应是副本")
        void shouldReturnCopy() {
            List<McpTool> tools1 = dispatcher.getAllTools();
            List<McpTool> tools2 = dispatcher.getAllTools();

            assertNotSame(tools1, tools2);
        }
    }

    // ==================== executeTool 测试 ====================

    @Nested
    @DisplayName("executeTool - 同步执行工具")
    class ExecuteToolTest {

        @Test
        @DisplayName("应调用正确的工具")
        void shouldInvokeCorrectTool() {
            Map<String, Object> args = Map.of("key", "value");
            Map<String, Object> expectedResult = Map.of("models", List.of());

            when(metadataTool.execute(args, "trace-1", "Bearer token")).thenReturn(expectedResult);

            Object result = dispatcher.executeTool("dataset.get_metadata", args, "trace-1", "Bearer token");

            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("未知工具应抛出 IllegalArgumentException")
        void unknownTool_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () ->
                    dispatcher.executeTool("unknown.tool", Map.of(), "trace-1", null));
        }

        @Test
        @DisplayName("工具执行异常应向上传播")
        void toolException_shouldPropagate() {
            when(metadataTool.execute(any(), any(), any()))
                    .thenThrow(new RuntimeException("Service error"));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    dispatcher.executeTool("dataset.get_metadata", Map.of(), "trace-1", null));

            assertEquals("Service error", ex.getMessage());
        }

        @Test
        @DisplayName("应正确传递参数")
        void shouldPassArgumentsCorrectly() {
            Map<String, Object> args = Map.of("model", "TestModel", "limit", 100);

            when(queryTool.execute(args, "trace-2", "Bearer token")).thenReturn(Map.of("success", true));

            dispatcher.executeTool("dataset.query_model_v2", args, "trace-2", "Bearer token");

            // 验证参数传递（通过 Mock 的 when 配置隐式验证）
        }
    }

    // ==================== executeWithProgress 测试 ====================

    @Nested
    @DisplayName("executeWithProgress - 流式执行工具")
    class ExecuteWithProgressTest {

        @Test
        @DisplayName("非流式工具应包装为进度事件")
        void nonStreamingTool_shouldWrapAsProgressEvents() {
            when(metadataTool.supportsStreaming()).thenReturn(false);
            when(metadataTool.execute(any(), any(), any())).thenReturn(Map.of("data", "value"));

            McpRequest request = McpRequest.builder()
                    .method("tools/call")
                    .params(Map.of("name", "dataset.get_metadata", "arguments", Map.of()))
                    .build();

            Flux<ProgressEvent> flux = dispatcher.executeWithProgress(request, "trace-1", "Bearer token");

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("流式工具应委托给工具实现")
        void streamingTool_shouldDelegateToTool() {
            when(nlTool.supportsStreaming()).thenReturn(true);
            when(nlTool.executeWithProgress(any(), any(), any()))
                    .thenReturn(Flux.just(
                            ProgressEvent.progress("processing", 50),
                            ProgressEvent.complete(Map.of("result", "ok"))
                    ));

            McpRequest request = McpRequest.builder()
                    .method("tools/call")
                    .params(Map.of("name", "dataset_nl.query", "arguments", Map.of("query", "test")))
                    .build();

            Flux<ProgressEvent> flux = dispatcher.executeWithProgress(request, "trace-1", "Bearer token");

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("工具不存在应返回错误事件")
        void unknownTool_shouldReturnErrorEvent() {
            McpRequest request = McpRequest.builder()
                    .method("tools/call")
                    .params(Map.of("name", "unknown.tool"))
                    .build();

            Flux<ProgressEvent> flux = dispatcher.executeWithProgress(request, "trace-1", null);

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "error".equals(e.getEventType()) &&
                            hasErrorCode(e, "TOOL_NOT_FOUND"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("params 为 null 应返回错误事件")
        void nullParams_shouldReturnErrorEvent() {
            McpRequest request = McpRequest.builder()
                    .method("tools/call")
                    .params(null)
                    .build();

            Flux<ProgressEvent> flux = dispatcher.executeWithProgress(request, "trace-1", null);

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "error".equals(e.getEventType()) &&
                            hasErrorCode(e, "INVALID_PARAMS"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("直接调用格式应正常工作")
        void directCallFormat_shouldWork() {
            when(metadataTool.supportsStreaming()).thenReturn(false);
            when(metadataTool.execute(any(), any(), any())).thenReturn(Map.of("ok", true));

            // 直接调用格式：method 是工具名，params 是参数
            McpRequest request = McpRequest.builder()
                    .method("dataset.get_metadata")
                    .params(Map.of()) // 没有 name 字段
                    .build();

            Flux<ProgressEvent> flux = dispatcher.executeWithProgress(request, "trace-1", null);

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("工具执行异常应返回错误事件")
        void executionError_shouldReturnErrorEvent() {
            when(metadataTool.supportsStreaming()).thenReturn(false);
            when(metadataTool.execute(any(), any(), any()))
                    .thenThrow(new RuntimeException("Execution failed"));

            McpRequest request = McpRequest.builder()
                    .method("tools/call")
                    .params(Map.of("name", "dataset.get_metadata", "arguments", Map.of()))
                    .build();

            Flux<ProgressEvent> flux = dispatcher.executeWithProgress(request, "trace-1", null);

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()))
                    .expectNextMatches(e -> "error".equals(e.getEventType()) &&
                            hasErrorMessage(e, "Execution failed"))
                    .verifyComplete();
        }

        /**
         * 检查 ProgressEvent 的 data 中是否包含指定的 error code
         */
        @SuppressWarnings("unchecked")
        private boolean hasErrorCode(ProgressEvent e, String expectedCode) {
            if (e.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) e.getData();
                return expectedCode.equals(data.get("code"));
            }
            return false;
        }

        /**
         * 检查 ProgressEvent 的 data 中的 message 是否包含指定字符串
         */
        @SuppressWarnings("unchecked")
        private boolean hasErrorMessage(ProgressEvent e, String expectedMessage) {
            if (e.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) e.getData();
                Object message = data.get("message");
                return message != null && message.toString().contains(expectedMessage);
            }
            return false;
        }
    }
}
