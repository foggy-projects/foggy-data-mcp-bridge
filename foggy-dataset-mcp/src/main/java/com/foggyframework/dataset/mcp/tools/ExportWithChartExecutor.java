package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.mcp.spi.ProgressEvent;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared query/chart orchestration for the fixed-engine export tools.
 *
 * <p>This component is deliberately not an MCP tool. Public discovery is owned
 * by {@link ExportWithXChartTool} and {@link ExportWithEChartsTool}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportWithChartExecutor {

    private static final String SYS_META = "_sys_meta";

    private final QueryModelTool queryModelTool;
    private final ChartTool chartTool;

    public Object execute(
            Map<String, Object> arguments,
            ToolExecutionContext context,
            String engine
    ) {
        String traceId = context.getTraceId();
        try {
            PreparedRequest request = prepare(arguments);
            log.info(
                    "Export with {}: model={}, traceId={}",
                    engine,
                    request.model(),
                    traceId
            );

            RX<SemanticQueryResponse> queryResult = queryModelTool.executeQuery(
                    request.model(),
                    request.payload(),
                    "execute",
                    traceId,
                    context.getAuthorization()
            );
            if (!queryResult.isOk()) {
                return queryResult;
            }
            return renderQueryResult(
                    queryResult.getData(),
                    request.chart(),
                    context,
                    engine,
                    request.pivot()
            );
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                log.warn(
                        "Export with {} request rejected: {}, traceId={}",
                        engine,
                        e.getMessage(),
                        traceId
                );
            } else {
                log.error(
                        "Export with {} failed: {}, traceId={}",
                        engine,
                        e.getMessage(),
                        traceId,
                        e
                );
            }
            return RX.failB("导出失败: " + e.getMessage());
        }
    }

    public Flux<ProgressEvent> executeWithProgress(
            Map<String, Object> arguments,
            ToolExecutionContext context,
            String engine
    ) {
        return Flux.create(sink -> {
            try {
                sink.next(ProgressEvent.progress("querying", 20));
                PreparedRequest request = prepare(arguments);

                RX<SemanticQueryResponse> queryResult = queryModelTool.executeQuery(
                        request.model(),
                        request.payload(),
                        "execute",
                        context.getTraceId(),
                        context.getAuthorization()
                );
                if (!queryResult.isOk()) {
                    sink.next(ProgressEvent.error("QUERY_ERROR", queryResult.getMsg()));
                    sink.complete();
                    return;
                }

                sink.next(ProgressEvent.partialResult(Map.of("query", "completed")));
                sink.next(ProgressEvent.progress("rendering_chart", 60));
                Object result = renderQueryResult(
                        queryResult.getData(),
                        request.chart(),
                        context,
                        engine,
                        request.pivot()
                );

                sink.next(ProgressEvent.progress("finalizing", 90));
                sink.next(ProgressEvent.complete(result));
                sink.complete();
            } catch (Exception e) {
                sink.next(ProgressEvent.error("EXPORT_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }

    private PreparedRequest prepare(Map<String, Object> arguments) {
        String model = requiredString(arguments, "model");
        Map<String, Object> payload = objectValue(arguments.get("payload"), "payload", false);
        Map<String, Object> payloadCopy = deepCopyMap(payload);
        boolean pivot = normalizePivot(payloadCopy);
        Map<String, Object> chart = objectValue(arguments.get("chart"), "chart", true);
        return new PreparedRequest(model, payloadCopy, chart, pivot);
    }

    private boolean normalizePivot(Map<String, Object> payload) {
        Object pivotValue = payload.get("pivot");
        if (pivotValue == null) {
            return false;
        }
        Map<String, Object> pivot = objectValue(pivotValue, "payload.pivot", true);
        rejectTreeHierarchy(pivot.get("rows"), "payload.pivot.rows");
        rejectTreeHierarchy(pivot.get("columns"), "payload.pivot.columns");

        String outputFormat = optionalString(pivot.get("outputFormat"));
        if (outputFormat == null) {
            pivot.put("outputFormat", "flat");
        } else if (!"flat".equalsIgnoreCase(outputFormat)) {
            throw new IllegalArgumentException(
                    "图表导出仅支持 Pivot outputFormat=flat，当前值: " + outputFormat);
        }
        payload.put("pivot", pivot);
        return true;
    }

    private void rejectTreeHierarchy(Object axesValue, String field) {
        if (axesValue == null) {
            return;
        }
        if (!(axesValue instanceof List<?> axes)) {
            throw new IllegalArgumentException(field + " 必须是数组");
        }
        for (Object axis : axes) {
            if (axis instanceof Map<?, ?> axisMap) {
                Object hierarchyMode = axisMap.get("hierarchyMode");
                if (hierarchyMode != null
                        && "tree".equalsIgnoreCase(hierarchyMode.toString().trim())) {
                    throw new IllegalArgumentException(
                            "图表导出不支持 Pivot hierarchyMode=tree");
                }
            }
        }
    }

    private Object renderQueryResult(
            SemanticQueryResponse queryResponse,
            Map<String, Object> chart,
            ToolExecutionContext context,
            String engine,
            boolean pivot
    ) {
        List<Map<String, Object>> items = normalizeItems(queryResponse.getItems(), pivot);
        if (items.isEmpty()) {
            return buildResponse(queryResponse, items, null, "查询结果为空，无法生成图表");
        }

        Map<String, Object> chartArguments = new LinkedHashMap<>(chart);
        chartArguments.put("engine", engine);
        chartArguments.put("data", items);
        Object chartResult = chartTool.execute(chartArguments, context);

        String summary = isChartSuccess(chartResult)
                ? "查询和图表生成完成"
                : "查询完成，但图表生成失败";
        return buildResponse(queryResponse, items, chartResult, summary);
    }

    private List<Map<String, Object>> normalizeItems(
            List<Map<String, Object>> source,
            boolean pivot
    ) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        if (!pivot) {
            return source;
        }
        return source.stream()
                .filter(row -> !isPivotTotal(row))
                .toList();
    }

    private boolean isPivotTotal(Map<String, Object> row) {
        Object metaValue = row.get(SYS_META);
        if (!(metaValue instanceof Map<?, ?> meta)) {
            return false;
        }
        return Boolean.TRUE.equals(meta.get("isRowSubtotal"))
                || Boolean.TRUE.equals(meta.get("isColSubtotal"))
                || Boolean.TRUE.equals(meta.get("isGrandTotal"));
    }

    private boolean isChartSuccess(Object chartResult) {
        return chartResult instanceof Map<?, ?> chartMap
                && Boolean.TRUE.equals(chartMap.get("success"));
    }

    private Map<String, Object> buildResponse(
            SemanticQueryResponse queryResponse,
            List<Map<String, Object>> items,
            Object chartResult,
            String summary
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "result");
        response.put("items", items);
        response.put(
                "total",
                queryResponse.getTotal() != null ? queryResponse.getTotal() : items.size()
        );
        response.put("summary", summary);

        Map<String, Object> exports = new LinkedHashMap<>();
        if (chartResult instanceof Map<?, ?> chartMap) {
            if (Boolean.TRUE.equals(chartMap.get("success"))) {
                exports.put("charts", List.of(chartMap.get("chart")));
            } else if (chartMap.get("message") != null) {
                exports.put("chartError", chartMap.get("message"));
            }
        }
        response.put("exports", exports);
        return response;
    }

    private static String requiredString(Map<String, Object> arguments, String field) {
        Object value = arguments.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(field + " 不得为空");
        }
        return value.toString();
    }

    private static String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Map<String, Object> objectValue(
            Object value,
            String field,
            boolean requireNonEmpty
    ) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(
                    field + (requireNonEmpty ? " 必须是非空对象" : " 必须是对象"));
        }
        if (requireNonEmpty && rawMap.isEmpty()) {
            throw new IllegalArgumentException(field + " 必须是非空对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, deepCopyValue(value)));
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            rawMap.forEach((key, item) ->
                    copy.put(String.valueOf(key), deepCopyValue(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(deepCopyValue(item)));
            return copy;
        }
        return value;
    }

    private record PreparedRequest(
            String model,
            Map<String, Object> payload,
            Map<String, Object> chart,
            boolean pivot
    ) {
    }
}
