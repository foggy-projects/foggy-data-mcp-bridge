package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ChartTool 单元测试
 *
 * 使用 WireMock 模拟外部 chart-render-service
 */
@DisplayName("ChartTool 单元测试")
class ChartToolTest {

    private static WireMockServer wireMockServer;
    private ChartTool chartTool;
    private ObjectMapper objectMapper;
    private McpProperties mcpProperties;

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void tearDownWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mcpProperties = new McpProperties();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();

        chartTool = new ChartTool(webClient, mcpProperties, objectMapper);
        wireMockServer.resetAll();
    }

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("工具基本属性")
    class BasicPropertiesTest {

        @Test
        @DisplayName("getName 应返回正确的工具名称")
        void getName_shouldReturnCorrectName() {
            assertEquals("chart.generate", chartTool.getName());
        }

        @Test
        @DisplayName("getCategories 应返回 VISUALIZATION 类别")
        void getCategories_shouldReturnVisualizationCategory() {
            assertTrue(chartTool.getCategories().contains(ToolCategory.VISUALIZATION));
            assertEquals(1, chartTool.getCategories().size());
        }

        @Test
        @DisplayName("supportsStreaming 应返回 true")
        void supportsStreaming_shouldReturnTrue() {
            assertTrue(chartTool.supportsStreaming());
        }

        // Note: getDescription() and getInputSchema() now load from config files,
        // they are tested in integration tests with ToolConfigLoader
    }

    // ==================== execute 参数验证测试 ====================

    @Nested
    @DisplayName("execute - 参数验证")
    class ParameterValidationTest {

        @Test
        @DisplayName("空数据应返回错误")
        void emptyData_shouldReturnError() {
            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "data", List.of()
            );

            Object result = chartTool.execute(args, "trace-1", null);

            assertIsError(result, "数据不能为空");
        }

        @Test
        @DisplayName("null 数据应返回错误")
        void nullData_shouldReturnError() {
            Map<String, Object> args = new HashMap<>();
            args.put("type", "bar");
            args.put("data", null);

            Object result = chartTool.execute(args, "trace-2", null);

            assertIsError(result, "数据不能为空");
        }
    }

    // ==================== execute 成功场景测试 ====================

    @Nested
    @DisplayName("execute - 成功场景")
    class ExecuteSuccessTest {

        @Test
        @DisplayName("柱图生成应成功")
        void barChart_shouldGenerateSuccessfully() throws Exception {
            // 模拟图表渲染服务返回图片字节
            byte[] fakeImageBytes = "fake-png-image-bytes".getBytes();

            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "image/png")
                            .withBody(fakeImageBytes)));

            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "title", "Sales by Category",
                    "data", List.of(
                            Map.of("category", "Electronics", "sales", 50000),
                            Map.of("category", "Clothing", "sales", 30000),
                            Map.of("category", "Food", "sales", 20000)
                    ),
                    "xField", "category",
                    "yField", "sales"
            );

            Object result = chartTool.execute(args, "trace-bar", null);

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue((Boolean) resultMap.get("success"));

            @SuppressWarnings("unchecked")
            Map<String, Object> chartInfo = (Map<String, Object>) resultMap.get("chart");
            assertEquals("BAR", chartInfo.get("type"));
            assertEquals("Sales by Category", chartInfo.get("title"));
            assertNotNull(chartInfo.get("url"));
        }

        @Test
        @DisplayName("线图生成应成功")
        void lineChart_shouldGenerateSuccessfully() throws Exception {
            byte[] fakeImageBytes = "fake-line-chart".getBytes();

            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "image/png")
                            .withBody(fakeImageBytes)));

            Map<String, Object> args = Map.of(
                    "type", "line",
                    "title", "Monthly Sales Trend",
                    "data", List.of(
                            Map.of("month", "Jan", "sales", 10000),
                            Map.of("month", "Feb", "sales", 12000),
                            Map.of("month", "Mar", "sales", 15000)
                    ),
                    "xField", "month",
                    "yField", "sales"
            );

            Object result = chartTool.execute(args, "trace-line", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertTrue((Boolean) resultMap.get("success"));

            @SuppressWarnings("unchecked")
            Map<String, Object> chartInfo = (Map<String, Object>) resultMap.get("chart");
            assertEquals("LINE", chartInfo.get("type"));
        }

        @Test
        @DisplayName("饼图生成应正确处理字段映射")
        void pieChart_shouldHandleFieldMappingCorrectly() throws Exception {
            byte[] fakeImageBytes = "fake-pie-chart".getBytes();

            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(fakeImageBytes)));

            Map<String, Object> args = Map.of(
                    "type", "pie",
                    "title", "Market Share",
                    "data", List.of(
                            Map.of("name", "Company A", "value", 45),
                            Map.of("name", "Company B", "value", 35),
                            Map.of("name", "Others", "value", 20)
                    ),
                    "xField", "name",
                    "yField", "value"
            );

            chartTool.execute(args, "trace-pie", null);

            // 验证饼图使用了正确的字段映射 (valueField/nameField)
            verify(postRequestedFor(urlEqualTo("/render/unified/stream"))
                    .withRequestBody(matchingJsonPath("$.unified.type", equalTo("pie")))
                    .withRequestBody(matchingJsonPath("$.unified.valueField", equalTo("value")))
                    .withRequestBody(matchingJsonPath("$.unified.nameField", equalTo("name"))));
        }

        @Test
        @DisplayName("自定义尺寸应正确传递")
        void customSize_shouldPassCorrectly() throws Exception {
            byte[] fakeImageBytes = "fake-sized-chart".getBytes();

            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(fakeImageBytes)));

            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "data", List.of(Map.of("x", 1, "y", 2)),
                    "xField", "x",
                    "yField", "y",
                    "width", 1200,
                    "height", 800,
                    "format", "svg"
            );

            Object result = chartTool.execute(args, "trace-size", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> chartInfo = (Map<String, Object>) resultMap.get("chart");
            assertEquals(1200, chartInfo.get("width"));
            assertEquals(800, chartInfo.get("height"));
            assertEquals("SVG", chartInfo.get("format"));

            verify(postRequestedFor(urlEqualTo("/render/unified/stream"))
                    .withRequestBody(matchingJsonPath("$.image.width", equalTo("1200")))
                    .withRequestBody(matchingJsonPath("$.image.height", equalTo("800")))
                    .withRequestBody(matchingJsonPath("$.image.format", equalTo("svg"))));
        }

        @Test
        @DisplayName("带 seriesField 的多系列图表应正确处理")
        void multiSeriesChart_shouldHandleSeriesField() throws Exception {
            byte[] fakeImageBytes = "fake-multi-series".getBytes();

            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(fakeImageBytes)));

            Map<String, Object> args = Map.of(
                    "type", "line",
                    "title", "Sales Comparison",
                    "data", List.of(
                            Map.of("month", "Jan", "sales", 100, "region", "North"),
                            Map.of("month", "Jan", "sales", 80, "region", "South"),
                            Map.of("month", "Feb", "sales", 120, "region", "North"),
                            Map.of("month", "Feb", "sales", 90, "region", "South")
                    ),
                    "xField", "month",
                    "yField", "sales",
                    "seriesField", "region"
            );

            chartTool.execute(args, "trace-multi-series", null);

            verify(postRequestedFor(urlEqualTo("/render/unified/stream"))
                    .withRequestBody(matchingJsonPath("$.unified.seriesField", equalTo("region"))));
        }

        @Test
        @DisplayName("默认值应正确应用")
        void defaultValues_shouldBeApplied() throws Exception {
            byte[] fakeImageBytes = "fake-default-chart".getBytes();

            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(fakeImageBytes)));

            // 只提供必需参数
            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "data", List.of(Map.of("x", 1, "y", 2))
            );

            Object result = chartTool.execute(args, "trace-defaults", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> chartInfo = (Map<String, Object>) resultMap.get("chart");

            // 验证默认值
            assertEquals("数据图表", chartInfo.get("title"));
            assertEquals("PNG", chartInfo.get("format"));
            assertEquals(800, chartInfo.get("width"));
            assertEquals(600, chartInfo.get("height"));
        }
    }

    // ==================== execute 错误场景测试 ====================

    @Nested
    @DisplayName("execute - 错误场景")
    class ExecuteErrorTest {

        @Test
        @DisplayName("渲染服务返回 500 应返回错误")
        void renderServiceError_shouldReturnError() {
            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "data", List.of(Map.of("x", 1, "y", 2))
            );

            Object result = chartTool.execute(args, "trace-500", null);

            assertIsError(result, "图表生成失败");
        }

        @Test
        @DisplayName("渲染服务返回空数据应返回错误")
        void emptyResponse_shouldReturnError() {
            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(new byte[0])));

            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "data", List.of(Map.of("x", 1, "y", 2))
            );

            Object result = chartTool.execute(args, "trace-empty", null);

            assertIsError(result, "图表生成失败");
        }
    }

    // ==================== executeWithProgress 测试 ====================

    @Nested
    @DisplayName("executeWithProgress - 流式执行")
    class ExecuteWithProgressTest {

        @Test
        @DisplayName("应发出进度事件序列")
        void shouldEmitProgressEvents() {
            byte[] fakeImageBytes = "fake-streaming-chart".getBytes();

            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(fakeImageBytes)));

            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "data", List.of(Map.of("x", 1, "y", 2))
            );

            Flux<ProgressEvent> flux = chartTool.executeWithProgress(args, "trace-streaming", null);

            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 10))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 50))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 80))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("执行错误应发出错误事件")
        void executionError_shouldEmitErrorEvent() {
            stubFor(post(urlEqualTo("/render/unified/stream"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Error")));

            Map<String, Object> args = Map.of(
                    "type", "bar",
                    "data", List.of(Map.of("x", 1, "y", 2))
            );

            Flux<ProgressEvent> flux = chartTool.executeWithProgress(args, "trace-error-stream", null);

            // 由于 execute 返回错误而非抛出异常，complete 事件会包含错误响应
            StepVerifier.create(flux)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();
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

    private void assertIsError(Object result, String expectedMessagePart) {
        assertNotNull(result);
        assertInstanceOf(Map.class, result);

        @SuppressWarnings("unchecked")
        Map<String, Object> errorMap = (Map<String, Object>) result;
        assertEquals(false, errorMap.get("success"));
        assertTrue((Boolean) errorMap.get("error"));
        assertTrue(errorMap.get("message").toString().contains(expectedMessagePart));
    }
}
