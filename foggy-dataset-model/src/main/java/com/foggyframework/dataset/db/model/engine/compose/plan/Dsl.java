package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical Compose Query DSL entry point.
 *
 * <p>The Python side exposes this as the module-level function
 * {@code from_(...)} (the trailing underscore avoids the {@code from}
 * keyword clash). JavaScript exposes it as the global function
 * {@code from(...)}. The Java side exposes it as {@link Dsl#from(FromOptions)}
 * — no keyword conflict on this side.</p>
 *
 * <p><b>Call shapes.</b>
 * <ul>
 *   <li>Base-model leaf:
 *       {@code Dsl.from(FromOptions.builder().model("SaleOrderQM").columns(List.of(...)).build())}</li>
 *   <li>Kernel derived (equivalent to {@code plan.query(...)}):
 *       {@code Dsl.from(FromOptions.builder().source(plan).columns(List.of(...)).build())}</li>
 * </ul>
 * {@code model} and {@code source} are mutually exclusive — passing both or
 * neither raises {@link IllegalArgumentException}.</p>
 *
 * <p>{@code columns} is a heterogeneous list of {@link String} or
 * {@link com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression};
 * element types are validated by the receiving plan's {@code build()}.</p>
 *
 * @since 8.2.0.beta
 */
public final class Dsl {

    private Dsl() {
        // Non-instantiable utility.
    }

    /**
     * Build a {@link BaseModelPlan} (when {@code opts.model()} is given) or
     * a {@link DerivedQueryPlan} (when {@code opts.source()} is given).
     *
     * @throws IllegalArgumentException when {@code model} and {@code source}
     *         are both set or both unset, or when any shape validation
     *         (columns non-empty, limit/start non-negative, …) fails.
     */
    public static QueryPlan from(FromOptions opts) {
        if (opts == null) {
            throw new IllegalArgumentException(
                    "Dsl.from(opts): opts must not be null");
        }
        String model = opts.model();
        QueryPlan source = opts.source();
        boolean hasModel = model != null;
        boolean hasSource = source != null;

        if (hasModel && hasSource) {
            throw new IllegalArgumentException(
                    "Dsl.from() accepts either model= (base model) or "
                            + "source= (derived), not both");
        }
        if (!hasModel && !hasSource) {
            throw new IllegalArgumentException(
                    "Dsl.from() requires exactly one of model= or source=");
        }
        // Legacy Dsl.from() always requires columns (the OO API specifies columns via .select())
        if (opts.columns() == null || opts.columns().isEmpty()) {
            throw new IllegalArgumentException(
                    "Dsl.from() requires non-empty columns list");
        }

        if (hasModel) {
            if (model.isEmpty()) {
                throw new IllegalArgumentException(
                        "Dsl.from(model=...) must be a non-empty string");
            }
            return BaseModelPlan.builder()
                    .model(model)
                    .columns(opts.columns())
                    .slice(opts.slice())
                    .groupBy(opts.groupBy())
                    .orderBy(opts.orderBy())
                    .calculatedFields(opts.calculatedFields())
                    .limit(opts.limit())
                    .start(opts.start())
                    .distinct(opts.distinct())
                    .build();
        }

        return DerivedQueryPlan.builder()
                .source(source)
                .columns(opts.columns())
                .slice(opts.slice())
                .groupBy(opts.groupBy())
                .orderBy(opts.orderBy())
                .limit(opts.limit())
                .start(opts.start())
                .distinct(opts.distinct())
                .build();
    }

    /**
     * Options carrier for {@link #from(FromOptions)}.
     *
     * <p>Python {@code from_()} uses keyword-only parameters; Java has no
     * keyword arguments, so a Builder stands in. Each optional field
     * defaults to its Python-equivalent zero value (empty list / null /
     * false).</p>
     */
    public static final class FromOptions {

        private final String model;
        private final QueryPlan source;
        private final List<Object> columns;
        private final List<Object> slice;
        private final List<String> groupBy;
        private final List<String> orderBy;
        private final List<CalculatedFieldDef> calculatedFields;
        private final Integer limit;
        private final Integer start;
        private final boolean distinct;

        private FromOptions(Builder b) {
            this.model = b.model;
            this.source = b.source;
            this.columns = b.columns;
            this.slice = b.slice;
            this.groupBy = b.groupBy;
            this.orderBy = b.orderBy;
            this.calculatedFields = b.calculatedFields;
            this.limit = b.limit;
            this.start = b.start;
            this.distinct = b.distinct;
        }

        public String model() { return model; }
        public QueryPlan source() { return source; }
        public List<Object> columns() { return columns; }
        public List<Object> slice() { return slice; }
        public List<String> groupBy() { return groupBy; }
        public List<String> orderBy() { return orderBy; }
        public List<CalculatedFieldDef> calculatedFields() { return calculatedFields; }
        public Integer limit() { return limit; }
        public Integer start() { return start; }
        public boolean distinct() { return distinct; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String model;
            private QueryPlan source;
            private List<Object> columns;
            private List<Object> slice;
            private List<String> groupBy;
            private List<String> orderBy;
            private List<CalculatedFieldDef> calculatedFields;
            private Integer limit;
            private Integer start;
            private boolean distinct;

            public Builder model(String v) { this.model = v; return this; }
            public Builder source(QueryPlan v) { this.source = v; return this; }

            /** Accepts a heterogeneous list of {@link String} or
             *  {@link com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression}.
             *  Element types are validated by the resulting plan's {@code build()}. */
            public Builder columns(List<?> v) {
                this.columns = v == null ? null : new ArrayList<>(v);
                return this;
            }

            public Builder slice(List<Object> v) { this.slice = v; return this; }
            public Builder groupBy(List<String> v) { this.groupBy = v; return this; }
            public Builder orderBy(List<String> v) { this.orderBy = v; return this; }
            public Builder calculatedFields(List<CalculatedFieldDef> v) { this.calculatedFields = v; return this; }
            public Builder limit(Integer v) { this.limit = v; return this; }
            public Builder start(Integer v) { this.start = v; return this; }
            public Builder distinct(boolean v) { this.distinct = v; return this; }

            public FromOptions build() { return new FromOptions(this); }
        }
    }
}
