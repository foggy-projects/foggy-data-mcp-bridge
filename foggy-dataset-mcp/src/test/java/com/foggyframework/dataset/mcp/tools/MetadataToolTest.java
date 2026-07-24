package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MetadataTool 单元测试
 *
 * 使用 Mock 的 DatasetAccessor
 */
@DisplayName("MetadataTool 单元测试")
@ExtendWith(MockitoExtension.class)
class MetadataToolTest {

    private static final Map<String, Object> GOVERNANCE_OPTIONS = Map.of(
            "deniedColumns",
            List.of(Map.of("table", "hr_employee", "columns", List.of("gender", "marital")))
    );

    @Mock
    private DatasetAccessor datasetAccessor;

    private MetadataTool metadataTool;

    @BeforeEach
    void setUp() {
        metadataTool = new MetadataTool(datasetAccessor);
    }

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("工具基本属性")
    class BasicPropertiesTest {

        @Test
        @DisplayName("getName 应返回正确的工具名称")
        void getName_shouldReturnCorrectName() {
            assertEquals("dataset.get_metadata", metadataTool.getName());
        }

        @Test
        @DisplayName("getCategories 应返回 METADATA 类别")
        void getCategories_shouldReturnMetadataCategory() {
            assertTrue(metadataTool.getCategories().contains(ToolCategory.METADATA));
            assertEquals(1, metadataTool.getCategories().size());
        }

        @Test
        @DisplayName("supportsStreaming 默认应返回 false")
        void supportsStreaming_shouldReturnFalse() {
            assertFalse(metadataTool.supportsStreaming());
        }
    }

    // ==================== execute 成功场景测试 ====================

    @Nested
    @DisplayName("execute - 成功场景")
    class ExecuteSuccessTest {

        @Test
        @DisplayName("应成功获取元数据")
        void shouldFetchMetadataSuccessfully() {
            // 准备模拟响应
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setFormat("json");
            response.setData(Map.of(
                    "models", List.of(
                            Map.of("name", "FactSalesModel", "caption", "销售事实表"),
                            Map.of("name", "DimCustomerModel", "caption", "客户维度表")
                    ),
                    "version", "1.0.0"
            ));

            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            // 执行
            Object result = metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-123", null));

            // 验证
            assertNotNull(result);
            assertInstanceOf(RX.class, result);

            @SuppressWarnings("unchecked")
            RX<SemanticMetadataResponse> rxResult = (RX<SemanticMetadataResponse>) result;
            assertNotNull(rxResult.getData());
            assertNotNull(rxResult.getData().getData());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> models = (List<Map<String, Object>>) rxResult.getData().getData().get("models");
            assertEquals(2, models.size());

            // 验证调用参数（namespace 为 null）
            verify(datasetAccessor).getMetadata(eq("trace-123"), isNull(), isNull(), eq(Map.of()));
        }

        @Test
        @DisplayName("应正确传递 traceId")
        void shouldPassTraceId() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            metadataTool.execute(Map.of(), ToolExecutionContext.of("custom-trace-id-456", null));

            verify(datasetAccessor).getMetadata(eq("custom-trace-id-456"), isNull(), isNull(), eq(Map.of()));
        }

