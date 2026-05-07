package com.foggyframework.dataset.db.model.semantic.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.enums.CaptionMatchMode;
import com.foggyframework.dataset.db.model.semantic.enums.MismatchHandleStrategy;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts the public dataset.query_model schema payload into the internal
 * {@link SemanticQueryRequest} shape without depending on MCP classes.
 */
@Component
public class SemanticQueryPayloadMapper {

    private static final Set<String> RESERVED_SLICE_KEYS = Set.of(
            "$or", "$and", "field", "op", "value", "maxDepth", "$expr"
    );

    private final ObjectMapper objectMapper;

    public SemanticQueryPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public SemanticQueryRequest toQueryRequest(Map<String, Object> payload) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        if (payload == null || payload.isEmpty()) {
            return request;
        }

        request.setColumns(optionalStringList(payload.get("columns")));
        request.setCalculatedFields(convertList(payload.get("calculatedFields"), CalculatedFieldDef.class));
        request.setSlice(convertSliceItems(payload.get("slice")));
        request.setHaving(convertSliceItems(payload.get("having")));
        request.setGroupBy(convertGroupBy(payload.get("groupBy")));
        request.setOrderBy(convertOrderBy(payload.get("orderBy")));
        request.setStart(intValue(payload.get("start")));
        request.setLimit(intValue(payload.get("limit")));
        request.setCursor(stringValue(payload.get("cursor")));
        request.setHints(convertMap(payload.get("hints")));
        request.setStream(boolValue(payload.get("stream")));
        request.setReturnTotal(boolOrDefault(payload.get("returnTotal"), request.getReturnTotal()));
        request.setDistinct(boolOrDefault(payload.get("distinct"), request.getDistinct()));
        request.setWithSubtotals(boolOrDefault(payload.get("withSubtotals"), request.getWithSubtotals()));
        request.setTimeWindow(convertMap(payload.get("timeWindow")));

