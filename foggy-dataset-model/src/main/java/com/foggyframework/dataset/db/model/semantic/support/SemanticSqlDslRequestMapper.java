package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a mapped Virtual Semantic SQL v1 evidence plan into a normal
 * {@link SemanticQueryRequest} that can reuse the existing DSL query pipeline.
 *
 * <p>The bridge is intentionally narrower than {@link SemanticSqlToDslMapper}.
 * The mapper can record evidence for expressions that are not yet safe to
 * execute through the normal DSL path; this class only accepts predicates and
 * projections that have a direct DSL request equivalent.</p>
 */
public final class SemanticSqlDslRequestMapper {

    public static final String STATUS_READY = "BRIDGE_READY";
    public static final String STATUS_DEFERRED = "BRIDGE_DEFERRED";

    private SemanticSqlDslRequestMapper() {
    }

    public static BridgeResult toDslRequest(String model, Map<String, Object> plan) {
        List<String> unsupported = new ArrayList<>();
        if (plan == null || !"MAPPED".equals(plan.get("mapping_status"))) {
            unsupported.add("semantic SQL mapper status is not MAPPED");
            return BridgeResult.deferred(unsupported);
        }
        if (Boolean.TRUE.equals(plan.get("requires_declared_relation"))) {
            unsupported.add("declared relation predicates are not executable through DSL bridge v1");
            return BridgeResult.deferred(unsupported);
        }

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL");
        request.setStatus("PLAN_READY");
        request.setColumns(columns(plan, unsupported));
        request.setSlice(sliceItems(plan.get("slice"), unsupported));
        request.setHaving(sliceItems(plan.get("having"), unsupported));
        request.setGroupBy(groupByItems(plan.get("groupBy"), unsupported));
        request.setOrderBy(orderItems(plan.get("orderBy"), unsupported));
        request.setLimit(intValue(plan.get("limit"), unsupported));
        request.setReturnTotal(false);

        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            unsupported.add("DSL bridge request must have at least one output column");
        }

