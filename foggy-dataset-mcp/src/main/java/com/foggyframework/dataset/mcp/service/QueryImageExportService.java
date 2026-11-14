package com.foggyframework.dataset.mcp.service;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.mcp.chart.ChartImageSpec;
import com.foggyframework.dataset.mcp.chart.ChartRenderRequest;
import com.foggyframework.dataset.mcp.chart.ChartRenderResult;
import com.foggyframework.dataset.mcp.chart.QueryImageRenderer;
import com.foggyframework.dataset.mcp.storage.ChartStorageAdapter;
import com.foggyframework.dataset.mcp.tools.QueryModelTool;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Single governed query followed by a bounded in-process PNG render. */
@Service
public class QueryImageExportService {
    private static final Set<String> KEYS = Set.of("model", "payload", "kind", "title", "xField", "yField",
            "xLabel", "yLabel", "unit", "columns", "maxRows", "width", "height", "delivery",
            "deniedColumns", "systemSlice");
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final QueryModelTool queryModelTool;
    private final QueryImageRenderer renderer;
    private final ChartStorageAdapter storage;

    public QueryImageExportService(QueryModelTool queryModelTool, QueryImageRenderer renderer, ChartStorageAdapter storage) {
        this.queryModelTool = queryModelTool;
        this.renderer = renderer;
        this.storage = storage;
    }

    public Map<String, Object> export(Map<String, Object> args, ToolExecutionContext context) {
        Prepared prepared;
        try {
            prepared = prepare(args);
        } catch (IllegalArgumentException e) {
            return failure("INVALID_IMAGE_REQUEST", e.getMessage());
        }
        RX<SemanticQueryResponse> result;
        try {
            result = queryModelTool.executeQuery(prepared.model(), prepared.payload(), "execute",
                    context.getTraceId(), context.getAuthorization(), context.getNamespace(), args);
        } catch (Exception e) {
            return failure("QUERY_FAILED", "查询未能完成，未生成图片。请使用普通查询入口查看受治理的诊断。");
        }
        if (result == null || !result.isOk() || result.getData() == null) {
            // Never expose SQL, connection strings or exception messages through the image surface.
            return failure("QUERY_FAILED", "查询失败或访问被拒绝，未生成图片。请检查模型、namespace 和查询权限。");
        }
        SemanticQueryResponse response = result.getData();
        List<Map<String, Object>> rows = imageRows(response.getItems(), prepared.payload().containsKey("pivot"));
        if (rows.isEmpty()) {
            return failure("NO_QUERY_DATA", "查询没有返回可展示的数据，未生成图片。");
        }
        try {
            Map<String, Object> config = new LinkedHashMap<>(prepared.config());
            Object requestedColumns = args.get("columns");
            if (!"table".equals(config.get("kind")) && requestedColumns == null) {
                requestedColumns = List.of(Map.of("field", config.get("xField")), Map.of("field", config.get("yField")));
            }
            List<Map<String, Object>> columns = resolveColumns(requestedColumns, response, rows);
            config.put("columns", columns);
            String kind = config.get("kind").toString();
            if (!"table".equals(kind)) {
                String x = config.get("xField").toString();
                String y = config.get("yField").toString();
                requireResultField(rows, x);
                requireResultField(rows, y);
                config.putIfAbsent("xLabel", caption(x, response));
                config.putIfAbsent("yLabel", caption(y, response));
            }
            int rendered = Math.min(rows.size(), prepared.maxRows());
            // total defaults to zero when the query did not request a count.
            Long knownTotal = response.getTotal() != null && response.getTotal() >= rows.size()
                    ? response.getTotal() : null;
            var pagination = response.getPagination();
            if (pagination != null && pagination.getTotalCount() != null && pagination.getTotalCount() >= rows.size()) {
                knownTotal = pagination.getTotalCount();
            }
            boolean startsAfterFirstPage = pagination != null && pagination.getStart() != null && pagination.getStart() > 0
                    || prepared.payload().get("start") instanceof Number start && start.doubleValue() > 0
                    || prepared.payload().get("cursor") instanceof String cursor && !cursor.isBlank();
            boolean queryPartial = Boolean.TRUE.equals(response.getHasNext())
                    || startsAfterFirstPage
                    || (pagination != null && Boolean.TRUE.equals(pagination.getHasMore()))
                    || (knownTotal != null && knownTotal > rows.size())
                    || (response.getTruncationInfo() != null && !response.getTruncationInfo().isEmpty());
            boolean truncated = rendered < rows.size() || queryPartial;
            String footer = "显示 " + rendered + " 行 · 本次查询返回 " + rows.size() + " 行";
            if (knownTotal != null) footer += " · 查询共 " + knownTotal + " 行";
            if (truncated) footer += " · 部分结果，请勿视为全部数据";
            config.put("footer", footer);
            ChartRenderResult png = renderer.render(new ChartRenderRequest(config,
                    rows.subList(0, rendered), prepared.image(), context.getTraceId()));
            if (png.bytes().length > MAX_BYTES) return failure("IMAGE_TOO_LARGE", "图片超过 8 MiB，请减少行列或图片尺寸。");
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("mimeType", "image/png");
            image.put("kind", kind);
            image.put("title", png.title());
            image.put("width", png.width());
            image.put("height", png.height());
            image.put("fileSize", png.bytes().length);
            if ("link".equals(prepared.delivery())) {
                // Do not incorporate caller-controlled trace IDs into storage paths.
                image.put("url", storage.save(png.bytes(), "png", UUID.randomUUID().toString()));
            } else {
                image.put("data", Base64.getEncoder().encodeToString(png.bytes()));
            }
            Map<String, Object> exported = new LinkedHashMap<>();
            exported.put("success", true);
            exported.put("image", image);
            exported.put("rowsRendered", rendered);
            exported.put("rowsReturned", rows.size());
            exported.put("truncated", truncated);
            exported.put("columns", columns);
            return exported;
        } catch (IllegalArgumentException e) {
            // Renderer validation messages contain rule names only, never row values.
            return failure("IMAGE_RENDER_FAILED", e.getMessage());
        } catch (Exception e) {
            return failure("IMAGE_RENDER_FAILED", "图片生成或保存失败。请检查图片尺寸、字段类型和存储可用性。");
        }
    }