        String captionMatchMode = stringValue(payload.get("captionMatchMode"));
        if (captionMatchMode != null && !captionMatchMode.isBlank()) {
            request.setCaptionMatchMode(CaptionMatchMode.valueOf(captionMatchMode));
        }
        String mismatchStrategy = stringValue(payload.get("mismatchHandleStrategy"));
        if (mismatchStrategy != null && !mismatchStrategy.isBlank()) {
            request.setMismatchHandleStrategy(MismatchHandleStrategy.valueOf(mismatchStrategy));
        }
        if (payload.get("pivot") instanceof Map<?, ?> pivotMap) {
            request.setPivot(objectMapper.convertValue(pivotMap, PivotRequest.class));
        }
        return request;
    }

    public Set<String> optionalStringSet(Object value) {
        List<String> list = optionalStringList(value);
        return list == null ? null : new LinkedHashSet<>(list);
    }

    @SuppressWarnings("unchecked")
    public List<DeniedPhysicalColumn> extractDeniedColumns(Map<String, Object> options) {
        if (options == null) {
            return null;
        }
        Object value = options.get("deniedColumns");
        if (!(value instanceof List<?> deniedList) || deniedList.isEmpty()) {
            return null;
        }
        List<DeniedPhysicalColumn> result = new ArrayList<>();
        for (Object entry : deniedList) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object columnsValue = map.get("columns");
            if (columnsValue instanceof List<?> columns) {
                String schema = stringValue(map.get("schema"));
                String table = stringValue(map.get("table"));
                for (Object columnObj : columns) {
                    String column = stringValue(columnObj);
                    if (!isBlank(table) && !isBlank(column)) {
                        result.add(new DeniedPhysicalColumn(schema, table, column));
                    }
                }
                continue;
            }
            String table = stringValue(map.get("table"));
            String column = stringValue(map.get("column"));
            if (!isBlank(table) && !isBlank(column)) {
                result.add(new DeniedPhysicalColumn(stringValue(map.get("schema")), table, column));
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    public List<SliceRequestDef> extractSystemSlice(Map<String, Object> options) {
        if (options == null) {
            return null;
        }
        Object value = options.get("systemSlice");
        if (!(value instanceof List<?> sliceList) || sliceList.isEmpty()) {
            return null;
        }
        List<SliceRequestDef> result = new ArrayList<>();
        for (Object entry : sliceList) {
            if (entry instanceof Map<?, ?> map) {
                result.add(convertToSliceRequestDef((Map<String, Object>) map));
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<SemanticQueryRequest.SliceItem> convertSliceItems(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<SemanticQueryRequest.SliceItem> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                result.add(convertToSliceItem((Map<String, Object>) map));
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private SemanticQueryRequest.SliceItem convertToSliceItem(Map<String, Object> map) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        if (map.size() == 1) {
            String key = map.keySet().iterator().next();
            if ("$or".equals(key)) {
                item.setOr(convertSliceItems(map.get(key)));
                return item;
            }
            if ("$and".equals(key)) {
                item.setAnd(convertSliceItems(map.get(key)));
                return item;
            }
            if (!RESERVED_SLICE_KEYS.contains(key)) {
                item.setField(key);
                item.setOp("=");
                item.setValue(map.get(key));
                return item;
            }
        }
        item.setField(stringValue(map.get("field")));
        item.setOp(stringOr(map.get("op"), "="));
        item.setValue(map.get("value"));
        if (map.containsKey("$or")) {
            item.setOr(convertSliceItems(map.get("$or")));
        }
        if (map.containsKey("$and")) {
            item.setAnd(convertSliceItems(map.get("$and")));
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<SemanticQueryRequest.GroupByItem> convertGroupBy(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<SemanticQueryRequest.GroupByItem> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String field) {
                result.add(new SemanticQueryRequest.GroupByItem(field, null));
            } else if (entry instanceof Map<?, ?> map) {
                result.add(new SemanticQueryRequest.GroupByItem(
                        stringValue(map.get("field")),
                        stringValue(map.get("agg"))
                ));
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private List<SemanticQueryRequest.OrderItem> convertOrderBy(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<SemanticQueryRequest.OrderItem> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String text) {
                result.add(parseOrderByShorthand(text));
            } else if (entry instanceof Map<?, ?> map) {
                SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
                item.setField(stringOr(map.get("field"), stringValue(map.get("column"))));
                item.setDir(stringOr(map.get("dir"), stringOr(map.get("direction"), "asc")));
                result.add(item);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private SemanticQueryRequest.OrderItem parseOrderByShorthand(String text) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        String trimmed = text.trim();
        if (trimmed.startsWith("-")) {
            item.setField(trimmed.substring(1).trim());
            item.setDir("desc");
            return item;
        }
        int spaceIndex = trimmed.lastIndexOf(' ');
        if (spaceIndex > 0) {
            String field = trimmed.substring(0, spaceIndex).trim();
            String dir = trimmed.substring(spaceIndex + 1).trim().toLowerCase();
            if ("asc".equals(dir) || "desc".equals(dir)) {
                item.setField(field);
                item.setDir(dir);
                return item;
            }
        }
        item.setField(trimmed);
        item.setDir("asc");
        return item;
    }

    @SuppressWarnings("unchecked")
    private SliceRequestDef convertToSliceRequestDef(Map<String, Object> map) {
        SliceRequestDef item = new SliceRequestDef();
        if (map.size() == 1) {
            String key = map.keySet().iterator().next();
            if ("$or".equals(key)) {
                item.setOr(convertGroupConditions((List<Object>) map.get(key)));
                return item;
            }
            if ("$and".equals(key)) {
                item.setAnd(convertGroupConditions((List<Object>) map.get(key)));
                return item;
            }
            if (!RESERVED_SLICE_KEYS.contains(key)) {
                item.setField(key);
                item.setOp("=");
                item.setValue(map.get(key));
                return item;
            }
        }
        item.setField(stringValue(map.get("field")));
        item.setOp(stringOr(map.get("op"), "="));
        item.setValue(map.get("value"));
        if (map.containsKey("maxDepth") && map.get("maxDepth") instanceof Number maxDepth) {
            item.setMaxDepth(maxDepth.intValue());
        }
        if (map.containsKey("$expr")) {
            item.setExpr(stringValue(map.get("$expr")));
        }
        if (map.containsKey("$or")) {
            item.setOr(convertGroupConditions((List<Object>) map.get("$or")));
        }
        if (map.containsKey("$and")) {
            item.setAnd(convertGroupConditions((List<Object>) map.get("$and")));
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<CondRequestDef> convertGroupConditions(List<Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        List<CondRequestDef> result = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> map) {
                result.add(convertToSliceRequestDef((Map<String, Object>) map));
            }
        }
        return result.isEmpty() ? null : result;
    }

    private <T> List<T> convertList(Object value, Class<T> itemClass) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(list, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, itemClass));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }

    private List<String> optionalStringList(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = stringValue(item);
            if (!isBlank(text)) {
                result.add(text);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private static Boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static Boolean boolOrDefault(Object value, Boolean fallback) {
        Boolean bool = boolValue(value);
        return bool != null ? bool : fallback;
    }

    private static String stringOr(Object value, String fallback) {
        String text = stringValue(value);
        return isBlank(text) ? fallback : text;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