        if (!unsupported.isEmpty()) {
            return BridgeResult.deferred(unsupported);
        }
        return BridgeResult.ready(request);
    }

    @SuppressWarnings("unchecked")
    private static List<String> columns(Map<String, Object> plan, List<String> unsupported) {
        List<String> columns = new ArrayList<>();
        Object rawColumns = plan.get("columns");
        if (rawColumns instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String field) {
                    columns.add(field);
                } else {
                    unsupported.add("non-string projection is not executable through DSL bridge v1: " + item);
                }
            }
        }
        Object rawMetrics = plan.get("metrics");
        if (rawMetrics instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> metric) {
                    columns.add(metricColumn((Map<String, Object>) metric, unsupported));
                } else {
                    unsupported.add("metric entry is not an object: " + item);
                }
            }
        }
        columns.removeIf(String::isBlank);
        return columns;
    }

    private static String metricColumn(Map<String, Object> metric, List<String> unsupported) {
        String agg = stringValue(metric.get("agg"));
        Object fieldObj = metric.get("field");
        String alias = stringValue(metric.get("alias"));
        if (agg == null || fieldObj == null) {
            unsupported.add("metric must declare agg and field: " + metric);
            return "";
        }
        if (!(fieldObj instanceof String field)) {
            unsupported.add("metric field expression is not executable through DSL bridge v1: " + fieldObj);
            return "";
        }
        String function = switch (agg.toUpperCase(Locale.ROOT)) {
            case "COUNT_DISTINCT" -> "COUNT_DISTINCT";
            case "SUM", "COUNT", "AVG", "MIN", "MAX" -> agg.toUpperCase(Locale.ROOT);
            default -> null;
        };
        if (function == null) {
            unsupported.add("unsupported metric agg for DSL bridge v1: " + agg);
            return "";
        }
        String expression = function + "(" + field + ")";
        return alias == null || alias.isBlank() ? expression : expression + " AS " + alias;
    }

    private static List<SemanticQueryRequest.SliceItem> sliceItems(Object raw, List<String> unsupported) {
        if (raw == null) {
            return null;
        }
        SemanticQueryRequest.SliceItem item = sliceItem(raw, unsupported);
        return item == null ? null : List.of(item);
    }

    @SuppressWarnings("unchecked")
    private static SemanticQueryRequest.SliceItem sliceItem(Object raw, List<String> unsupported) {
        if (!(raw instanceof Map<?, ?> map)) {
            unsupported.add("predicate is not an object: " + raw);
            return null;
        }
        Map<String, Object> predicate = (Map<String, Object>) map;
        if (predicate.containsKey("$and")) {
            return logical("$and", predicate.get("$and"), unsupported);
        }
        if (predicate.containsKey("$or")) {
            return logical("$or", predicate.get("$or"), unsupported);
        }
        if (predicate.containsKey("expr")) {
            unsupported.add("expression predicate is not executable through DSL bridge v1: " + predicate.get("expr"));
            return null;
        }
        String field = stringValue(predicate.get("field"));
        String op = stringValue(predicate.get("op"));
        if (field == null || op == null) {
            unsupported.add("predicate must declare field and op: " + predicate);
            return null;
        }
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp(op);
        item.setValue(convertValue(predicate.get("value")));
        return item;
    }

    @SuppressWarnings("unchecked")
    private static SemanticQueryRequest.SliceItem logical(String key, Object rawChildren, List<String> unsupported) {
        if (!(rawChildren instanceof List<?> children)) {
            unsupported.add("logical predicate children must be a list: " + rawChildren);
            return null;
        }
        List<SemanticQueryRequest.SliceItem> mapped = new ArrayList<>();
        for (Object child : children) {
            SemanticQueryRequest.SliceItem item = sliceItem(child, unsupported);
            if (item != null) {
                mapped.add(item);
            }
        }
        SemanticQueryRequest.SliceItem group = new SemanticQueryRequest.SliceItem();
        if ("$and".equals(key)) {
            group.setAnd(mapped);
        } else {
            group.setOr(mapped);
        }
        return group;
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("fieldRef")) {
            Map<String, Object> fieldRef = new LinkedHashMap<>();
            fieldRef.put("$field", map.get("fieldRef"));
            return fieldRef;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SemanticSqlDslRequestMapper::convertValue).toList();
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return value;
    }

    private static List<SemanticQueryRequest.GroupByItem> groupByItems(Object raw, List<String> unsupported) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            unsupported.add("groupBy must be a list: " + raw);
            return null;
        }
        List<SemanticQueryRequest.GroupByItem> groupBy = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String field) {
                groupBy.add(new SemanticQueryRequest.GroupByItem(field, null));
            } else {
                unsupported.add("expression groupBy is not executable through DSL bridge v1: " + item);
            }
        }
        return groupBy;
    }

    @SuppressWarnings("unchecked")
    private static List<SemanticQueryRequest.OrderItem> orderItems(Object raw, List<String> unsupported) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            unsupported.add("orderBy must be a list: " + raw);
            return null;
        }
        List<SemanticQueryRequest.OrderItem> orderBy = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                unsupported.add("orderBy item must be an object: " + item);
                continue;
            }
            Map<String, Object> order = (Map<String, Object>) map;
            if (order.containsKey("expr")) {
                unsupported.add("expression orderBy is not executable through DSL bridge v1: " + order.get("expr"));
                continue;
            }
            String field = stringValue(order.get("field"));
            if (field == null) {
                unsupported.add("orderBy item must declare field: " + order);
                continue;
            }
            SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
            orderItem.setField(field);
            orderItem.setDir(stringValue(order.getOrDefault("dir", "asc")));
            orderBy.add(orderItem);
        }
        return orderBy;
    }

    private static Integer intValue(Object raw, List<String> unsupported) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        unsupported.add("limit must be numeric for DSL bridge v1: " + raw);
        return null;
    }

    private static String stringValue(Object raw) {
        return raw instanceof String value ? value : null;
    }

    public record BridgeResult(String status, SemanticQueryRequest request, List<String> unsupported) {
        static BridgeResult ready(SemanticQueryRequest request) {
            return new BridgeResult(STATUS_READY, request, List.of());
        }

        static BridgeResult deferred(List<String> unsupported) {
            return new BridgeResult(STATUS_DEFERRED, null, List.copyOf(unsupported));
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public void requireReady() {
            if (!ready()) {
                throw RX.throwB("SEMANTIC_SQL_DSL_BRIDGE_NOT_SUPPORTED: " + unsupported);
            }
        }
    }
}
