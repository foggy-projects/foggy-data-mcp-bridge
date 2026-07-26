package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.mcp.chart.ChartRendererRegistry;
import com.foggyframework.dataset.mcp.chart.XChartRenderer;
import com.foggyframework.dataset.mcp.storage.ChartStorageAdapter;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.mcp.spi.ProgressEvent;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("固定引擎图表导出工具单元测试")
class ExportWithChartToolsTest {

    @Mock
    private QueryModelTool queryModelTool;

    @Mock
    private ChartTool chartTool;

    private ExportWithXChartTool xchartTool;
    private ExportWithEChartsTool echartsTool;

    @BeforeEach
    void setUp() {
        ExportWithChartExecutor executor =
                new ExportWithChartExecutor(queryModelTool, chartTool);
        xchartTool = new ExportWithXChartTool(executor);
        echartsTool = new ExportWithEChartsTool(executor);
    }

    @Test
    void shouldExposeTwoIndependentTools() {
        assertEquals("dataset.export_with_xchart", xchartTool.getName());
        assertEquals("dataset.export_with_echarts", echartsTool.getName());
        assertTrue(xchartTool.supportsStreaming());
        assertTrue(echartsTool.supportsStreaming());
        assertTrue(xchartTool.getCategories().contains(ToolCategory.QUERY));
        assertTrue(xchartTool.getCategories().contains(ToolCategory.VISUALIZATION));
        assertTrue(xchartTool.getCategories().contains(ToolCategory.EXPORT));
    }

    @Test
    void xchartToolShouldForceXChartEngine() {
        stubQuery(List.of(Map.of("month", "1月", "sales", 100)));
        when(chartTool.execute(any(), any())).thenReturn(successfulChart("xchart"));

        Map<String, Object> chart = new LinkedHashMap<>(xchartChart());
        chart.put("engine", "echarts");
        xchartTool.execute(
                Map.of("model", "SalesModel", "payload", Map.of(), "chart", chart),
                ToolExecutionContext.of("trace-xchart", null)
        );

        verify(chartTool).execute(
                org.mockito.ArgumentMatchers.argThat(arguments ->
                        "xchart".equals(arguments.get("engine"))
                                && arguments.containsKey("data")),
                any()
        );
    }

    @Test
    void echartsToolShouldForceEChartsEngine() {
        stubQuery(List.of(Map.of("month", "1月", "sales", 100)));
        when(chartTool.execute(any(), any())).thenReturn(successfulChart("echarts"));

        Map<String, Object> chart = new LinkedHashMap<>(echartsChart());
        chart.put("engine", "xchart");
        echartsTool.execute(
                Map.of("model", "SalesModel", "payload", Map.of(), "chart", chart),
                ToolExecutionContext.of("trace-echarts", null)
        );

        verify(chartTool).execute(
                org.mockito.ArgumentMatchers.argThat(arguments ->
                        "echarts".equals(arguments.get("engine"))
                                && arguments.containsKey("data")),
                any()
        );
    }

