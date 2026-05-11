package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Plan derived from another plan's output schema.
 *
 * <p>Per spec §3, derived plans are restricted to references visible in
 * {@code source}'s output schema. M2 does not enforce this — M4 does —
 * but the structural carriage is here so M4 has something to validate
 * against.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.plan.DerivedQueryPlan}.</p>
 *
 * @since 8.2.0.beta
 */
public final class DerivedQueryPlan extends QueryPlan {

    private final QueryPlan source;
    private final List<Object> columns;
    private final List<Object> slice;
    private final List<String> groupBy;
    private final List<String> orderBy;
    private final Integer limit;
    private final Integer start;
    private final boolean distinct;

    private DerivedQueryPlan(Builder b) {
        requirePlan(b.source, "DerivedQueryPlan.source");
        PlanQualifiedFieldResolver.NormalizedDerivedOptions normalized =
                PlanQualifiedFieldResolver.normalizeDerivedOptions(
                        b.source, b.columns, b.slice, b.groupBy, b.orderBy);
        // Columns may be null/empty for intermediate fluent stages
        // (where, groupBy, orderBy, limit before select()).
        validateColumnElements(normalized.columns(), "DerivedQueryPlan.columns");
        validateStringList(normalized.groupBy(), "DerivedQueryPlan.groupBy");
        validateStringList(normalized.orderBy(), "DerivedQueryPlan.orderBy");
        validateSliceValues(normalized.slice(), "DerivedQueryPlan.slice");
        validatePagination(b.limit, b.start, "DerivedQueryPlan");
        // G5 Phase 2 (F5) — visibility lineage = source.collectVisiblePlans()
        // (already includes source itself). Self-reference (plan === source)
        // is allowed per spec §5.2.
        validateF5PlanVisibility(normalized.columns(), b.source.collectVisiblePlans(),
                "DerivedQueryPlan.columns");

        this.source = b.source;
        this.columns = normalized.columns() == null ? List.of() : List.copyOf(normalized.columns());
        this.slice = normalized.slice() == null ? List.of() : List.copyOf(normalized.slice());
        this.groupBy = normalized.groupBy() == null ? List.of() : List.copyOf(normalized.groupBy());
        this.orderBy = normalized.orderBy() == null ? List.of() : List.copyOf(normalized.orderBy());
        this.limit = b.limit;
        this.start = b.start;
        this.distinct = b.distinct;
    }

    public QueryPlan source() { return source; }
    public List<Object> columns() { return columns; }
    public List<Object> slice() { return slice; }
    public List<String> groupBy() { return groupBy; }
    public List<String> orderBy() { return orderBy; }
    public Integer limit() { return limit; }
    public Integer start() { return start; }
    public boolean distinct() { return distinct; }

    @Override
    public List<BaseModelPlan> baseModelPlans() {
        List<BaseModelPlan> out = new java.util.ArrayList<>(source.baseModelPlans());
        out.addAll(sliceSubqueryBaseModelPlans(slice));
        return List.copyOf(out);
    }

    @Override
    public Set<QueryPlan> collectVisiblePlans() {
        // Derived = self ∪ source's visible plans (includes source recursively).
        Set<QueryPlan> set = identityPlanSet();
        set.add(this);
        set.addAll(source.collectVisiblePlans());
        return set;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private QueryPlan source;
        private List<Object> columns;
        private List<Object> slice;
        private List<String> groupBy;
        private List<String> orderBy;
        private Integer limit;
        private Integer start;
        private boolean distinct;

        public Builder source(QueryPlan v) { this.source = v; return this; }

        /** Accepts a heterogeneous list of {@link String} or
         *  {@link com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression}
         *  elements. Element types are validated in {@link #build()};
         *  illegal elements fail-closed with {@link IllegalArgumentException}. */
        public Builder columns(List<?> v) {
            this.columns = v == null ? null : new java.util.ArrayList<>(v);
            return this;
        }

        public Builder slice(List<Object> v) { this.slice = v; return this; }
        public Builder groupBy(List<String> v) { this.groupBy = v; return this; }
        public Builder orderBy(List<String> v) { this.orderBy = v; return this; }
        public Builder limit(Integer v) { this.limit = v; return this; }
        public Builder start(Integer v) { this.start = v; return this; }
        public Builder distinct(boolean v) { this.distinct = v; return this; }

        public DerivedQueryPlan build() { return new DerivedQueryPlan(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DerivedQueryPlan)) return false;
        DerivedQueryPlan p = (DerivedQueryPlan) o;
        return distinct == p.distinct
                && Objects.equals(source, p.source)
                && Objects.equals(columns, p.columns)
                && Objects.equals(slice, p.slice)
                && Objects.equals(groupBy, p.groupBy)
                && Objects.equals(orderBy, p.orderBy)
                && Objects.equals(limit, p.limit)
                && Objects.equals(start, p.start);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, columns, slice, groupBy, orderBy,
                limit, start, distinct);
    }

    @Override
    public String toString() {
        return "DerivedQueryPlan{sourceType=" + source.getClass().getSimpleName()
                + ", columns=" + columns
                + ", sliceSize=" + slice.size()
                + ", groupBy=" + groupBy
                + ", orderBy=" + orderBy
                + ", limit=" + limit
                + ", start=" + start
                + ", distinct=" + distinct
                + '}';
    }
}
