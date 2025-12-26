package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExportWithChartTool 单元测试
 *
 * 测试组合查询和图表生成功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExportWithChartTool 单元测试")
class ExportWithChartToolTest {

    @Mock
    private QueryModelTool queryModelTool;

    @Mock
    private ChartTool chartTool;

    private ExportWithChartTool exportWithChartTool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        exportWithChartTool = new ExportWithChartTool(queryModelTool, chartTool, objectMapper);
    }

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("工具基本属性")
    class BasicPropertiesTest {

        @Test
        @DisplayName("getName 应返回正确的工具名称")
        void getName_shouldReturnCorrectName() {
            assertEquals("dataset.export_with_chart", exportWithChartTool.getName());
        }

        @Test
        @DisplayName("getCategories 应返回多个类别")
        void getCategories_shouldReturnMultipleCategories() {
            var categories = exportWithChartTool.getCategories();
            assertTrue(categories.contains(ToolCategory.QUERY));
            assertTrue(categories.contains(ToolCategory.VISUALIZATION));
            assertTrue(categories.contains(ToolCategory.EXPORT));
            assertEquals(3, categories.size());
        }

        @Test
        @DisplayName("supportsStreaming 应返回 true")
        void supportsStreaming_shouldReturnTrue() {
            assertTrue(exportWithChartTool.supportsStreaming());
        }

        // Note: getDescription() and getInputSchema() now load from config files,
        // they are tested in integration tests with ToolConfigLoader
    }

    // ==================== execute 成功场景测试 ====================

    @Nested
    @DisplayName("execute - 成功场景")
    class ExecuteSuccessTest {

        @Test
        @DisplayName("查询和图表生成应成功")
        void queryAndChartGeneration_shouldSucceed() {
            // 模拟查询结果
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(
                    Map.of("category", "Electronics", "sales", 50000),
                    Map.of("category", "Clothing", "sales", 30000)
            ));
            queryResponse.setTotal(2L);

            // 模拟图表结果
            Map<String, Object> chartResult = Map.of(
                    "success", true,
                    "chart", Map.of(
                            "url", "file:///tmp/chart.png",
                            "type", "BAR"
                    )
            );

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> args = Map.of(
                    "model", "SalesModel",
                    "payload", Map.of(
                            "columns", List.of("category", "sales"),
                            "groupBy", List.of("category")
                    )
            );

            Object result = exportWithChartTool.execute(args, "trace-1", null);

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;

            assertEquals("result", resultMap.get("type"));
            assertEquals(2L, resultMap.get("total"));

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) resultMap.get("items");
            assertEquals(2, items.size());

            @SuppressWarnings("unchecked")
            Map<String, Object> exports = (Map<String, Object>) resultMap.get("exports");
            assertNotNull(exports.get("charts"));
        }

        @Test
        @DisplayName("应正确传递查询参数")
        void shouldPassQueryParamsCorrectly() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(Map.of("x", 1)));
            queryResponse.setTotal(1L);
            Map<String, Object> chartResult = Map.of("success", true, "chart", Map.of("url", "test"));

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> payload = Map.of(
                    "columns", List.of("name", "value"),
                    "slice", List.of(Map.of("name", "status", "type", "eq", "value", "active")),
                    "orderBy", List.of(Map.of("name", "value", "dir", "desc")),
                    "limit", 100
            );

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", payload
            );

            exportWithChartTool.execute(args, "trace-2", null);

            verify(queryModelTool).executeQuery(
                    eq("TestModel"),
                    eq(payload),
                    eq("execute"),
                    eq("trace-2"),
                    isNull()
            );
        }

        @Test
        @DisplayName("自定义图表配置应正确传递")
        void customChartConfig_shouldPassCorrectly() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(Map.of("month", "Jan", "sales", 100)));
            queryResponse.setTotal(1L);
            Map<String, Object> chartResult = Map.of("success", true, "chart", Map.of("url", "test"));

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> chartConfig = Map.of(
                    "type", "line",
                    "title", "Custom Title",
                    "xField", "month",
                    "yField", "sales",
                    "width", 1000,
                    "height", 700
            );

            Map<String, Object> args = Map.of(
                    "model", "SalesModel",
                    "payload", Map.of("columns", List.of("month", "sales")),
                    "chart", chartConfig
            );

            exportWithChartTool.execute(args, "trace-3", null);

            verify(chartTool).execute(argThat(chartArgs -> {
                assertEquals("line", chartArgs.get("type"));
                assertEquals("Custom Title", chartArgs.get("title"));
                assertEquals("month", chartArgs.get("xField"));
                assertEquals("sales", chartArgs.get("yField"));
                assertEquals(1000, chartArgs.get("width"));
                assertEquals(700, chartArgs.get("height"));
                return true;
            }), eq("trace-3"), any());
        }

        @Test
        @DisplayName("auto 图表类型应根据数据推断")
        void autoChartType_shouldBeInferred() {
            // 少量分组数据应推断为饼图
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(
                    Map.of("category", "A", "value", 10),
                    Map.of("category", "B", "value", 20),
                    Map.of("category", "C", "value", 30)
            ));
            queryResponse.setTotal(3L);
            Map<String, Object> chartResult = Map.of("success", true, "chart", Map.of("url", "test"));

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of(
                            "columns", List.of("category", "value"),
                            "groupBy", List.of("category")
                    ),
                    "chart", Map.of("type", "auto")
            );

            exportWithChartTool.execute(args, "trace-4", null);

            verify(chartTool).execute(argThat(chartArgs -> {
                // 少于8个分类应推断为饼图
                assertEquals("pie", chartArgs.get("type"));
                return true;
            }), any(), any());
        }

        @Test
        @DisplayName("时间字段应推断为线图")
        void timeField_shouldInferLineChart() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(
                    Map.of("orderDate", "2025-01-01", "sales", 100),
                    Map.of("orderDate", "2025-01-02", "sales", 120)
            ));
            queryResponse.setTotal(2L);
            Map<String, Object> chartResult = Map.of("success", true, "chart", Map.of("url", "test"));

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("orderDate", "sales")),
                    "chart", Map.of("type", "auto")
            );

            exportWithChartTool.execute(args, "trace-5", null);

            verify(chartTool).execute(argThat(chartArgs -> {
                assertEquals("line", chartArgs.get("type"));
                return true;
            }), any(), any());
        }

        @Test
        @DisplayName("字段应自动推断")
        void fields_shouldBeInferredAutomatically() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(
                    Map.of("category", "A", "amount", 100),
                    Map.of("category", "B", "amount", 200)
            ));
            queryResponse.setTotal(2L);
            Map<String, Object> chartResult = Map.of("success", true, "chart", Map.of("url", "test"));

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of(
                            "columns", List.of("category", "amount"),
                            "groupBy", List.of("category")
                    )
                    // 不指定 xField, yField
            );

            exportWithChartTool.execute(args, "trace-6", null);

            verify(chartTool).execute(argThat(chartArgs -> {
                // xField 应从 groupBy 推断
                assertEquals("category", chartArgs.get("xField"));
                // yField 应从数值字段推断
                assertEquals("amount", chartArgs.get("yField"));
                return true;
            }), any(), any());
        }
    }

    // ==================== execute 错误场景测试 ====================

    @Nested
    @DisplayName("execute - 错误场景")
    class ExecuteErrorTest {

        @Test
        @DisplayName("查询失败应返回错误")
        void queryFailed_shouldReturnError() {
            RX<SemanticQueryResponse> queryError = RX.failB("Model not found");

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(queryError);

            Map<String, Object> args = Map.of(
                    "model", "NonExistentModel",
                    "payload", Map.of("columns", List.of("field1"))
            );

            Object result = exportWithChartTool.execute(args, "trace-error-1", null);

            // 应直接返回查询错误 RX
            assertInstanceOf(RX.class, result);
            @SuppressWarnings("unchecked")
            RX<SemanticQueryResponse> rxResult = (RX<SemanticQueryResponse>) result;
            assertNotEquals(200, rxResult.getCode());

            // 图表生成不应被调用
            verify(chartTool, never()).execute(any(), any(), any());
        }

        @Test
        @DisplayName("查询结果为空应返回提示")
        void emptyQueryResult_shouldReturnMessage() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of());
            queryResponse.setTotal(0L);

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("field1"))
            );

            Object result = exportWithChartTool.execute(args, "trace-empty", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue(resultMap.get("summary").toString().contains("查询结果为空"));

            // 图表生成不应被调用
            verify(chartTool, never()).execute(any(), any(), any());
        }

        @Test
        @DisplayName("图表生成失败应仍返回查询结果")
        void chartGenerationFailed_shouldStillReturnQueryResult() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(Map.of("x", 1, "y", 2)));
            queryResponse.setTotal(1L);
            Map<String, Object> chartError = Map.of(
                    "success", false,
                    "error", true,
                    "message", "Chart service unavailable"
            );

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartError);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("x", "y"))
            );

            Object result = exportWithChartTool.execute(args, "trace-chart-error", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;

            // 查询结果应包含
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) resultMap.get("items");
            assertEquals(1, items.size());

            // exports.charts 应为空（图表生成失败）
            @SuppressWarnings("unchecked")
            Map<String, Object> exports = (Map<String, Object>) resultMap.get("exports");
            assertNull(exports.get("charts"));
        }

        @Test
        @DisplayName("异常应返回错误响应")
        void exception_shouldReturnError() {
            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("field1"))
            );

            Object result = exportWithChartTool.execute(args, "trace-exception", null);

            // 异常情况返回 RX 错误
            assertInstanceOf(RX.class, result);
            @SuppressWarnings("unchecked")
            RX<?> rxResult = (RX<?>) result;
            assertNotEquals(200, rxResult.getCode());
            assertTrue(rxResult.getMsg().contains("导出失败"));
        }
    }

    // ==================== executeWithProgress 测试 ====================

    @Nested
    @DisplayName("executeWithProgress - 流式执行")
    class ExecuteWithProgressTest {

        @Test
        @DisplayName("应发出正确的进度事件序列")
        void shouldEmitCorrectProgressSequence() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(Map.of("x", 1, "y", 2)));
            queryResponse.setTotal(1L);
            Map<String, Object> chartResult = Map.of(
                    "success", true,
                    "chart", Map.of("url", "test")
            );

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("x", "y"))
            );

            Flux<ProgressEvent> flux = exportWithChartTool.executeWithProgress(args, "trace-stream", null);

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 20))
                    .expectNextMatches(e -> "partial_result".equals(e.getEventType()))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 50))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 90))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("查询错误应发出错误事件")
        void queryError_shouldEmitErrorEvent() {
            RX<SemanticQueryResponse> queryError = RX.failB("Query failed");

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(queryError);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("x"))
            );

            Flux<ProgressEvent> flux = exportWithChartTool.executeWithProgress(args, "trace-error-stream", null);

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 20))
                    .expectNextMatches(e -> "error".equals(e.getEventType()) &&
                            hasErrorCode(e, "QUERY_ERROR"))
                    .verifyComplete();
        }
    }

    // ==================== 数据提取测试 ====================

    @Nested
    @DisplayName("数据提取")
    class DataExtractionTest {

        @Test
        @DisplayName("应正确返回查询项")
        void shouldReturnQueryItems() {
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(Map.of("x", 1)));
            queryResponse.setTotal(1L);
            Map<String, Object> chartResult = Map.of("success", true, "chart", Map.of("url", "test"));

            when(queryModelTool.executeQuery(anyString(), any(), anyString(), anyString(), any()))
                    .thenReturn(RX.success(queryResponse));
            when(chartTool.execute(any(), any(), any())).thenReturn(chartResult);

            Map<String, Object> args = Map.of(
                    "model", "TestModel",
                    "payload", Map.of("columns", List.of("x"))
            );

            Object result = exportWithChartTool.execute(args, "trace-extract", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) resultMap.get("items");
            assertEquals(1, items.size());
        }
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private boolean hasPercent(ProgressEvent e, int expectedPercent) {
        if (e.getData() instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) e.getData();
            Object percent = data.get("percent");
            return percent != null && ((Number) percent).intValue() == expectedPercent;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean hasErrorCode(ProgressEvent e, String expectedCode) {
        if (e.getData() instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) e.getData();
            return expectedCode.equals(data.get("code"));
        }
        return false;
    }
}
