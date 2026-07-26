package com.foggyframework.dataset.mcp.chart;

import java.util.Objects;

/**
 * Binary result returned by a chart renderer.
 */
public record ChartRenderResult(
        byte[] bytes,
        String format,
        int width,
        int height,
        String chartType,
        String title
) {

    public ChartRenderResult {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("图表渲染结果为空");
        }
        Objects.requireNonNull(format, "format");
        chartType = chartType == null || chartType.isBlank() ? "chart" : chartType;
        title = title == null ? "" : title;
    }
}