    private Prepared prepare(Map<String, Object> args) {
        if (args == null || !KEYS.containsAll(args.keySet())) throw new IllegalArgumentException("图片请求包含不支持的参数。");
        String model = text(args.get("model"), null, 160);
        if (model == null) throw new IllegalArgumentException("model 为必填项。");
        if (!(args.get("payload") instanceof Map<?, ?> raw)) throw new IllegalArgumentException("payload 必须为查询对象。");
        Map<String, Object> payload = copyMap(raw);
        normalizePivot(payload);
        String kind = text(args.get("kind"), "table", 16);
        if (!Set.of("table", "bar", "line").contains(kind)) throw new IllegalArgumentException("kind 仅支持 table、bar、line。");
        int width = integer(args.get("width"), 1200, 640, 2400);
        int height = integer(args.get("height"), "table".equals(kind) ? 360 : 700, "table".equals(kind) ? 360 : 520, 2400);
        if ((long) width * height > 8_000_000) throw new IllegalArgumentException("图片像素面积过大。");
        int rowCap = "table".equals(kind) ? 50 : "bar".equals(kind) ? 40 : 100;
        int maxRows = integer(args.get("maxRows"), rowCap, 1, rowCap);
        String delivery = text(args.get("delivery"), "inline", 12);
        if (!Set.of("inline", "link").contains(delivery)) throw new IllegalArgumentException("delivery 仅支持 inline 或 link。");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("kind", kind);
        config.put("title", text(args.get("title"), "查询结果", 120));
        for (String key : List.of("xField", "yField", "xLabel", "yLabel", "unit")) {
            int limit = "unit".equals(key) ? 32 : key.endsWith("Label") ? 80 : 100;
            String value = text(args.get(key), null, limit);
            if (value != null) config.put(key, value);
        }
        if (!"table".equals(kind) && (!config.containsKey("xField") || !config.containsKey("yField"))) {
            throw new IllegalArgumentException("柱状图和折线图必须显式指定 xField、yField。");
        }
        // Validate column shape before executing a query.
        explicitColumns(args.get("columns"));
        return new Prepared(model, payload, config, new ChartImageSpec(width, height, "png"), maxRows, delivery);
    }

    private List<Map<String, Object>> resolveColumns(Object requested, SemanticQueryResponse response, List<Map<String, Object>> rows) {
        List<Map<String, Object>> columns = explicitColumns(requested);
        if (columns.isEmpty()) {
            Set<String> fields = new LinkedHashSet<>();
            if (response.getSchema() != null && response.getSchema().getColumns() != null) {
                response.getSchema().getColumns().forEach(c -> { if (c.getName() != null) fields.add(c.getName()); });
            }
            if (fields.isEmpty()) fields.addAll(rows.get(0).keySet());
            fields.removeIf(f -> f.startsWith("_sys_"));
            if (fields.size() > 12) throw new IllegalArgumentException("表格最多展示 12 列，请显式选择 columns。");
            for (String field : fields) columns.add(new LinkedHashMap<>(Map.of("field", field, "caption", caption(field, response))));
        }
        for (Map<String, Object> column : columns) {
            String field = column.get("field").toString();
            requireResultField(rows, field);
            column.putIfAbsent("caption", caption(field, response));
        }
        return columns;
    }

