package com.foggyframework.dataviewer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * OpenInViewerTool 单元测试
 * <p>
 * 使用类型安全的 SliceRequestDef
 */
@ExtendWith(MockitoExtension.class)
class OpenInViewerToolTest {

    @Mock
    private QueryCacheService cacheService;

    @Mock
    private QueryScopeConstraintService constraintService;

    private DataViewerProperties properties;
    private ObjectMapper objectMapper;
    private OpenInViewerTool tool;
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        properties = new DataViewerProperties();
        properties.setBaseUrl("http://localhost:8080/data-viewer");
        objectMapper = new ObjectMapper();

        tool = new OpenInViewerTool(cacheService, constraintService, properties, objectMapper);
        context = ToolExecutionContext.builder()
                .traceId("test-trace-id")
                .authorization("Bearer test-token")
                .build();
    }

    @Nested
    @DisplayName("工具元数据测试")
    class MetadataTests {

        @Test
        @DisplayName("应返回正确的工具名称")
        void shouldReturnCorrectName() {
            assertEquals("dataset.open_in_viewer", tool.getName());
        }

        @Test
        @DisplayName("应返回正确的工具分类")
        void shouldReturnCorrectCategories() {
            Set<ToolCategory> categories = tool.getCategories();

            assertTrue(categories.contains(ToolCategory.EXPORT));
            assertTrue(categories.contains(ToolCategory.QUERY));
        }

        @Test
        @DisplayName("应返回非空的描述")
        void shouldReturnNonEmptyDescription() {
            String description = tool.getDescription();

            assertNotNull(description);
            assertFalse(description.isEmpty());
            assertTrue(description.contains("filter"));
        }

        @Test
        @DisplayName("应返回正确的输入Schema")
        void shouldReturnCorrectInputSchema() {
            Map<String, Object> schema = tool.getInputSchema();

            assertNotNull(schema);
            assertEquals("object", schema.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> schemaProps = (Map<String, Object>) schema.get("properties");
            assertNotNull(schemaProps);
            assertTrue(schemaProps.containsKey("model"));
            assertTrue(schemaProps.containsKey("columns"));
            assertTrue(schemaProps.containsKey("slice"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertNotNull(required);
            assertTrue(required.contains("model"));
            assertTrue(required.contains("columns"));
            assertTrue(required.contains("slice"));
        }
    }

    @Nested
    @DisplayName("执行测试")
    class ExecutionTests {

        @Test
        @DisplayName("应成功执行并返回URL")
        void shouldExecuteSuccessfully() {
            List<SliceRequestDef> slice = createSlice("customerId", "=", "C001");

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("model", "orders");
            arguments.put("title", "客户订单");
            arguments.put("columns", List.of("orderId", "customerId", "amount"));
            arguments.put("slice", List.of(Map.of("field", "customerId", "op", "=", "value", "C001")));

            CachedQueryContext cachedContext = CachedQueryContext.builder()
                    .queryId("test-query-id")
                    .expiresAt(Instant.now().plus(60, ChronoUnit.MINUTES))
                    .build();

            when(constraintService.enforceConstraints(anyString(), anyList()))
                    .thenReturn(slice);
            when(cacheService.cacheQuery(any(), anyString()))
                    .thenReturn(cachedContext);

            Object result = tool.execute(arguments, context);

            assertNotNull(result);
            assertTrue(result instanceof Map);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals("test-query-id", resultMap.get("queryId"));
            assertTrue(((String) resultMap.get("viewerUrl")).contains("test-query-id"));
        }

        @Test
        @DisplayName("应处理约束验证失败")
        void shouldHandleConstraintValidationFailure() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("model", "orders");
            arguments.put("title", "无效查询");
            arguments.put("columns", List.of("orderId"));
            arguments.put("slice", new ArrayList<>());

            // 空的 slice 会在 parseRequest 中被拒绝
            assertThrows(IllegalArgumentException.class, () -> tool.execute(arguments, context));
        }

        @Test
        @DisplayName("应处理缺少必需参数model")
        void shouldThrowWhenMissingModel() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("columns", List.of("orderId"));
            arguments.put("slice", List.of(Map.of("field", "id", "op", "=", "value", "1")));

            assertThrows(IllegalArgumentException.class, () -> tool.execute(arguments, context));
        }

        @Test
        @DisplayName("应处理缺少必需参数columns")
        void shouldThrowWhenMissingColumns() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("model", "orders");
            arguments.put("slice", List.of(Map.of("field", "id", "op", "=", "value", "1")));

            assertThrows(IllegalArgumentException.class, () -> tool.execute(arguments, context));
        }

        @Test
        @DisplayName("应处理缺少必需参数slice")
        void shouldThrowWhenMissingSlice() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("model", "orders");
            arguments.put("columns", List.of("orderId"));

            assertThrows(IllegalArgumentException.class, () -> tool.execute(arguments, context));
        }

        @Test
        @DisplayName("应处理空slice")
        void shouldThrowWhenSliceIsEmpty() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("model", "orders");
            arguments.put("columns", List.of("orderId"));
            arguments.put("slice", new ArrayList<>());

            assertThrows(IllegalArgumentException.class, () -> tool.execute(arguments, context));
        }
    }

    @Nested
    @DisplayName("流式支持测试")
    class StreamingSupportTests {

        @Test
        @DisplayName("应不支持流式执行")
        void shouldNotSupportStreaming() {
            assertFalse(tool.supportsStreaming());
        }
    }

    private List<SliceRequestDef> createSlice(String field, String op, String value) {
        List<SliceRequestDef> slice = new ArrayList<>();
        slice.add(new SliceRequestDef(field, op, value));
        return slice;
    }
}
