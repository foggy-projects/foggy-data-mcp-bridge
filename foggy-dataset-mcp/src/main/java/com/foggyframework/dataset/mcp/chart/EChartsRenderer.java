package com.foggyframework.dataset.mcp.chart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Optional external ECharts adapter.
 *
 * <p>This renderer does not translate XChart configuration. It forwards a raw
 * ECharts Option to the existing native render endpoint. Query data is injected
 * as {@code dataset.source}, which is an ECharts-specific operation.
 */
@Slf4j
@Component
public class EChartsRenderer implements ChartRenderer {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "svg");

    private final WebClient chartRenderWebClient;

    public EChartsRenderer(@Qualifier("chartRenderWebClient") WebClient chartRenderWebClient) {
        this.chartRenderWebClient = chartRenderWebClient;
    }

    @Override
    public String getEngine() {
        return "echarts";
    }

    @Override
    public ChartRenderResult render(ChartRenderRequest request) {
        Map<String, Object> option = injectDataset(request.config(), request.data());
        ChartImageSpec image = request.image();
        if (!SUPPORTED_FORMATS.contains(image.format())) {
            throw new IllegalArgumentException(
                    "ECharts 渲染器仅支持 png/svg，当前格式: " + image.format());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", "echarts");
        body.put("engine_spec", option);
        body.put("image", Map.of(
                "format", image.format(),
                "width", image.width(),
                "height", image.height()
        ));

        try {
            WebClient.RequestBodySpec httpRequest = chartRenderWebClient.post()
                    .uri("/render/native/stream")
                    .contentType(MediaType.APPLICATION_JSON);
            if (request.traceId() != null && !request.traceId().isBlank()) {
                httpRequest.header("X-Request-Id", request.traceId());
            }

            byte[] bytes = httpRequest.bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("ECharts 渲染服务未返回图片数据");
            }

            return new ChartRenderResult(
                    bytes,
                    image.format(),
                    image.width(),
                    image.height(),
                    inferChartType(option),
                    inferTitle(option)
            );
        } catch (WebClientResponseException e) {
            log.error(
                    "ECharts render service error: status={}, traceId={}",
                    e.getStatusCode(),
                    request.traceId()
            );
            throw new IllegalStateException(
                    "ECharts 渲染服务错误: HTTP " + e.getStatusCode().value(),
                    e
            );
        }
    }

    private Map<String, Object> injectDataset(
            Map<String, Object> originalOption,
            List<Map<String, Object>> data
    ) {
        Map<String, Object> option = new LinkedHashMap<>(originalOption);
        rejectEmbeddedSeriesData(option.get("series"));
        rejectEmbeddedAxisData(option.get("xAxis"));

        Object datasetValue = option.get("dataset");
        if (datasetValue == null) {
            option.put("dataset", Map.of("source", data));
            return option;
        }
        if (!(datasetValue instanceof Map<?, ?> datasetMap)) {
            throw new IllegalArgumentException(
                    "ECharts 图表仅支持单个 dataset 对象，不支持 dataset 数组");
        }

        Map<String, Object> dataset = new LinkedHashMap<>();
        datasetMap.forEach((key, value) -> dataset.put(String.valueOf(key), value));
        if (dataset.containsKey("source")) {
            throw new IllegalArgumentException(
                    "ECharts config.dataset.source 不得内嵌数据");
        }
        if (dataset.containsKey("transform")
                || dataset.containsKey("fromDatasetId")
                || dataset.containsKey("fromDatasetIndex")) {
            throw new IllegalArgumentException(
                    "ECharts 图表暂不支持 dataset transform 或 dataset 链");
        }
        dataset.put("source", data);
        option.put("dataset", dataset);
        return option;
    }

    private void rejectEmbeddedSeriesData(Object seriesValue) {
        for (Map<?, ?> series : objectOrObjectList(seriesValue, "series")) {
            if (series.containsKey("data")) {
                throw new IllegalArgumentException(
                        "ECharts config.series[*].data 不得内嵌数据");
            }
        }
    }

    private void rejectEmbeddedAxisData(Object axisValue) {
        for (Map<?, ?> axis : objectOrObjectList(axisValue, "xAxis")) {
            if (axis.containsKey("data")) {
                throw new IllegalArgumentException(
                        "ECharts config.xAxis.data 不得内嵌数据");
            }
        }
    }

    private List<Map<?, ?>> objectOrObjectList(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Map<?, ?> map) {
            return List.of(map);
        }
        if (value instanceof List<?> list) {
            List<Map<?, ?>> result = new java.util.ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException(
                            "ECharts config." + field + " 必须是对象或对象数组");
                }
                result.add(map);
            }
            return result;
        }
        throw new IllegalArgumentException(
                "ECharts config." + field + " 必须是对象或对象数组");
    }

    private String inferChartType(Map<String, Object> option) {
        Object seriesValue = option.get("series");
        if (seriesValue instanceof Map<?, ?> series) {
            return seriesType(series);
        }
        if (seriesValue instanceof List<?> series && !series.isEmpty()
                && series.get(0) instanceof Map<?, ?> firstSeries) {
            return seriesType(firstSeries);
        }
        return "echarts";
    }

    private String seriesType(Map<?, ?> series) {
        Object type = series.get("type");
        return type == null
                ? "echarts"
                : type.toString().toLowerCase(Locale.ROOT);
    }

    private String inferTitle(Map<String, Object> option) {
        Object titleValue = option.get("title");
        if (titleValue instanceof Map<?, ?> title) {
            Object text = title.get("text");
            return text == null ? "" : text.toString();
        }
        return "";
    }
}
