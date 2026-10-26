package com.foggyframework.dataset.model.engine.compose.plan;

import java.util.List;
import java.util.Map;

/**
 * Represents the OVER() clause in a window function.
 * E.g. OVER (PARTITION BY ... ORDER BY ... ROWS BETWEEN ...)
 * 
 * @since 8.3.0.beta
 */
public final class OverClause {
    private final List<String> partitionBy;
    private final List<String> orderBy;
    private final Map<Integer, PlanColumnRef> partitionReferences;
    private final Map<Integer, PlanColumnRef> orderReferences;
    private final WindowFrame windowFrame;  // nullable — when null, no frame clause is rendered

    public OverClause(List<String> partitionBy, List<String> orderBy) {
        this(partitionBy, orderBy, null);
    }

    public OverClause(List<String> partitionBy, List<String> orderBy, WindowFrame windowFrame) {
        this(partitionBy, orderBy, windowFrame, Map.of(), Map.of());
    }

    private OverClause(List<String> partitionBy, List<String> orderBy, WindowFrame windowFrame,
                       Map<Integer, PlanColumnRef> partitionReferences, Map<Integer, PlanColumnRef> orderReferences) {
        this.partitionBy = partitionBy != null ? List.copyOf(partitionBy) : List.of();
        this.orderBy = orderBy != null ? List.copyOf(orderBy) : List.of();
        this.windowFrame = windowFrame;
        this.partitionReferences = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(partitionReferences));
        this.orderReferences = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(orderReferences));
    }

    public List<String> getPartitionBy() {
        return partitionBy;
    }

    public List<String> getOrderBy() {
        return orderBy;
    }

    public WindowFrame getWindowFrame() {
        return windowFrame;
    }

    public Map<Integer, PlanColumnRef> partitionReferences() { return partitionReferences; }
    public Map<Integer, PlanColumnRef> orderReferences() { return orderReferences; }

    private static List<String> extractStringList(Object obj, String key, Map<Integer, PlanColumnRef> references) {
        if (obj == null) return null;
        if (!(obj instanceof List<?> list)) {
            throw com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException.invalidType(
                    "COMPOSE_PARAMETER_TYPE_INVALID", "over." + key, key, "an array or null", obj);
        }
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof PlanColumnRef ref) {
                references.put(names.size(), ref);
                names.add(ref.name());
            } else if (item instanceof String text && !text.isBlank()) {
                names.add(text);
            } else {
                throw com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException.invalidType(
                        "COMPOSE_PARAMETER_TYPE_INVALID", "over." + key + "[" + names.size() + "]", key,
                        "a non-blank field string or PlanColumnRef", item);
            }
        }
        return List.copyOf(names);
    }

    /**
     * Helper to construct from a Map/Dict usually passed from JS sandbox.
     */
    @SuppressWarnings("unchecked")
    public static OverClause fromMap(Map<String, Object> config) {
        if (config == null) {
            return new OverClause(null, null);
        }

        Map<Integer, PlanColumnRef> partitions = new java.util.LinkedHashMap<>();
        Map<Integer, PlanColumnRef> orders = new java.util.LinkedHashMap<>();
        List<String> partitionBy = extractStringList(config.get("partitionBy"), "partitionBy", partitions);
        List<String> orderBy = extractStringList(config.get("orderBy"), "orderBy", orders);
        WindowFrame frame = WindowFrame.fromMapOrString(config.get("windowFrame"));

        return new OverClause(partitionBy, orderBy, frame, partitions, orders);
    }
}
