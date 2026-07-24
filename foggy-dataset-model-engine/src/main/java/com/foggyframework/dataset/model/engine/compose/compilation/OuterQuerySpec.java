package com.foggyframework.dataset.model.engine.compose.compilation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * S7d · Immutable specification for an outer query over a
 * {@link com.foggyframework.dataset.model.engine.compose.relation.CompiledRelation}.
 *
 * <p>Controls which columns to select, ordering, filtering, and
 * pagination. Every referenced column is validated against the
 * relation's {@code OutputSchema} and {@code referencePolicy}
 * by {@link RelationOuterQueryBuilder}.</p>
 *
 * @since 8.5.0.beta (S7d)
 */
public final class OuterQuerySpec {

    private final List<String> selectColumns;   // nullable → select all readable
    private final List<String> groupBy;         // nullable → no grouping
    private final List<String> orderBy;         // nullable → no ordering
    private final String filter;                // nullable → no WHERE
    private final List<Object> filterParams;    // nullable → no bind params
    private final Set<String> filterColumns;    // declared column names referenced in filter
    private final Integer limit;                // nullable → no LIMIT
    private final Integer offset;               // nullable → no OFFSET

    private OuterQuerySpec(Builder b) {
        this.selectColumns = b.selectColumns == null ? null
                : Collections.unmodifiableList(new ArrayList<>(b.selectColumns));
        this.groupBy = b.groupBy == null ? null
                : Collections.unmodifiableList(new ArrayList<>(b.groupBy));
        this.orderBy = b.orderBy == null ? null
                : Collections.unmodifiableList(new ArrayList<>(b.orderBy));
        this.filter = b.filter;
        this.filterParams = b.filterParams == null ? null
                : Collections.unmodifiableList(new ArrayList<>(b.filterParams));
        this.filterColumns = b.filterColumns == null ? null
                : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(b.filterColumns));
        this.limit = b.limit;
        this.offset = b.offset;
    }

    /** Columns to SELECT. {@code null} = select all readable columns. */
    public List<String> selectColumns() { return selectColumns; }

    /** GROUP BY columns. Format: relation output column names. */
    public List<String> groupBy() { return groupBy; }

    /** ORDER BY clauses. Format: {@code "colName"} or {@code "colName DESC"}.
     *  {@code null} = no ordering. */
    public List<String> orderBy() { return orderBy; }

    /** Raw WHERE condition. {@code null} = no filter. */
    public String filter() { return filter; }

    /** Bind params for the filter. */
    public List<Object> filterParams() { return filterParams; }

    /** Declared column names referenced by {@link #filter()}.
     *  Required for referencePolicy validation when filter is non-null. */
    public Set<String> filterColumns() { return filterColumns; }

    /** Row limit. {@code null} = no limit. */
    public Integer limit() { return limit; }

    /** Row offset. {@code null} = no offset. */
    public Integer offset() { return offset; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<String> selectColumns;
        private List<String> groupBy;
        private List<String> orderBy;
        private String filter;
        private List<Object> filterParams;
        private Set<String> filterColumns;
        private Integer limit;
        private Integer offset;

        public Builder selectColumns(List<String> v) { this.selectColumns = v; return this; }
        public Builder groupBy(List<String> v) { this.groupBy = v; return this; }
        public Builder orderBy(List<String> v) { this.orderBy = v; return this; }
        public Builder filter(String v) { this.filter = v; return this; }
        public Builder filterParams(List<Object> v) { this.filterParams = v; return this; }
        public Builder filterColumns(Set<String> v) { this.filterColumns = v; return this; }
        public Builder limit(Integer v) { this.limit = v; return this; }
        public Builder offset(Integer v) { this.offset = v; return this; }

        public OuterQuerySpec build() { return new OuterQuerySpec(this); }
    }
}
