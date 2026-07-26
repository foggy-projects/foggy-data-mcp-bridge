package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.chart.ChartRendererRegistry;
import com.foggyframework.dataset.mcp.chart.ChartRenderRequest;
import com.foggyframework.dataset.mcp.chart.ChartRenderResult;
import com.foggyframework.dataset.mcp.chart.ChartRenderer;
import com.foggyframework.dataset.mcp.chart.XChartRenderer;
import com.foggyframework.dataset.mcp.storage.ChartStorageAdapter;
import com.foggyframework.dataset.mcp.storage.ChartStorageException;
import com.foggyframework.mcp.spi.ProgressEvent;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ChartTool 单元测试")
class ChartToolTest {

    private ChartTool chartTool;
    private ChartStorageAdapter storageAdapter;
    private ChartRenderer echartsRenderer;

    @BeforeEach
    void setUp() {
        storageAdapter = mock(ChartStorageAdapter.class);
        when(storageAdapter.getType()).thenReturn("mock");
        when(storageAdapter.save(any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation ->
                        "http://mock-storage/charts/chart_"
                                + invocation.getArgument(2)
                                + "."
                                + invocation.getArgument(1));

        echartsRenderer = mock(ChartRenderer.class);
        when(echartsRenderer.getEngine()).thenReturn("echarts");
        when(echartsRenderer.render(any(ChartRenderRequest.class)))
                .thenReturn(new ChartRenderResult(
                        new byte[]{1, 2, 3, 4},
                        "png",
                        1000,
                        600,
                        "bar",
                        "月度销售额"
                ));

        ChartRendererRegistry registry = new ChartRendererRegistry(
                List.of(new XChartRenderer(), echartsRenderer));
        chartTool = new ChartTool(registry, storageAdapter);
    }

    @Nested
    @DisplayName("工具属性")
    class BasicPropertiesTest {

        @Test
        void shouldExposeVisualizationTool() {
            assertEquals("chart.generate", chartTool.getName());
            assertEquals(1, chartTool.getCategories().size());
            assertTrue(chartTool.getCategories().contains(ToolCategory.VISUALIZATION));
            assertTrue(chartTool.supportsStreaming());
        }
    }

    @Nested
    @DisplayName("进程内 XChart 渲染")
    class XChartExecutionTest {

        @Test
        void shouldRenderCategoryChartAndStorePng() {
            Map<String, Object> arguments = Map.of(
                    "data", List.of(
                            Map.of("category", "A", "sales", 120),
                            Map.of("category", "B", "sales", 180)
                    ),
                    "config", Map.of(
                            "chartType", "CategoryChart",
                            "title", "分类销售额",
                            "series", List.of(Map.of(
                                    "name", "销售额",
                                    "xField", "category",
                                    "yField", "sales",
                                    "renderStyle", "Bar"
                            ))
                    ),
                    "image", Map.of(
                            "width", 900,
                            "height", 500,
                            "format", "png"
                    )
            );

            Object result = chartTool.execute(
                    arguments,
                    ToolExecutionContext.of("trace-xchart", null)
            );

            Map<String, Object> resultMap = castMap(result);
            assertEquals(true, resultMap.get("success"));

            Map<String, Object> chart = castMap(resultMap.get("chart"));
            assertEquals("xchart", chart.get("engine"));
            assertEquals("CategoryChart", chart.get("type"));
            assertEquals("分类销售额", chart.get("title"));
            assertEquals("PNG", chart.get("format"));
            assertEquals(900, chart.get("width"));
            assertEquals(500, chart.get("height"));

            ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(storageAdapter).save(
                    bytesCaptor.capture(),
                    org.mockito.ArgumentMatchers.eq("png"),
                    org.mockito.ArgumentMatchers.eq("trace-xchart")
            );
            byte[] image = bytesCaptor.getValue();
            assertTrue(image.length > 100);
            assertArrayEquals(
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                    java.util.Arrays.copyOf(image, 8)
            );
        }

        @Test
        void missingDataShouldBeRejected() {
            Map<String, Object> arguments = Map.of(
                    "config", Map.of(
                            "chartType", "XYChart",
                            "series", List.of(Map.of(
                                    "name", "趋势",
                                    "xField", "x",
                                    "yField", "y",
                                    "renderStyle", "Line"
                            ))
                    )
            );

            assertError(
                    chartTool.execute(
                            arguments,
                            ToolExecutionContext.of("trace-direct", null)
                    ),
                    "data 必须是非空对象数组"
            );
        }

        @Test
        void shouldRejectSvgForXChart() {
            Map<String, Object> arguments = Map.of(
                    "data", List.of(Map.of("category", "A", "amount", 1)),
                    "config", Map.of(
                            "chartType", "PieChart",
                            "nameField", "category",
                            "valueField", "amount"
                    ),
                    "image", Map.of("format", "svg")
            );

            assertError(
                    chartTool.execute(arguments, ToolExecutionContext.of("trace-svg", null)),
                    "仅支持 png/jpg"
            );
        }
    }

    @Nested
    @DisplayName("ECharts 直接数据路由")
    class EChartsExecutionTest {

        @Test
        void shouldRouteNativeOptionAndTopLevelDataToEChartsRenderer() {
            List<Map<String, Object>> data = List.of(
                    Map.of("month", "1月", "amount", 12000),
                    Map.of("month", "2月", "amount", 15000)
            );
            Map<String, Object> config = Map.of(
                    "title", Map.of("text", "月度销售额"),
                    "series", List.of(Map.of(
                            "type", "bar",
                            "encode", Map.of("x", "month", "y", "amount")
                    ))
            );

            Map<String, Object> result = castMap(chartTool.execute(
                    Map.of(
                            "engine", "echarts",
                            "data", data,
                            "config", config,
                            "image", Map.of(
                                    "width", 1000,
                                    "height", 600,
                                    "format", "png"
                            )
                    ),
                    ToolExecutionContext.of("trace-echarts-direct", null)
            ));

            assertEquals(true, result.get("success"));
            Map<String, Object> chart = castMap(result.get("chart"));
            assertEquals("echarts", chart.get("engine"));
            assertEquals("bar", chart.get("type"));

            verify(echartsRenderer).render(
                    org.mockito.ArgumentMatchers.argThat(request ->
                            config.equals(request.config())
                                    && data.equals(request.data())
                                    && "trace-echarts-direct".equals(request.traceId()))
            );
            verify(storageAdapter).save(
                    org.mockito.AdditionalMatchers.aryEq(new byte[]{1, 2, 3, 4}),
                    org.mockito.ArgumentMatchers.eq("png"),
                    org.mockito.ArgumentMatchers.eq("trace-echarts-direct")
            );
        }
    }

    @Nested
    @DisplayName("参数与降级")
    class ValidationAndFallbackTest {

        @Test
        void missingConfigShouldReturnError() {
            assertError(
                    chartTool.execute(
                            Map.of("engine", "xchart"),
                            ToolExecutionContext.of("trace-no-config", null)
                    ),
                    "config 必须是非空对象"
            );
        }

        @Test
        void unsupportedEngineShouldListAvailableEngines() {
            assertError(
                    chartTool.execute(
                            Map.of(
                                    "engine", "unknown",
                                    "data", List.of(Map.of("category", "A", "amount", 1)),
                                    "config", Map.of(
                                            "chartType", "PieChart",
                                            "nameField", "category",
                                            "valueField", "amount"
                                    )
                            ),
                            ToolExecutionContext.of("trace-engine", null)
                    ),
                    "可用引擎"
            );
        }

        @Test
        void storageFailureShouldFallBackToBase64() {
            when(storageAdapter.save(any(byte[].class), anyString(), anyString()))
                    .thenThrow(new ChartStorageException("storage unavailable"));

            Map<String, Object> result = castMap(chartTool.execute(
                    Map.of(
                            "data", List.of(
                                    Map.of("category", "A", "amount", 10),
                                    Map.of("category", "B", "amount", 20)
                            ),
                            "config", Map.of(
                                    "chartType", "PieChart",
                                    "nameField", "category",
                                    "valueField", "amount"
                            )
                    ),
                    ToolExecutionContext.of("trace-base64", null)
            ));

            assertEquals(true, result.get("success"));
            Map<String, Object> chart = castMap(result.get("chart"));
            assertTrue(chart.get("url").toString().startsWith("data:image/png;base64,"));
        }
    }

    @Test
    void shouldEmitProgressEvents() {
        Map<String, Object> arguments = Map.of(
                "data", List.of(Map.of("category", "A", "amount", 1)),
                "config", Map.of(
                        "chartType", "PieChart",
                        "nameField", "category",
                        "valueField", "amount"
                )
        );

        Flux<ProgressEvent> flux = chartTool.executeWithProgress(
                arguments,
                ToolExecutionContext.of("trace-progress", null)
        );

        StepVerifier.create(flux)
                .expectNextMatches(event -> isProgress(event, 10))
                .expectNextMatches(event -> isProgress(event, 50))
                .expectNextMatches(event -> isProgress(event, 80))
                .expectNextMatches(event -> "complete".equals(event.getEventType()))
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        assertNotNull(value);
        assertTrue(value instanceof Map<?, ?>);
        return (Map<String, Object>) value;
    }

    private void assertError(Object result, String messagePart) {
        Map<String, Object> error = castMap(result);
        assertEquals(false, error.get("success"));
        assertEquals(true, error.get("error"));
        assertTrue(error.get("message").toString().contains(messagePart));
    }

    @SuppressWarnings("unchecked")
    private boolean isProgress(ProgressEvent event, int expectedPercent) {
        if (!"progress".equals(event.getEventType()) || !(event.getData() instanceof Map<?, ?>)) {
            return false;
        }
        return ((Number) ((Map<String, Object>) event.getData()).get("percent")).intValue()
                == expectedPercent;
    }
}
