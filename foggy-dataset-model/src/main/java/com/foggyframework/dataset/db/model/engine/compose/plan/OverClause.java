package com.foggyframework.dataset.db.model.engine.compose.plan;

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
    private final WindowFrame windowFrame;  // nullable — when null, no frame clause is rendered

    public OverClause(List<String> partitionBy, List<String> orderBy) {
        this(partitionBy, orderBy, null);
    }

    public OverClause(List<String> partitionBy, List<String> orderBy, WindowFrame windowFrame) {
        this.partitionBy = partitionBy != null ? List.copyOf(partitionBy) : List.of();
        this.orderBy = orderBy != null ? List.copyOf(orderBy) : List.of();
        this.windowFrame = windowFrame;
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

    private static List<String> extractStringList(Object obj) {
        if (!(obj instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(item -> {
            if (item instanceof PlanColumnRef ref) {
                return ref.name();
            }
            return String.valueOf(item);
        }).toList();
    }

    /**
     * Helper to construct from a Map/Dict usually passed from JS sandbox.
     */
    @SuppressWarnings("unchecked")
    public static OverClause fromMap(Map<String, Object> config) {
        if (config == null) {
            return new OverClause(null, null);
        }

        List<String> partitionBy = extractStringList(config.get("partitionBy"));
        List<String> orderBy = extractStringList(config.get("orderBy"));
        WindowFrame frame = WindowFrame.fromMapOrString(config.get("windowFrame"));

        return new OverClause(partitionBy, orderBy, frame);
    }
}
