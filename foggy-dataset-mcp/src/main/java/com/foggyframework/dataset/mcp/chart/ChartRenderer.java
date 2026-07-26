package com.foggyframework.dataset.mcp.chart;

/**
 * Adapter contract for renderer-native chart implementations.
 */
public interface ChartRenderer {

    /**
     * Stable engine name used by MCP requests, for example {@code xchart} or {@code echarts}.
     */
    String getEngine();

    /**
     * Render the engine-native configuration to image bytes.
     */
    ChartRenderResult render(ChartRenderRequest request);
}