    @Test
    void pivotShouldUseCopiedFlatPayloadAndFilterTotals() {
        Map<String, Object> pivot = new LinkedHashMap<>();
        pivot.put("rows", List.of("region"));
        pivot.put("metrics", List.of("sales"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pivot", pivot);

        Map<String, Object> totalMeta = Map.of("isGrandTotal", true);
        List<Map<String, Object>> items = List.of(
                Map.of("region", "华东", "sales", 100),
                Map.of("region", "总计", "sales", 100, "_sys_meta", totalMeta)
        );
        stubQuery(items);
        when(chartTool.execute(any(), any())).thenReturn(successfulChart("xchart"));

        Map<String, Object> response = castMap(xchartTool.execute(
                Map.of("model", "SalesModel", "payload", payload, "chart", xchartChart()),
                ToolExecutionContext.of("trace-pivot", null)
        ));

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryModelTool).executeQuery(
                anyString(),
                payloadCaptor.capture(),
                anyString(),
                anyString(),
                any()
        );
        Map<String, Object> submittedPivot =
                castMap(payloadCaptor.getValue().get("pivot"));
        assertEquals("flat", submittedPivot.get("outputFormat"));
        assertFalse(pivot.containsKey("outputFormat"));

        verify(chartTool).execute(
                org.mockito.ArgumentMatchers.argThat(arguments ->
                        List.of(Map.of("region", "华东", "sales", 100))
                                .equals(arguments.get("data"))),
                any()
        );
        assertEquals(1, ((List<?>) response.get("items")).size());
    }

    @Test
    void pivotTreeOrGridShouldFailBeforeQuery() {
        Object gridResult = xchartTool.execute(
                Map.of(
                        "model", "SalesModel",
                        "payload", Map.of("pivot", Map.of(
                                "rows", List.of("region"),
                                "metrics", List.of("sales"),
                                "outputFormat", "grid"
                        )),
                        "chart", xchartChart()
                ),
                ToolExecutionContext.of("trace-grid", null)
        );
        assertTrue(((RX<?>) gridResult).getMsg().contains("outputFormat=flat"));

        Object treeResult = echartsTool.execute(
                Map.of(
                        "model", "SalesModel",
                        "payload", Map.of("pivot", Map.of(
                                "rows", List.of(Map.of(
                                        "field", "region",
                                        "hierarchyMode", "tree"
                                )),
                                "metrics", List.of("sales")
                        )),
                        "chart", echartsChart()
                ),
                ToolExecutionContext.of("trace-tree", null)
        );
        assertTrue(((RX<?>) treeResult).getMsg().contains("hierarchyMode=tree"));
        verify(queryModelTool, never()).executeQuery(
                anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void dslCteAndTimeWindowFinalItemsShouldPassThroughUnchanged() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("month", "1月");
        row.put("cteTotal", 100);
        row.put("cteTotal__prior", null);
        stubQuery(List.of(row));
        when(chartTool.execute(any(), any())).thenReturn(successfulChart("xchart"));

        Map<String, Object> payload = Map.of(
                "route", "DSL_CTE",
                "executable_plan", Map.of(
                        "cte_plan", Map.of(
                                "output", List.of("month", "cteTotal", "cteTotal__prior")
                        )
                )
        );
        xchartTool.execute(
                Map.of("model", "SalesModel", "payload", payload, "chart", xchartChart()),
                ToolExecutionContext.of("trace-cte", null)
        );

        verify(chartTool).execute(
                org.mockito.ArgumentMatchers.argThat(arguments ->
                        List.of(row).equals(arguments.get("data"))),
                any()
        );
    }

    @Test
    void queryFailureAndEmptyResultShouldSkipRendering() {
        when(queryModelTool.executeQuery(
                anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(RX.failB("Model not found"));

        Object failed = xchartTool.execute(
                validXChartArguments(),
                ToolExecutionContext.of("trace-query-error", null)
        );
        assertTrue(failed instanceof RX<?>);
        assertNotEquals(200, ((RX<?>) failed).getCode());
        verify(chartTool, never()).execute(any(), any());

        org.mockito.Mockito.reset(queryModelTool, chartTool);
        stubQuery(List.of());
        Map<String, Object> empty = castMap(echartsTool.execute(
                Map.of(
                        "model", "SalesModel",
                        "payload", Map.of(),
                        "chart", echartsChart()
                ),
                ToolExecutionContext.of("trace-empty", null)
        ));
        assertTrue(empty.get("summary").toString().contains("查询结果为空"));
        verify(chartTool, never()).execute(any(), any());
    }

    @Test
    void chartFailureShouldKeepQueryItems() {
        stubQuery(List.of(Map.of("category", "A", "sales", 10)));
        when(chartTool.execute(any(), any())).thenReturn(Map.of(
                "success", false,
                "error", true,
                "message", "invalid config"
        ));

        Map<String, Object> response = castMap(xchartTool.execute(
                validXChartArguments(),
                ToolExecutionContext.of("trace-chart-error", null)
        ));

        assertEquals("查询完成，但图表生成失败", response.get("summary"));
        assertEquals(
                "invalid config",
                castMap(response.get("exports")).get("chartError")
        );
    }

    @Test
    void streamingShouldQueryOnlyOnce() {
        stubQuery(List.of(Map.of("category", "A", "sales", 10)));
        when(chartTool.execute(any(), any())).thenReturn(successfulChart("xchart"));

        Flux<ProgressEvent> flux = xchartTool.executeWithProgress(
                validXChartArguments(),
                ToolExecutionContext.of("trace-stream", null)
        );

        StepVerifier.create(flux)
                .expectNextMatches(event -> isProgress(event, 20))
                .expectNextMatches(event -> "partial_result".equals(event.getEventType()))
                .expectNextMatches(event -> isProgress(event, 60))
                .expectNextMatches(event -> isProgress(event, 90))
                .expectNextMatches(event -> "complete".equals(event.getEventType()))
                .verifyComplete();
        verify(queryModelTool, times(1)).executeQuery(
                anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void categoryValidationShouldNotExposeQueryValueInChartErrorOrLogs(CapturedOutput output) {
        String sensitiveValue = "SECRET-CATEGORY-AC10";
        assertXChartValidationIsSanitized(
                Map.of("category", "A", "sales", sensitiveValue),
                Map.of(
                        "chartType", "CategoryChart",
                        "series", List.of(Map.of(
                                "xField", "category",
                                "yField", "sales"
                        ))
                ),
                sensitiveValue,
                "字段 sales 必须是数值或 null",
                output
        );
    }

    @Test
    void xyValidationShouldNotExposeQueryValueInChartErrorOrLogs(CapturedOutput output) {
        String sensitiveValue = "SECRET-XY-AC10";
        assertXChartValidationIsSanitized(
                Map.of("x", sensitiveValue, "sales", 10),
                Map.of(
                        "chartType", "XYChart",
                        "series", List.of(Map.of(
                                "xField", "x",
                                "yField", "sales"
                        ))
                ),
                sensitiveValue,
                "XYChart 的 xData/xField 必须是数值或日期",
                output
        );
    }

    @Test
    void pieValidationShouldNotExposeQueryValueInChartErrorOrLogs(CapturedOutput output) {
        String sensitiveValue = "SECRET-PIE-AC10";
        assertXChartValidationIsSanitized(
                Map.of("category", "A", "amount", sensitiveValue),
                Map.of(
                        "chartType", "PieChart",
                        "nameField", "category",
                        "valueField", "amount"
                ),
                sensitiveValue,
                "字段 amount 必须是数值",
                output
        );
    }

    private void assertXChartValidationIsSanitized(
            Map<String, Object> queryRow,
            Map<String, Object> config,
            String sensitiveValue,
            String expectedError,
            CapturedOutput output
    ) {
        stubQuery(List.of(queryRow));
        ChartTool realChartTool = new ChartTool(
                new ChartRendererRegistry(List.of(new XChartRenderer())),
                mock(ChartStorageAdapter.class)
        );
        ExportWithXChartTool realTool = new ExportWithXChartTool(
                new ExportWithChartExecutor(queryModelTool, realChartTool)
        );

        Map<String, Object> response = castMap(realTool.execute(
                Map.of(
                        "model", "SalesModel",
                        "payload", Map.of(),
                        "chart", Map.of("config", config)
                ),
                ToolExecutionContext.of("trace-log-sanitization", "Bearer caller-token")
        ));

        String chartError = String.valueOf(
                castMap(response.get("exports")).get("chartError"));
        assertTrue(chartError.contains(expectedError));
        assertFalse(chartError.contains(sensitiveValue));
        assertFalse(output.getAll().contains(sensitiveValue));
        assertFalse(output.getAll().contains("Bearer caller-token"));
    }

    private void stubQuery(List<Map<String, Object>> items) {
        when(queryModelTool.executeQuery(
                anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(RX.success(queryResponse(items, items.size())));
    }

    private Map<String, Object> validXChartArguments() {
        return Map.of(
                "model", "SalesModel",
                "payload", Map.of("columns", List.of("category", "sales")),
                "chart", xchartChart()
        );
    }

    private Map<String, Object> xchartChart() {
        return Map.of(
                "config", Map.of(
                        "chartType", "CategoryChart",
                        "series", List.of(Map.of(
                                "name", "销售额",
                                "xField", "category",
                                "yField", "sales"
                        ))
                )
        );
    }

    private Map<String, Object> echartsChart() {
        return Map.of(
                "config", Map.of(
                        "xAxis", Map.of("type", "category"),
                        "yAxis", Map.of("type", "value"),
                        "series", List.of(Map.of(
                                "type", "bar",
                                "encode", Map.of("x", "category", "y", "sales")
                        ))
                )
        );
    }

    private SemanticQueryResponse queryResponse(
            List<Map<String, Object>> items,
            long total
    ) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(items);
        response.setTotal(total);
        return response;
    }

    private Map<String, Object> successfulChart(String engine) {
        return Map.of(
                "success", true,
                "chart", Map.of(
                        "url", "http://localhost/charts/test.png",
                        "engine", engine,
                        "type", "chart"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        assertTrue(value instanceof Map<?, ?>);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private boolean isProgress(ProgressEvent event, int expectedPercent) {
        if (!"progress".equals(event.getEventType())
                || !(event.getData() instanceof Map<?, ?>)) {
            return false;
        }
        return ((Number) ((Map<String, Object>) event.getData()).get("percent")).intValue()
                == expectedPercent;
    }
}
