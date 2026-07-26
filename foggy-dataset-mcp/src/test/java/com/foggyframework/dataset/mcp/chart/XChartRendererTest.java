package com.foggyframework.dataset.mcp.chart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("XChartRenderer 单元测试")
class XChartRendererTest {

    private final XChartRenderer renderer = new XChartRenderer();

    @Test
    void shouldRenderGroupedCategorySeries() {
        ChartRenderResult result = renderer.render(new ChartRenderRequest(
                Map.of(
                        "chartType", "CategoryChart",
                        "title", "年度对比",
                        "series", List.of(Map.of(
                                "xField", "month",
                                "yField", "sales",
                                "seriesField", "year",
                                "renderStyle", "Line",
                                "smooth", true
                        ))
                ),
                List.of(
                        Map.of("year", "2025", "month", "1月", "sales", 100),
                        Map.of("year", "2025", "month", "2月", "sales", 120),
                        Map.of("year", "2026", "month", "1月", "sales", 130),
                        Map.of("year", "2026", "month", "2月", "sales", 150)
                ),
                new ChartImageSpec(800, 600, "png"),
                "trace"
        ));

        assertEquals("CategoryChart", result.chartType());
        assertTrue(result.bytes().length > 100);
    }

    @Test
    void shouldRenderPieFromBoundDataAndAggregateNames() {
        ChartRenderResult result = renderer.render(new ChartRenderRequest(
                Map.of(
                        "chartType", "PieChart",
                        "nameField", "category",
                        "valueField", "amount",
                        "renderStyle", "Donut",
                        "styler", Map.of(
                                "labelsVisible", true,
                                "labelType", "NameAndPercentage"
                        )
                ),
                List.of(
                        Map.of("category", "A", "amount", 10),
                        Map.of("category", "A", "amount", 5),
                        Map.of("category", "B", "amount", 20)
                ),
                new ChartImageSpec(600, 400, "png"),
                "trace"
        ));

        assertEquals("PieChart", result.chartType());
        assertTrue(result.bytes().length > 100);
    }

    @Test
    void shouldEncodeJpg() {
        ChartRenderResult result = renderer.render(new ChartRenderRequest(
                Map.of(
                        "chartType", "PieChart",
                        "nameField", "category",
                        "valueField", "amount"
                ),
                List.of(
                        Map.of("category", "A", "amount", 1),
                        Map.of("category", "B", "amount", 2)
                ),
                new ChartImageSpec(400, 300, "jpeg"),
                "trace"
        ));

        assertEquals("jpg", result.format());
        assertEquals((byte) 0xFF, result.bytes()[0]);
        assertEquals((byte) 0xD8, result.bytes()[1]);
    }

    @Test
    void xyChartShouldRejectCategoricalStrings() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        Map.of(
                                "chartType", "XYChart",
                                "series", List.of(Map.of(
                                        "name", "错误示例",
                                        "xField", "month",
                                        "yField", "sales"
                                ))
                        ),
                        List.of(
                                Map.of("month", "1月", "sales", 10),
                                Map.of("month", "2月", "sales", 20)
                        ),
                        new ChartImageSpec(800, 600, "png"),
                        "trace"
                ))
        );

        assertTrue(error.getMessage().contains("CategoryChart"));
    }

    @Test
    void shouldRejectEmbeddedSeriesArrays() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        Map.of(
                                "chartType", "CategoryChart",
                                "series", List.of(Map.of(
                                        "xField", "month",
                                        "yField", "sales",
                                        "xData", List.of("1月"),
                                        "yData", List.of(10)
                                ))
                        ),
                        List.of(Map.of("month", "1月", "sales", 10)),
                        new ChartImageSpec(800, 600, "png"),
                        "trace"
                ))
        );

        assertTrue(error.getMessage().contains("不支持的字段"));
        assertTrue(error.getMessage().contains("xData"));
    }

    @Test
    void shouldRenderNullYAsGap() {
        Map<String, Object> nullRow = new java.util.LinkedHashMap<>();
        nullRow.put("month", "2月");
        nullRow.put("sales", null);

        ChartRenderResult result = renderer.render(new ChartRenderRequest(
                Map.of(
                        "chartType", "CategoryChart",
                        "series", List.of(Map.of(
                                "xField", "month",
                                "yField", "sales",
                                "renderStyle", "Line"
                        ))
                ),
                List.of(
                        Map.of("month", "1月", "sales", 10),
                        nullRow,
                        Map.of("month", "3月", "sales", 30)
                ),
                new ChartImageSpec(800, 600, "png"),
                "trace"
        ));

        assertTrue(result.bytes().length > 100);
    }

    @Test
    void shouldRejectUnknownConfigStylerAndSeriesFields() {
        assertUnsupportedField(Map.of(
                "chartType", "CategoryChart",
                "unknownBuilder", true,
                "series", validSeries()
        ), "unknownBuilder");

        assertUnsupportedField(Map.of(
                "chartType", "CategoryChart",
                "styler", Map.of("unknownStyler", true),
                "series", validSeries()
        ), "unknownStyler");

        assertUnsupportedField(Map.of(
                "chartType", "CategoryChart",
                "series", List.of(Map.of(
                        "xField", "month",
                        "yField", "sales",
                        "unknownSeries", true
                ))
        ), "unknownSeries");
    }

    @Test
    void pieShouldRejectSeriesValueData() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        Map.of(
                                "chartType", "PieChart",
                                "series", List.of(Map.of("name", "A", "value", 1))
                        ),
                        List.of(Map.of("category", "A", "amount", 1)),
                        new ChartImageSpec(800, 600, "png"),
                        "trace"
                ))
        );

        assertTrue(error.getMessage().contains("不支持的字段"));
        assertTrue(error.getMessage().contains("series"));
    }

    private List<Map<String, Object>> validSeries() {
        return List.of(Map.of(
                "xField", "month",
                "yField", "sales"
        ));
    }

    private void assertUnsupportedField(Map<String, Object> config, String field) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        config,
                        List.of(Map.of("month", "1月", "sales", 10)),
                        new ChartImageSpec(800, 600, "png"),
                        "trace"
                ))
        );
        assertTrue(error.getMessage().contains(field));
    }
}
