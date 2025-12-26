package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * QueryModelTool 单元测试
 *
 * 使用 Mock 的 DatasetAccessor
 */
@DisplayName("QueryModelTool 单元测试")
@ExtendWith(MockitoExtension.class)
class QueryModelToolTest {

    @Mock
    private DatasetAccessor datasetAccessor;

    private QueryModelTool queryModelTool;

    @BeforeEach
    void setUp() {
        queryModelTool = new QueryModelTool(datasetAccessor);
    }

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("工具基本属性")
    class BasicPropertiesTest {

        @Test
        @DisplayName("getName 应返回正确的工具名称")
        void getName_shouldReturnCorrectName() {
            assertEquals("dataset.query_model_v2", queryModelTool.getName());
        }

        @Test
        @DisplayName("getCategories 应返回 QUERY 类别")
        void getCategories_shouldReturnQueryCategory() {
            assertTrue(queryModelTool.getCategories().contains(ToolCategory.QUERY));
            assertEquals(1, queryModelTool.getCategories().size());
        }

        // Note: getDescription() and getInputSchema() now load from config files,
        // they are tested in integration tests with ToolConfigLoader
    }

    // ==================== execute 参数验证测试 ====================

    @Nested
    @DisplayName("execute - 参数验证")
    class ParameterValidationTest {

        @Test
        @DisplayName("缺少 model 参数应返回错误")
        void missingModel_shouldReturnError() {
            Map<String, Object> args = new HashMap<>();
            args.put("payload", Map.of("columns", List.of("name")));

            Object result = queryModelTool.execute(args, "trace-1", null);

            assertIsError(result, "缺少必要参数: model");
        }

        @Test
        @DisplayName("model 为空字符串应返回错误")
        void emptyModel_shouldReturnError() {
            Map<String, Object> args = new HashMap<>();
            args.put("model", "  ");
            args.put("payload", Map.of("columns", List.of("name")));

            Object result = queryModelTool.execute(args, "trace-2", null);

            assertIsError(result, "缺少必要参数: model");
        }

        @Test
        @DisplayName("缺少 payload 参数应返回错误")
        void missingPayload_shouldReturnError() {
            Map<String, Object> args = new HashMap<>();
            args.put("model", "TestModel");

            Object result = queryModelTool.execute(args, "trace-3", null);

            assertIsError(result, "缺少必要参数: payload");
        }

        @Test
        @DisplayName("payload 为 null 应返回错误")
        void nullPayload_shouldReturnError() {
            Map<String, Object> args = new HashMap<>();
            args.put("model", "TestModel");
            args.put("payload", null);

            Object result = queryModelTool.execute(args, "trace-4", null);

            assertIsError(result, "缺少必要参数: payload");
        }
    }

    // ==================== execute 成功场景测试 ====================

    @Nested
    @DisplayName("execute - 成功场景")
    class ExecuteSuccessTest {

        @Test
        @DisplayName("简单查询应成功执行")
        void simpleQuery_shouldExecuteSuccessfully() {
            SemanticQueryResponse mockResponse = new SemanticQueryResponse();
            mockResponse.setItems(List.of(
                    Map.of("id", 1, "name", "Product A", "price", 100),
                    Map.of("id", 2, "name", "Product B", "price", 200)
            ));
            mockResponse.setTotal(2L);
            mockResponse.setHasNext(false);

            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(mockResponse));

            Map<String, Object> args = Map.of(
                    "model", "ProductModel",
                    "payload", Map.of("columns", List.of("id", "name", "price"))
            );

            Object result = queryModelTool.execute(args, "trace-success-1", null);

            assertNotNull(result);
            assertInstanceOf(RX.class, result);
            @SuppressWarnings("unchecked")
            RX<SemanticQueryResponse> rxResult = (RX<SemanticQueryResponse>) result;
            assertEquals(2L, rxResult.getData().getTotal());
            assertEquals(2, rxResult.getData().getItems().size());

            // 验证调用
            verify(datasetAccessor).queryModel(
                    eq("ProductModel"),
                    any(),
                    eq("execute"),
                    eq("trace-success-1"),
                    isNull()
            );
        }

        @Test
        @DisplayName("带过滤条件的查询应正确传递参数")
        void queryWithSlice_shouldPassCorrectParams() {
            SemanticQueryResponse mockResponse = new SemanticQueryResponse();
            mockResponse.setItems(List.of());
            mockResponse.setTotal(0L);
            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(mockResponse));

            Map<String, Object> payload = Map.of(
                    "columns", List.of("name", "price"),
                    "slice", List.of(
                            Map.of("name", "category", "type", "eq", "value", "electronics"),
                            Map.of("name", "price", "type", "gt", "value", 100)
                    ),
                    "limit", 50
            );

            Map<String, Object> args = Map.of(
                    "model", "ProductModel",
                    "payload", payload
            );

            queryModelTool.execute(args, "trace-slice", null);