        @Test
        @DisplayName("应正确传递 authorization")
        void shouldPassAuthorization() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), anyString(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-789", "Bearer token123"));

            verify(datasetAccessor).getMetadata(eq("trace-789"), eq("Bearer token123"), isNull(), eq(Map.of()));
        }

        @Test
        @DisplayName("参数应被忽略（无参工具）")
        void argumentsShouldBeIgnored() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            // 即使传入参数也应正常执行
            Object result = metadataTool.execute(
                    Map.of("some", "argument", "another", 123),
                    ToolExecutionContext.of("trace-ignored", null)
            );

            assertNotNull(result);
            verify(datasetAccessor).getMetadata(anyString(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("应透传 deniedColumns 等治理参数")
        void shouldPassGovernanceArguments() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            Map<String, Object> arguments = new LinkedHashMap<>(GOVERNANCE_OPTIONS);

            Object result = metadataTool.execute(arguments, ToolExecutionContext.of("trace-governance", null));

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            RX<SemanticMetadataResponse> rxResult = (RX<SemanticMetadataResponse>) result;
            assertNotNull(rxResult.getData());
            verify(datasetAccessor).getMetadata(
                    eq("trace-governance"),
                    isNull(),
                    isNull(),
                    same(arguments)
            );
        }
    }

    // ==================== Namespace 传递测试 ====================

    @Nested
    @DisplayName("execute - Namespace 传递")
    class NamespaceTest {

        @Test
        @DisplayName("应正确传递 namespace 到 DatasetAccessor")
        void shouldPassNamespaceToAccessor() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), any(), anyString(), anyMap()))
                    .thenReturn(RX.success(response));

            ToolExecutionContext context = ToolExecutionContext.builder()
                    .traceId("trace-ns")
                    .authorization("Bearer token")
                    .namespace("odoo-prod")
                    .build();

            metadataTool.execute(Map.of(), context);

            verify(datasetAccessor).getMetadata(
                    eq("trace-ns"),
                    eq("Bearer token"),
                    eq("odoo-prod"),
                    eq(Map.of())
            );
        }

        @Test
        @DisplayName("namespace 为 null 时应传递 null")
        void nullNamespace_shouldPassNull() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-ns-null", null));

            verify(datasetAccessor).getMetadata(anyString(), any(), isNull(), eq(Map.of()));
        }

        @Test
        @DisplayName("应同时传递 authorization 和 namespace")
        void shouldPassBothAuthAndNamespace() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(RX.success(response));

            ToolExecutionContext context = ToolExecutionContext.builder()
                    .traceId("trace-multi")
                    .authorization("Bearer jwt")
                    .namespace("tenant-b")
                    .build();

            metadataTool.execute(Map.of(), context);

            verify(datasetAccessor).getMetadata(
                    eq("trace-multi"),
                    eq("Bearer jwt"),
                    eq("tenant-b"),
                    eq(Map.of())
            );
        }
    }

    // ==================== execute 错误场景测试 ====================

    @Nested
    @DisplayName("execute - 错误场景")
    class ExecuteErrorTest {

        @Test
        @DisplayName("服务返回错误应返回错误响应")
        void serviceError_shouldReturnErrorResponse() {
            // 使用 RX 的错误响应
            RX<SemanticMetadataResponse> errorResponse = RX.failB("Service unavailable");
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(errorResponse);

            Object result = metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-error-1", null));

            assertNotNull(result);
            assertInstanceOf(RX.class, result);

            @SuppressWarnings("unchecked")
            RX<SemanticMetadataResponse> rxResult = (RX<SemanticMetadataResponse>) result;
            assertNotEquals(200, rxResult.getCode());
        }

        @Test
        @DisplayName("服务抛出异常应被正确处理")
        void serviceException_shouldBeHandled() {
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenThrow(new RuntimeException("Connection failed"));

            // 工具层不捕获异常，异常由调用者处理
            // 在实际使用中，DatasetAccessor 的实现会捕获异常并返回错误响应
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-exception", null));
            });

            assertEquals("Connection failed", exception.getMessage());
        }
    }

    // ==================== 响应格式测试 ====================

    @Nested
    @DisplayName("响应格式验证")
    class ResponseFormatTest {

        @Test
        @DisplayName("应正确解析复杂的元数据结构")
        void shouldParseComplexMetadataStructure() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setFormat("json");
            response.setData(Map.of(
                    "models", List.of(
                            Map.of(
                                    "name", "FactSalesModel",
                                    "caption", "销售事实表",
                                    "measures", List.of("totalAmount", "quantity"),
                                    "dimensions", List.of(
                                            Map.of("name", "customer", "type", "FK"),
                                            Map.of("name", "product", "type", "FK")
                                    )
                            )
                    ),
                    "enums", Map.of(
                            "OrderStatus", List.of("PENDING", "CONFIRMED", "SHIPPED")
                    ),
                    "version", "2.0.0",
                    "lastUpdate", "2025-01-15T10:30:00"
            ));

            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            Object result = metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-complex", null));

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            RX<SemanticMetadataResponse> rxResult = (RX<SemanticMetadataResponse>) result;

            assertEquals("2.0.0", rxResult.getData().getData().get("version"));
            assertNotNull(rxResult.getData().getData().get("models"));
            assertNotNull(rxResult.getData().getData().get("enums"));
        }

        @Test
        @DisplayName("空模型列表应正常返回")
        void emptyModelList_shouldReturnSuccessfully() {
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of(), "version", "1.0.0"));
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            Object result = metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-empty", null));

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            RX<SemanticMetadataResponse> rxResult = (RX<SemanticMetadataResponse>) result;
            @SuppressWarnings("unchecked")
            List<?> models = (List<?>) rxResult.getData().getData().get("models");
            assertTrue(models.isEmpty());
        }
    }

    // ==================== AccessMode 测试 ====================

    @Nested
    @DisplayName("AccessMode 日志")
    class AccessModeTest {

        @Test
        @DisplayName("应调用 getAccessMode 获取模式信息")
        void shouldLogAccessMode() {
            when(datasetAccessor.getAccessMode()).thenReturn("local");
            SemanticMetadataResponse response = new SemanticMetadataResponse();
            response.setData(Map.of("models", List.of()));
            when(datasetAccessor.getMetadata(anyString(), any(), any(), anyMap()))
                    .thenReturn(RX.success(response));

            metadataTool.execute(Map.of(), ToolExecutionContext.of("trace-mode", null));

            verify(datasetAccessor).getAccessMode();
        }
    }
}