    private List<Map<String, Object>> explicitColumns(Object value) {
        List<Map<String, Object>> columns = new ArrayList<>();
        if (value == null) return columns;
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 12) throw new IllegalArgumentException("columns 必须包含 1-12 列。");
        Set<String> used = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw) || !Set.of("field", "caption", "unit").containsAll(raw.keySet())) {
                throw new IllegalArgumentException("每列仅支持 field、caption 和 unit。");
            }
            String field = text(raw.get("field"), null, 100);
            if (field == null || field.startsWith("_sys_") || !used.add(field)) throw new IllegalArgumentException("列字段缺失、重复或属于内部字段。");
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("field", field);
            for (String key : List.of("caption", "unit")) {
                String text = text(raw.get(key), null, "unit".equals(key) ? 32 : 100);
                if (text != null) column.put(key, text);
            }
            columns.add(column);
        }
        return columns;
    }

    private String caption(String field, SemanticQueryResponse response) {
        if (response.getSchema() != null && response.getSchema().getColumns() != null) {
            for (var column : response.getSchema().getColumns()) {
                if (field.equals(column.getName()) && column.getTitle() != null && !column.getTitle().isBlank()) {
                    return text(column.getTitle(), field, 100);
                }
            }
        }
        return field;
    }

    private void requireResultField(List<Map<String, Object>> rows, String field) {
        if (field.startsWith("_sys_") || rows.stream().anyMatch(r -> !r.containsKey(field))) {
            throw new IllegalArgumentException("展示字段必须存在于最终查询结果中。");
        }
    }

    private void normalizePivot(Map<String, Object> payload) {
        if (!payload.containsKey("pivot")) return;
        if (!(payload.get("pivot") instanceof Map<?, ?> raw)) throw new IllegalArgumentException("pivot 必须为对象。");
        Map<String, Object> pivot = copyMap(raw);
        String format = text(pivot.get("outputFormat"), "flat", 12);
        if (!"flat".equalsIgnoreCase(format)) throw new IllegalArgumentException("图片导出只支持 flat Pivot。");
        for (String key : List.of("rows", "columns")) {
            if (pivot.get(key) instanceof List<?> axes) {
                for (Object axis : axes) {
                    if (axis instanceof Map<?, ?> map && "tree".equalsIgnoreCase(String.valueOf(map.get("hierarchyMode")))) {
                        throw new IllegalArgumentException("图片导出不支持树状 Pivot。");
                    }
                }
            }
        }
        pivot.put("outputFormat", "flat");
        payload.put("pivot", pivot);
    }

    private List<Map<String, Object>> imageRows(List<Map<String, Object>> source, boolean pivot) {
        if (source == null) return List.of();
        if (!pivot) return source;
        return source.stream().filter(row -> {
            if (!(row.get("_sys_meta") instanceof Map<?, ?> meta)) return true;
            return !Boolean.TRUE.equals(meta.get("isRowSubtotal")) && !Boolean.TRUE.equals(meta.get("isColSubtotal")) && !Boolean.TRUE.equals(meta.get("isGrandTotal"));
        }).toList();
    }

    private static String text(Object value, String fallback, int max) {
        if (value == null) return fallback;
        if (!(value instanceof String text) || text.isBlank() || text.length() > max || text.chars().anyMatch(c -> Character.isISOControl(c))) {
            throw new IllegalArgumentException("文字参数必须非空，且不超过允许长度或包含控制字符。");
        }
        return text;
    }

    private static int integer(Object value, int fallback, int min, int max) {
        if (value == null) return fallback;
        if (!(value instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < min || n.intValue() > max) {
            throw new IllegalArgumentException("行数或图片尺寸超出允许范围。");
        }
        return n.intValue();
    }

    private static Map<String, Object> copyMap(Map<?, ?> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((k, v) -> copy.put(String.valueOf(k), copyValue(v)));
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) return copyMap(map);
        if (value instanceof List<?> list) return list.stream().map(QueryImageExportService::copyValue).toList();
        return value;
    }

    private static Map<String, Object> failure(String code, String message) {
        return Map.of("success", false, "error", Map.of("code", code, "message", message));
    }

    private record Prepared(String model, Map<String, Object> payload, Map<String, Object> config,
            ChartImageSpec image, int maxRows, String delivery) { }
}