            verify(datasetAccessor).queryModel(
                    eq("ProductModel"),
                    eq(payload),
                    eq("execute"),
                    eq("trace-slice"),
                    isNull()
            );
        }

        @Test
        @DisplayName("带分组的查询应正确执行")
        void queryWithGroupBy_shouldExecuteSuccessfully() {
            SemanticQueryResponse mockResponse = new SemanticQueryResponse();
            mockResponse.setItems(List.of(
                    Map.of("category", "Electronics", "totalSales", 50000),
                    Map.of("category", "Clothing", "totalSales", 30000)
            ));
            mockResponse.setTotal(2L);

            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(mockResponse));

            Map<String, Object> payload = Map.of(
                    "columns", List.of("category$caption", "SUM(amount) as totalSales"),
                    "groupBy", List.of("category")
            );

            Map<String, Object> args = Map.of(
                    "model", "SalesModel",
                    "payload", payload
            );

            Object result = queryModelTool.execute(args, "trace-groupby", null);

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            RX<SemanticQueryResponse> rxResult = (RX<SemanticQueryResponse>) result;
            assertEquals(2, rxResult.getData().getItems().size());
        }

        @Test
        @DisplayName("validate 模式应正确传递")
        void validateMode_shouldPassCorrectly() {
            SemanticQueryResponse mockResponse = new SemanticQueryResponse();
            mockResponse.setItems(List.of());
            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(mockResponse));

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("field1")),
                    "mode", "validate"
            );

            queryModelTool.execute(args, "trace-validate", null);

            verify(datasetAccessor).queryModel(
                    eq("TestModel"),
                    any(),
                    eq("validate"),
                    anyString(),
                    any()
            );
        }

        @Test
        @DisplayName("默认 mode 应为 execute")
        void defaultMode_shouldBeExecute() {
            SemanticQueryResponse mockResponse = new SemanticQueryResponse();
            mockResponse.setItems(List.of());
            mockResponse.setTotal(0L);
            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(mockResponse));

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("field1"))
            );

            queryModelTool.execute(args, "trace-default-mode", null);

            verify(datasetAccessor).queryModel(
                    anyString(),
                    any(),
                    eq("execute"),
                    anyString(),
                    any()
            );
        }

        @Test
        @DisplayName("分页查询应正确处理 cursor")
        void paginationWithCursor_shouldWork() {
            SemanticQueryResponse mockResponse = new SemanticQueryResponse();
            mockResponse.setItems(List.of(Map.of("id", 21)));
            mockResponse.setTotal(100L);
            mockResponse.setHasNext(true);
            mockResponse.setCursor("cursor_page_3");

            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(mockResponse));

            Map<String, Object> payload = Map.of(
                    "columns", List.of("id"),
                    "limit", 20,
                    "cursor", "cursor_page_2"
            );

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", payload
            );

            Object result = queryModelTool.execute(args, "trace-pagination", null);

            @SuppressWarnings("unchecked")
            RX<SemanticQueryResponse> rxResult = (RX<SemanticQueryResponse>) result;
            assertEquals(true, rxResult.getData().getHasNext());
            assertEquals("cursor_page_3", rxResult.getData().getCursor());
        }

        @Test
        @DisplayName("应正确传递 authorization")
        void shouldPassAuthorization() {
            SemanticQueryResponse mockResponse = new SemanticQueryResponse();
            mockResponse.setItems(List.of());
            mockResponse.setTotal(0L);
            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(RX.success(mockResponse));

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("field1"))
            );

            queryModelTool.execute(args, "trace-auth", "Bearer token123");

            verify(datasetAccessor).queryModel(
                    anyString(),
                    any(),
                    anyString(),
                    anyString(),
                    eq("Bearer token123")
            );
        }
    }

    // ==================== execute 错误场景测试 ====================

    @Nested
    @DisplayName("execute - 错误场景")
    class ExecuteErrorTest {

        @Test
        @DisplayName("服务返回错误应返回错误响应")
        void serverError_shouldReturnError() {
            // 使用 RX 的错误响应
            RX<SemanticQueryResponse> errorResponse = RX.failB("Query execution failed");

            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(errorResponse);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("field1"))
            );

            Object result = queryModelTool.execute(args, "trace-500", null);

            assertNotNull(result);
            assertInstanceOf(RX.class, result);
            @SuppressWarnings("unchecked")
            RX<SemanticQueryResponse> rxResult = (RX<SemanticQueryResponse>) result;
            assertNotEquals(200, rxResult.getCode());
        }

        @Test
        @DisplayName("服务抛出异常应被正确处理")
        void serviceException_shouldBeHandled() {
            when(datasetAccessor.queryModel(anyString(), any(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Connection failed"));

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("field1"))
            );

            // 工具层不捕获异常，异常由调用者处理
            // 在实际使用中，DatasetAccessor 的实现会捕获异常并返回错误响应
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                queryModelTool.execute(args, "trace-exception", null);
            });

            assertEquals("Connection failed", exception.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private void assertIsError(Object result, String expectedMessagePart) {
        assertNotNull(result);
        assertInstanceOf(RX.class, result);

        @SuppressWarnings("unchecked")
        RX<?> rxResult = (RX<?>) result;
        assertNotEquals(200, rxResult.getCode());
        assertTrue(rxResult.getMsg().contains(expectedMessagePart)
                || (rxResult.getUserTip() != null && rxResult.getUserTip().contains(expectedMessagePart)));
    }
}
