package com.foggyframework.dataset.mcp.chart;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves chart renderers by their engine name.
 */
@Component
public class ChartRendererRegistry {

    private final Map<String, ChartRenderer> renderers;

    public ChartRendererRegistry(List<ChartRenderer> rendererList) {
        Map<String, ChartRenderer> resolved = new LinkedHashMap<>();
        for (ChartRenderer renderer : rendererList) {
            String engine = normalize(renderer.getEngine());
            ChartRenderer previous = resolved.putIfAbsent(engine, renderer);
            if (previous != null) {
                throw new IllegalStateException("重复的图表渲染器: " + engine);
            }
        }
        this.renderers = Map.copyOf(resolved);
    }

    public ChartRenderer require(String engine) {
        String normalized = normalize(engine);
        ChartRenderer renderer = renderers.get(normalized);
        if (renderer == null) {
            throw new IllegalArgumentException(
                    "不支持的图表引擎: " + engine + "，可用引擎: " + availableEngines());
        }
        return renderer;
    }

    public Set<String> availableEngines() {
        return renderers.keySet();
    }

    private static String normalize(String engine) {
        if (engine == null || engine.isBlank()) {
            return "xchart";
        }
        return engine.trim().toLowerCase(Locale.ROOT);
    }
}
