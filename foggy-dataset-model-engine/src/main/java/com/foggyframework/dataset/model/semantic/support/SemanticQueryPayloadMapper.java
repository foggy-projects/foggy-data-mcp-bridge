package com.foggyframework.dataset.model.semantic.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.enums.CaptionMatchMode;
import com.foggyframework.dataset.model.semantic.enums.MismatchHandleStrategy;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridInputBinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final String SLICE_CONTRACT_ERROR = "QUERY_MODEL_SLICE_CONTRACT_INVALID";
    private static final String SLICE_CONTRACT_HINT =
            "Use entries like {\"field\":\"fieldName\",\"op\":\"=\",\"value\":value} "
                    + "or {\"$or\":[...]} objects; do not pass booleans, strings, or JSON-escaped keys.";

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
        request.setSlice(convertSliceItemsIfPresent(payload, "slice"));
        request.setHaving(convertSliceItemsIfPresent(payload, "having"));
        request.setPostSlice(convertSliceItemsIfPresent(payload, "postSlice"));
        request.setGroupBy(convertGroupBy(payload.get("groupBy")));
        request.setOrderBy(convertOrderBy(payload.get("orderBy")));
        request.setStart(intValue(payload.get("start")));
        request.setLimit(intValue(payload.get("limit")));
        request.setCursor(stringValue(payload.get("cursor")));
        request.setHints(convertMap(payload.get("hints")));
        request.setExtData(convertMap(payload.get("extData")));
        request.setStream(boolValue(payload.get("stream")));
        request.setReturnTotal(boolOrDefault(payload.get("returnTotal"), request.getReturnTotal()));
        request.setDistinct(boolOrDefault(payload.get("distinct"), request.getDistinct()));
        request.setWithSubtotals(boolOrDefault(payload.get("withSubtotals"), request.getWithSubtotals()));
        request.setTimeWindow(convertMap(payload.get("timeWindow")));
        request.setRoute(stringValue(payload.get("route")));
        request.setStatus(stringValue(payload.get("status")));
        request.setRiskFlags(optionalStringList(firstPresent(payload, "risk_flags", "riskFlags")));
        request.setClarifyingQuestions(optionalStringList(firstPresent(payload, "clarifying_questions", "clarifyingQuestions")));
        request.setWhy(optionalStringList(payload.get("why")));
        request.setExecutablePlan(firstPresent(payload, "executable_plan", "executablePlan"));
        request.setSemanticSql(stringValue(firstPresent(payload, "semantic_sql", "semanticSql")));
        request.setMemoryGridPlan(convertMap(firstPresent(payload, "memory_grid_plan", "memoryGridPlan")));
        request.setGridSql(stringValue(firstPresent(payload, "grid_sql", "gridSql")));
        request.setMemoryGridBindings(convertList(firstPresent(payload, "bindings", "memoryGridBindings"),
                MemoryGridInputBinding.class));

        String captionMatchMode = stringValue(payload.get("captionMatchMode"));
        if (captionMatchMode != null && !captionMatchMode.isBlank()) {
            request.setCaptionMatchMode(CaptionMatchMode.valueOf(captionMatchMode));
        }
        String mismatchStrategy = stringValue(payload.get("mismatchHandleStrategy"));
        if (mismatchStrategy != null && !mismatchStrategy.isBlank()) {
            request.setMismatchHandleStrategy(MismatchHandleStrategy.valueOf(mismatchStrategy));
        }
        if (payload.containsKey("pivot")) {
            request.setPivot(toPivotRequest(payload.get("pivot")));
        }
        return request;
    }

    /**
     * Converts the public pivot payload into the engine AST.
     *
     * <p>The public contract accepts both the native object form
     * {@code {"field":"orderStatus"}} and the LLM-friendly shorthand
     * {@code "orderStatus"} for row/column axis entries.</p>
     */
    public PivotRequest toPivotRequest(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(
                    "QUERY_MODEL_PIVOT_CONTRACT_INVALID: payload.pivot must be an object");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException(
                        "QUERY_MODEL_PIVOT_CONTRACT_INVALID: payload.pivot keys must be non-empty strings");
            }
            normalized.put(key, entry.getValue());
        }
        normalizePivotAxis(normalized, "rows");
        normalizePivotAxis(normalized, "columns");
        return objectMapper.convertValue(normalized, PivotRequest.class);
    }

    private static void normalizePivotAxis(Map<String, Object> pivot, String axisName) {
        if (!pivot.containsKey(axisName) || pivot.get(axisName) == null) {
            return;
        }
        Object value = pivot.get(axisName);
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException(
                    "QUERY_MODEL_PIVOT_CONTRACT_INVALID: payload.pivot."
                            + axisName + " must be an array");
        }

        List<Object> normalized = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            if (entry instanceof String field) {
                if (field.isBlank()) {
                    throw new IllegalArgumentException(
                            "QUERY_MODEL_PIVOT_CONTRACT_INVALID: payload.pivot."
                                    + axisName + "[" + i + "] must not be blank");
                }
                normalized.add(Map.of("field", field));
            } else if (entry instanceof Map<?, ?>) {
                normalized.add(entry);
            } else {
                throw new IllegalArgumentException(
                        "QUERY_MODEL_PIVOT_CONTRACT_INVALID: payload.pivot."
                                + axisName + "[" + i + "] must be a field string or object");
            }
        }
        pivot.put(axisName, normalized);
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

    public List<SliceRequestDef> extractSystemSlice(Map<String, Object> options) {
        if (options == null) {
            return null;
        }
        if (!options.containsKey("systemSlice")) {
            return null;
        }
        Object value = options.get("systemSlice");
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> sliceList)) {
            throw invalidSlice("options.systemSlice", "must be an array of filter objects, got " + typeName(value));
        }
        if (sliceList.isEmpty()) {
            return null;
        }
        List<SliceRequestDef> result = new ArrayList<>();
        for (int i = 0; i < sliceList.size(); i++) {
            Object entry = sliceList.get(i);
            if (!(entry instanceof Map<?, ?> map)) {
                throw invalidSlice("options.systemSlice[" + i + "]",
                        "must be an object, got " + typeName(entry));
            }
            result.add(convertToSliceRequestDef(map, "options.systemSlice[" + i + "]"));
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    private List<SemanticQueryRequest.SliceItem> convertSliceItemsIfPresent(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key)) {
            return null;
        }
        return convertSliceItems(payload.get(key), "payload." + key, true);
    }

    private List<SemanticQueryRequest.SliceItem> convertSliceItems(Object value, String path, boolean allowEmptyList) {
        if (!(value instanceof List<?> list)) {
            throw invalidSlice(path, "must be an array of filter objects, got " + typeName(value));
        }
        if (list.isEmpty()) {
            if (allowEmptyList) {
                return null;
            }
            throw invalidSlice(path, "must be a non-empty array of filter objects");
        }
        List<SemanticQueryRequest.SliceItem> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (!(entry instanceof Map<?, ?> map)) {
                throw invalidSlice(path + "[" + i + "]", "must be an object, got " + typeName(entry));
            }
            result.add(convertToSliceItem(map, path + "[" + i + "]"));
        }
        return result;
    }

    private List<SemanticQueryRequest.SliceItem> convertLogicalSliceItems(Object value, String path) {
        return convertSliceItems(value, path, false);
    }

    private SemanticQueryRequest.SliceItem convertToSliceItem(Map<?, ?> map, String path) {
        validateSliceMap(map, path);
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        if (map.size() == 1) {
            String key = firstSliceKey(map);
            if ("$or".equals(key)) {
                item.setOr(convertLogicalSliceItems(map.get(key), path + ".$or"));
                return item;
            }
            if ("$and".equals(key)) {
                item.setAnd(convertLogicalSliceItems(map.get(key), path + ".$and"));
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
            item.setOr(convertLogicalSliceItems(map.get("$or"), path + ".$or"));
        }
        if (map.containsKey("$and")) {
            item.setAnd(convertLogicalSliceItems(map.get("$and"), path + ".$and"));
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

    private SliceRequestDef convertToSliceRequestDef(Map<?, ?> map, String path) {
        validateSliceMap(map, path);
        SliceRequestDef item = new SliceRequestDef();
        if (map.size() == 1) {
            String key = firstSliceKey(map);
            if ("$or".equals(key)) {
                item.setOr(convertGroupConditions(map.get(key), path + ".$or"));
                return item;
            }
            if ("$and".equals(key)) {
                item.setAnd(convertGroupConditions(map.get(key), path + ".$and"));
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
            item.setOr(convertGroupConditions(map.get("$or"), path + ".$or"));
        }
        if (map.containsKey("$and")) {
            item.setAnd(convertGroupConditions(map.get("$and"), path + ".$and"));
        }
        return item;
    }

    private List<CondRequestDef> convertGroupConditions(Object value, String path) {
        if (!(value instanceof List<?> entries)) {
            throw invalidSlice(path, "must be an array of filter objects, got " + typeName(value));
        }
        if (entries.isEmpty()) {
            throw invalidSlice(path, "must be a non-empty array of filter objects");
        }
        List<CondRequestDef> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            if (!(entry instanceof Map<?, ?> map)) {
                throw invalidSlice(path + "[" + i + "]", "must be an object, got " + typeName(entry));
            }
            result.add(convertToSliceRequestDef(map, path + "[" + i + "]"));
        }
        return result;
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

    private static Object firstPresent(Map<String, Object> map, String first, String second) {
        if (map == null) {
            return null;
        }
        return map.containsKey(first) ? map.get(first) : map.get(second);
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

    private static void validateSliceMap(Map<?, ?> map, String path) {
        if (map.isEmpty()) {
            throw invalidSlice(path, "must not be empty");
        }
        for (Object rawKey : map.keySet()) {
            if (!(rawKey instanceof String key) || isBlank(key)) {
                throw invalidSlice(path, "keys must be non-empty strings");
            }
            validateSliceKey(key, path);
        }
    }

    private static void validateSliceKey(String key, String path) {
        if (!key.equals(key.trim())) {
            throw invalidSlice(path, "contains invalid key '" + key + "'. Keys must not have surrounding whitespace");
        }
        if (key.indexOf('"') >= 0 || key.indexOf('\'') >= 0 || key.indexOf('\\') >= 0) {
            throw invalidSlice(path,
                    "contains invalid key '" + key + "'. Logic keys must be exactly $or or $and");
        }
    }

    private static String firstSliceKey(Map<?, ?> map) {
        return (String) map.keySet().iterator().next();
    }

    private static RuntimeException invalidSlice(String path, String detail) {
        return RX.throwAUserTip(SLICE_CONTRACT_ERROR + ": " + path + " " + detail + ". "
                + SLICE_CONTRACT_HINT);
    }

    private static String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        return value.getClass().getSimpleName();
    }
}
