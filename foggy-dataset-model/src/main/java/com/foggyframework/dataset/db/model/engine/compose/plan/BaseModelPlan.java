package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Leaf plan node pointing at a physical QM (query-model).
 *
 * <p>Authority binding (M5) resolves per {@code BaseModelPlan} — that is
 * why the same QM referenced twice in a script materialises as two
 * distinct {@code BaseModelPlan} values, each with its own authority
 * lifecycle.</p>
 *
 * <p>Cross-repo invariant: field names mirror Python
 * {@code BaseModelPlan} (only casing: {@code group_by} → {@code groupBy}).</p>
 *
 * @since 8.2.0.beta
 */
public final class BaseModelPlan extends QueryPlan {

    private final String model;
    private final List<Object> columns;
    private final List<Object> slice;
    private final List<Object> having;
    private final List<String> groupBy;
    private final List<String> orderBy;
    private final List<CalculatedFieldDef> calculatedFields;
    private final Integer limit;
    private final Integer start;
    private final boolean distinct;

    private BaseModelPlan(Builder b) {
        if (b.model == null || b.model.isEmpty()) {
            throw new IllegalArgumentException("BaseModelPlan.model must be non-empty");
        }
        validateColumnElements(b.columns, "BaseModelPlan.columns");
        validateStringList(b.groupBy, "BaseModelPlan.groupBy");
        validateStringList(b.orderBy, "BaseModelPlan.orderBy");
        validateSliceValues(b.slice, "BaseModelPlan.slice");
        validateSliceValues(b.having, "BaseModelPlan.having");
        validatePagination(b.limit, b.start, "BaseModelPlan");
        // G5 Phase 2 (F5) — base plans have no lineage children and the
        // plan-being-built does not yet exist, so any F5 plan-qualified
        // column is by definition out-of-scope. Pass an empty visiblePlans
        // set so the helper rejects every PlanColumnRef with
        // COLUMN_PLAN_NOT_VISIBLE.
        validateF5PlanVisibility(b.columns, identityPlanSet(), "BaseModelPlan.columns");

        this.model = b.model;
        this.columns = b.columns == null ? List.of() : List.copyOf(b.columns);
        this.slice = b.slice == null ? List.of() : List.copyOf(b.slice);
        this.having = b.having == null ? List.of() : List.copyOf(b.having);
        this.groupBy = b.groupBy == null ? List.of() : List.copyOf(b.groupBy);
        this.orderBy = b.orderBy == null ? List.of() : List.copyOf(b.orderBy);
        this.calculatedFields = b.calculatedFields == null ? List.of() : List.copyOf(b.calculatedFields);
        this.limit = b.limit;
        this.start = b.start;
        this.distinct = b.distinct;
    }

    public String model() { return model; }
    public List<Object> columns() { return columns; }
    public List<Object> slice() { return slice; }
    public List<Object> having() { return having; }
    public List<String> groupBy() { return groupBy; }
    public List<String> orderBy() { return orderBy; }
    public List<CalculatedFieldDef> calculatedFields() { return calculatedFields; }
    public Integer limit() { return limit; }
    public Integer start() { return start; }
    public boolean distinct() { return distinct; }

    @Override
    public List<BaseModelPlan> baseModelPlans() {
        List<BaseModelPlan> out = new java.util.ArrayList<>();
        out.add(this);
        out.addAll(sliceSubqueryBaseModelPlans(slice));
        out.addAll(sliceSubqueryBaseModelPlans(having));
        return List.copyOf(out);
    }

    @Override
    public Set<QueryPlan> collectVisiblePlans() {
        // BaseModelPlan is a leaf — visible set is just {this} (identity-keyed).
        Set<QueryPlan> set = identityPlanSet();
        set.add(this);
        return set;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String model;
        private List<Object> columns;
        private List<Object> slice;
        private List<Object> having;
        private List<String> groupBy;
        private List<String> orderBy;
        private List<CalculatedFieldDef> calculatedFields;
        private Integer limit;
        private Integer start;
        private boolean distinct;

        public Builder model(String v) { this.model = v; return this; }

        /** Accepts a heterogeneous list of {@link String} or
         *  {@link com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression}
         *  elements. Element types are validated in {@link #build()};
         *  illegal elements fail-closed with {@link IllegalArgumentException}. */
        public Builder columns(List<?> v) {
            this.columns = v == null ? null : new java.util.ArrayList<>(v);
            return this;
        }

        public Builder slice(List<Object> v) { this.slice = v; return this; }
        public Builder having(List<Object> v) { this.having = v; return this; }
        public Builder groupBy(List<String> v) { this.groupBy = v; return this; }
        public Builder orderBy(List<String> v) { this.orderBy = v; return this; }
        public Builder calculatedFields(List<CalculatedFieldDef> v) { this.calculatedFields = v; return this; }
        public Builder limit(Integer v) { this.limit = v; return this; }
        public Builder start(Integer v) { this.start = v; return this; }
        public Builder distinct(boolean v) { this.distinct = v; return this; }

        public BaseModelPlan build() { return new BaseModelPlan(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseModelPlan)) return false;
        BaseModelPlan p = (BaseModelPlan) o;
        return distinct == p.distinct
                && Objects.equals(model, p.model)
                && Objects.equals(columns, p.columns)
                && Objects.equals(slice, p.slice)
                && Objects.equals(having, p.having)
                && Objects.equals(groupBy, p.groupBy)
                && Objects.equals(orderBy, p.orderBy)
                && Objects.equals(calculatedFields, p.calculatedFields)
                && Objects.equals(limit, p.limit)
                && Objects.equals(start, p.start);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, columns, slice, having, groupBy, orderBy,
                calculatedFields, limit, start, distinct);
    }

    @Override
    public String toString() {
        return "BaseModelPlan{model=" + model
                + ", columns=" + columns
                + ", sliceSize=" + slice.size()
                + ", havingSize=" + having.size()
                + ", groupBy=" + groupBy
                + ", orderBy=" + orderBy
                + ", calculatedFields=" + calculatedFields
                + ", limit=" + limit
                + ", start=" + start
                + ", distinct=" + distinct
                + '}';
    }
}
