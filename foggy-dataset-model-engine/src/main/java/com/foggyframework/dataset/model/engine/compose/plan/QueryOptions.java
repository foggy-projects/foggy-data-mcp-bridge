package com.foggyframework.dataset.model.engine.compose.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyword-argument carrier for {@link QueryPlan#query(QueryOptions)} and the
 * Python-equivalent of {@code from_(source=..., columns=..., ...)}.
 *
 * <p>Python exposes these as keyword-only parameters. Java has no keyword
 * arguments, so we use a Builder — the contract and defaults are identical.
 * All fields except {@code columns} are optional; {@code columns} must be
 * a non-empty list of {@link String} or
 * {@link com.foggyframework.dataset.model.engine.compose.plan.expr.PlanExpression}.</p>
 *
 * <p>This carrier intentionally does NOT include {@code model} or
 * {@code source}; those are exclusive to the {@link Dsl.FromOptions} shape
 * which {@link Dsl#from(Dsl.FromOptions)} consumes.</p>
 *
 * @since 8.2.0.beta
 */
public final class QueryOptions {

    private final List<Object> columns;
    private final List<Object> slice;
    private final List<String> groupBy;
    private final List<String> orderBy;
    private final Integer limit;
    private final Integer start;
    private final boolean distinct;

    private QueryOptions(Builder b) {
        if (b.columns == null) {
            throw new IllegalArgumentException(
                    "QueryOptions.columns must not be null");
        }
        this.columns = List.copyOf(b.columns);
        this.slice = b.slice == null ? List.of() : List.copyOf(b.slice);
        this.groupBy = b.groupBy == null ? List.of() : List.copyOf(b.groupBy);
        this.orderBy = b.orderBy == null ? List.of() : List.copyOf(b.orderBy);
        this.limit = b.limit;
        this.start = b.start;
        this.distinct = b.distinct;
    }

    public List<Object> columns() { return columns; }
    public List<Object> slice() { return slice; }
    public List<String> groupBy() { return groupBy; }
    public List<String> orderBy() { return orderBy; }
    public Integer limit() { return limit; }
    public Integer start() { return start; }
    public boolean distinct() { return distinct; }

    public static Builder builder() { return new Builder(); }

    /** Convenience factory — just columns, all other fields default. */
    public static QueryOptions of(List<?> columns) {
        return builder().columns(columns).build();
    }

    public static final class Builder {
        private List<Object> columns;
        private List<Object> slice;
        private List<String> groupBy;
        private List<String> orderBy;
        private Integer limit;
        private Integer start;
        private boolean distinct;

        /** Accepts a heterogeneous list of {@link String} or
         *  {@link com.foggyframework.dataset.model.engine.compose.plan.expr.PlanExpression}.
         *  Element types are validated when consumed by {@link QueryPlan#query(QueryOptions)}. */
        public Builder columns(List<?> v) {
            this.columns = v == null ? null : new ArrayList<>(v);
            return this;
        }

        public Builder slice(List<Object> v) { this.slice = v; return this; }
        public Builder groupBy(List<String> v) { this.groupBy = v; return this; }
        public Builder orderBy(List<String> v) { this.orderBy = v; return this; }
        public Builder limit(Integer v) { this.limit = v; return this; }
        public Builder start(Integer v) { this.start = v; return this; }
        public Builder distinct(boolean v) { this.distinct = v; return this; }

        public QueryOptions build() { return new QueryOptions(this); }
    }
}
