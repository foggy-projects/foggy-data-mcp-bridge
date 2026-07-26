package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.chart.ChartImageSpec;
import com.foggyframework.dataset.mcp.chart.ChartRenderRequest;
import com.foggyframework.dataset.mcp.chart.ChartRenderResult;
import com.foggyframework.dataset.mcp.chart.ChartRenderer;
import com.foggyframework.dataset.mcp.chart.ChartRendererRegistry;
import com.foggyframework.dataset.mcp.storage.ChartStorageAdapter;
import com.foggyframework.dataset.mcp.storage.ChartStorageException;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ProgressEvent;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chart generation tool.
 *
 * <p>The tool only selects a renderer and persists its binary result. It does
 * not translate chart semantics between engines:
 * <ul>
 *   <li>XChart requests use the XChart-specific builder/styler/series JSON.</li>
 *   <li>ECharts requests use a native ECharts Option.</li>
 * </ul>
 */
@Slf4j
@Component
public class ChartTool implements McpTool {

    private static final String DEFAULT_ENGINE = "xchart";

    private final ChartRendererRegistry rendererRegistry;
    private final ChartStorageAdapter storageAdapter;

    public ChartTool(
            ChartRendererRegistry rendererRegistry,
            ChartStorageAdapter storageAdapter
    ) {
        this.rendererRegistry = rendererRegistry;
        this.storageAdapter = storageAdapter;
    }

    @Override
    public String getName() {
        return "chart.generate";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.VISUALIZATION);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String traceId = context.getTraceId();
        String engine = stringValue(arguments.get("engine"), DEFAULT_ENGINE);

        try {
            Map<String, Object> config = requireObject(arguments.get("config"), "config");
            List<Map<String, Object>> data = requireData(arguments.get("data"));
            ChartImageSpec image = ChartImageSpec.from(arguments.get("image"));

            ChartRenderer renderer = rendererRegistry.require(engine);
            log.info(
                    "Generating chart: engine={}, dataSize={}, image={}x{}.{}, traceId={}",
                    renderer.getEngine(),
                    data.size(),
                    image.width(),
                    image.height(),
                    image.format(),
                    traceId
            );

            ChartRenderResult renderResult = renderer.render(new ChartRenderRequest(
                    config,
                    data,
                    image,
                    traceId
            ));

            String imageUrl = saveChartImage(
                    renderResult.bytes(), renderResult.format(), traceId);

            Map<String, Object> chart = new LinkedHashMap<>();
            chart.put("url", imageUrl);
            chart.put("engine", renderer.getEngine());
            chart.put("type", renderResult.chartType());
            chart.put("title", renderResult.title());
            chart.put("format", renderResult.format().toUpperCase());
            chart.put("width", renderResult.width());
            chart.put("height", renderResult.height());
            chart.put("fileSize", renderResult.bytes().length);

            log.info(
                    "Chart generated successfully: engine={}, url={}, size={}KB, traceId={}",
                    renderer.getEngine(),
                    safeUrlForLog(imageUrl),
                    renderResult.bytes().length / 1024,
                    traceId
            );

            return Map.of(
                    "success", true,
                    "chart", chart
            );
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                log.warn(
                        "Chart request rejected: engine={}, message={}, traceId={}",
                        engine,
                        e.getMessage(),
                        traceId
                );
            } else {
                log.error(
                        "Chart generation failed: engine={}, message={}, traceId={}",
                        engine,
                        e.getMessage(),
                        traceId,
                        e
                );
            }
            return errorResponse("图表生成失败: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Flux<ProgressEvent> executeWithProgress(
            Map<String, Object> arguments,
            ToolExecutionContext context
    ) {
        return Flux.create(sink -> {
            try {
                sink.next(ProgressEvent.progress("preparing", 10));
                sink.next(ProgressEvent.progress("rendering", 50));

                Object result = execute(arguments, context);

                sink.next(ProgressEvent.progress("saving", 80));
                sink.next(ProgressEvent.complete(result));
                sink.complete();
            } catch (Exception e) {
                sink.next(ProgressEvent.error("CHART_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }

    private String saveChartImage(byte[] imageBytes, String format, String traceId) {
        try {
            String url = storageAdapter.save(imageBytes, format, traceId);
            log.info(
                    "Chart saved via {}: url={}, size={}KB",
                    storageAdapter.getType(),
                    safeUrlForLog(url),
                    imageBytes.length / 1024
            );
            return url;
        } catch (ChartStorageException e) {
            log.warn(
                    "Failed to save chart image, returning data URI: {}",
                    e.getMessage()
            );
            log.debug("Chart storage failure details", e);
            return "data:" + mediaType(format) + ";base64,"
                    + Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    private String mediaType(String format) {
        return "jpg".equalsIgnoreCase(format) ? "image/jpeg" : "image/" + format;
    }

    private String safeUrlForLog(String url) {
        return url != null && url.startsWith("data:") ? "data-uri" : url;
    }

    private Map<String, Object> errorResponse(String message) {
        return Map.of(
                "success", false,
                "error", true,
                "message", message
        );
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private static Map<String, Object> requireObject(Object value, String field) {
        if (!(value instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            throw new IllegalArgumentException(field + " 必须是非空对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static List<Map<String, Object>> requireData(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("data 必须是非空对象数组");
        }
        if (rawList.isEmpty()) {
            throw new IllegalArgumentException("data 必须是非空对象数组");
        }

        return rawList.stream()
                .map(item -> {
                    if (!(item instanceof Map<?, ?> rawMap)) {
                        throw new IllegalArgumentException("data 只能包含对象");
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    rawMap.forEach((key, fieldValue) ->
                            row.put(String.valueOf(key), fieldValue));
                    return row;
                })
                .toList();
    }
}
