package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.List;
import java.util.Map;

/**
 * Represents a declarative time window definition in a SemanticQueryRequest.
 * <p>
 * This is the high-level DSL structure that AI agents fill in to express
 * standard time-series analysis (YoY, MoM, rolling, cumulative) without
 * needing to hand-write SQL window functions.
 * <p>
 * The engine intercepts this definition and expands it into the appropriate
 * QueryPlan AST nodes (WindowColumn, DerivedQueryPlan + JoinPlan, etc.).
 *
 * @since 8.3.0.beta
 * @see WindowFrame
 * @see OverClause
 */
public record TimeWindowDef(
        String field,               // time axis field (business_date), e.g. "salesDate$id" or "orderDate"
        String grain,               // day | week | month | quarter | year
        String comparison,          // yoy | mom | wow | ytd | mtd | rolling_7d | rolling_30d | rolling_90d
        String range,               // "[)" (default) or "[]"
        List<String> value,         // [start, end] — absolute dates or relative expressions
        List<String> targetMetrics, // nullable — measures to apply the window to
        String rollingAggregator    // nullable — sum | avg | count | min | max (defaults to measure's native agg)
) {

    /**
     * Compact constructor: immutable copies + basic port validation.
     */
    public TimeWindowDef {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("timeWindow.field is required");
        }
        if (grain == null || grain.isBlank()) {
            throw new IllegalArgumentException("timeWindow.grain is required");
        }
        if (comparison == null || comparison.isBlank()) {
            throw new IllegalArgumentException("timeWindow.comparison is required");
        }
        range = (range == null || range.isBlank()) ? "[)" : range;
        value = value != null ? List.copyOf(value) : List.of();
        targetMetrics = targetMetrics != null ? List.copyOf(targetMetrics) : null;
        rollingAggregator = rollingAggregator;  // nullable
    }

    // ---- Query helpers ----

    public boolean isComparative() {
        return "yoy".equals(comparison) || "mom".equals(comparison) || "wow".equals(comparison);
    }

    public boolean isCumulative() {
        return "ytd".equals(comparison) || "mtd".equals(comparison);
    }

    public boolean isRolling() {
        return comparison != null && comparison.startsWith("rolling_");
    }

    /**
     * Parse rolling window size from comparison string, e.g. "rolling_7d" → 7.
     * @throws IllegalArgumentException if comparison is not a rolling type.
     */
    public int rollingWindowSize() {
        if (!isRolling()) {
            throw new IllegalArgumentException("Not a rolling comparison: " + comparison);
        }
        // Extract number from "rolling_7d", "rolling_30d", "rolling_90d"
        String numStr = comparison.replaceAll("[^0-9]", "");
        return Integer.parseInt(numStr);
    }

    // ---- Factory from Map (JSON deserialization) ----

    @SuppressWarnings("unchecked")
    public static TimeWindowDef fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new TimeWindowDef(
                (String) map.get("field"),
                (String) map.get("grain"),
                (String) map.get("comparison"),
                (String) map.get("range"),
                map.get("value") instanceof List<?> v ? v.stream().map(String::valueOf).toList() : null,
                map.get("targetMetrics") instanceof List<?> tm ? tm.stream().map(String::valueOf).toList() : null,
                (String) map.get("rollingAggregator")
        );
    }
}
