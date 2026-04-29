package com.foggyframework.dataset.db.model.engine.compose.compilation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * S7f · Structured, validated window function specification.
 *
 * <p>Captures a parsed window expression in a safe, inspectable form:
 * <pre>
 *   FUNC([input]) OVER (
 *       [PARTITION BY col1, col2]
 *       [ORDER BY col3 ASC, col4 DESC]
 *       [frame]
 *   ) AS alias
 * </pre>
 *
 * <p>Immutable. Use {@link WindowSelectParser#parse(String)} to construct.</p>
 *
 * @since 8.5.0.beta (S7f)
 */
public final class WindowSelectSpec {

    /** Supported window function names. */
    public static final java.util.Set<String> RANKING_FUNCTIONS =
            java.util.Set.of("ROW_NUMBER", "RANK", "DENSE_RANK");
    public static final java.util.Set<String> OFFSET_FUNCTIONS =
            java.util.Set.of("LAG", "LEAD");
    public static final java.util.Set<String> AGGREGATE_WINDOW_FUNCTIONS =
            java.util.Set.of("SUM", "AVG", "MIN", "MAX", "COUNT");

    private final String function;
    private final String inputColumn;      // nullable for ranking; "*" for COUNT(*)
    private final List<String> partitionBy;
    private final List<String> orderBy;
    private final String frame;            // nullable
    private final String outputAlias;

    WindowSelectSpec(String function, String inputColumn,
                     List<String> partitionBy, List<String> orderBy,
                     String frame, String outputAlias) {
        if (function == null || function.isEmpty()) {
            throw new IllegalArgumentException(
                    "WindowSelectSpec.function must be non-empty");
        }
        if (outputAlias == null || outputAlias.isEmpty()) {
            throw new IllegalArgumentException(
                    "WindowSelectSpec.outputAlias must be non-empty");
        }
        this.function = function.toUpperCase(java.util.Locale.ROOT);
        this.inputColumn = inputColumn;
        this.partitionBy = partitionBy == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(partitionBy));
        this.orderBy = orderBy == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(orderBy));
        this.frame = frame;
        this.outputAlias = outputAlias;
    }

    /** Window function name (upper case): ROW_NUMBER, RANK, etc. */
    public String function() { return function; }

    /** Input column for aggregate/offset functions; null for ranking; "*" for COUNT(*). */
    public String inputColumn() { return inputColumn; }

    /** PARTITION BY columns (may be empty). */
    public List<String> partitionBy() { return partitionBy; }

    /** ORDER BY clauses (may be empty). Each entry: "colName" or "colName DESC". */
    public List<String> orderBy() { return orderBy; }

    /** Optional frame clause (e.g. "ROWS BETWEEN 2 PRECEDING AND CURRENT ROW"). */
    public String frame() { return frame; }

    /** Output alias for the window column. */
    public String outputAlias() { return outputAlias; }

    /** Whether this is a ranking function (no input column). */
    public boolean isRanking() { return RANKING_FUNCTIONS.contains(function); }

    /** Whether this is an offset function (LAG, LEAD). */
    public boolean isOffset() { return OFFSET_FUNCTIONS.contains(function); }

    /** Whether this is an aggregate window function (SUM, AVG, etc.). */
    public boolean isAggregateWindow() { return AGGREGATE_WINDOW_FUNCTIONS.contains(function); }

    @Override
    public String toString() {
        return "WindowSelectSpec{function=" + function
                + ", inputColumn=" + inputColumn
                + ", partitionBy=" + partitionBy
                + ", orderBy=" + orderBy
                + ", frame=" + frame
                + ", outputAlias=" + outputAlias + "}";
    }
}
