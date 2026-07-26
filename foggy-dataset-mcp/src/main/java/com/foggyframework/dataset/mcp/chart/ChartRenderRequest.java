package com.foggyframework.dataset.mcp.chart;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request passed to a concrete chart renderer.
 *
 * @param config renderer-native configuration (XChart config or ECharts Option)
 * @param data tabular data supplied by chart.generate or a fixed-engine export tool
 */
public record ChartRenderRequest(
        Map<String, Object> config,
        List<Map<String, Object>> data,
        ChartImageSpec image,
        String traceId
) {

    public ChartRenderRequest {
        config = config == null ? Map.of() : new LinkedHashMap<>(config);
        data = data == null ? List.of() : List.copyOf(data);
        image = Objects.requireNonNull(image, "image");
    }
}
