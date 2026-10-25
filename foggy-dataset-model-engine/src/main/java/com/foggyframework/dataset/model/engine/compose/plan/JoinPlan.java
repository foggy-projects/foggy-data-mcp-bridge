package com.foggyframework.dataset.model.engine.compose.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Relational join of two plans.
 *
 * <p>Same-datasource constraint (cross-datasource join rejected by the M6
 * compiler — not enforced in M2). {@link #on()} is a non-empty list of
 * {@link JoinOn} predicates; {@link #type()} is one of
 * {@link JoinType#INNER}, {@link JoinType#LEFT}, {@link JoinType#RIGHT},
 * {@link JoinType#FULL}.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.plan.JoinPlan}. The Python
 * side stores {@code type} as a lower-case string; the Java side stores a
 * {@link JoinType} enum. The wire-level normalisation is
 * {@link JoinType#token()} ↔ {@link JoinType#fromString(String)}.</p>
 *
 * @since 8.2.0.beta
 */
public final class JoinPlan extends QueryPlan {

    private final QueryPlan left;
    private final QueryPlan right;
    private final JoinType type;
    private final List<JoinOn> on;

    private JoinPlan(Builder b) {
        requirePlan(b.left, "JoinPlan.left");
        requirePlan(b.right, "JoinPlan.right");
        if (b.type == null) {
            throw new IllegalArgumentException(
                    "JoinPlan.type must not be null; use one of "
                            + "JoinType.{INNER, LEFT, RIGHT, FULL}");
        }
        if (b.on == null || b.on.isEmpty()) {
            throw new IllegalArgumentException(
                    "JoinPlan.on must be non-empty; cross joins are not "
                            + "supported in 8.2.0.beta M2");
        }
        for (int i = 0; i < b.on.size(); i++) {
            JoinOn c = b.on.get(i);
            if (c == null) {
                throw new IllegalArgumentException(
                        "JoinPlan.on[" + i + "] must not be null");
            }
        }

        this.left = b.left;
        this.right = b.right;
        this.type = b.type;
        this.on = List.copyOf(PlanQualifiedFieldResolver.normalizeJoinOn(left, right, b.on));
    }

    public QueryPlan left() { return left; }
    public QueryPlan right() { return right; }
    public JoinType type() { return type; }
    public List<JoinOn> on() { return on; }

    /** Bind typed conditions against actual visible outputs, preserving UNION opacity. */
    public List<JoinOn> resolvedOn(java.util.function.Function<QueryPlan, List<String>> outputs) {
        List<JoinOn> resolved = new ArrayList<>();
        for (int i = 0; i < on.size(); i++) {
            JoinOn condition = on.get(i);
            String l = resolveKey(left, condition.leftReference(), condition.left(), outputs, i, "left");
            String r = resolveKey(right, condition.rightReference(), condition.right(), outputs, i, "right");
            resolved.add(JoinOn.builder().left(l).op(condition.op()).right(r).nullSafe(condition.nullSafe()).build());
        }
        return resolved;
    }

    private static String resolveKey(QueryPlan source, PlanColumnRef ref, String fallback,
                                     java.util.function.Function<QueryPlan, List<String>> outputs, int index, String side) {
        if (ref == null) return fallback;
        String field = ref.plan() == null ? null : PlanFieldOwnership.visibleField(source, ref.plan(), ref.name(), outputs);
        if (field == null) throw new com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaException(
                ("left".equals(side) ? com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaErrorCodes.JOIN_ON_LEFT_UNKNOWN_FIELD : com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaErrorCodes.JOIN_ON_RIGHT_UNKNOWN_FIELD),
                "Typed JOIN field '" + ref.name() + "' is not visible from its " + side + " input",
                com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                "JoinPlan", ref.name()).withDetails(java.util.Map.of("path", "join.on[" + index + "]." + side,
                        "candidates", outputs.apply(source)));
        return field;
    }

    /**
     * Append an additional join condition (AND predicate).
     * Returns a new JoinPlan with the extra condition appended.
     *
     * <p>Usage in fsscript:
     * <pre>{@code
     * const joined = customers.leftJoin(orders)
     *     .on(customers.id, orders.partnerId)
     *     .and(customers.companyId, orders.companyId);
     * }</pre>
     */
    public JoinPlan and(PlanColumnRef leftCol, PlanColumnRef rightCol) {
        List<JoinOn> extended = new ArrayList<>(this.on);
        extended.add(JoinOn.ofReferences(leftCol, rightCol));
        return JoinPlan.builder()
                .left(this.left)
                .right(this.right)
                .type(this.type)
                .on(extended)
                .build();
    }

    @Override
    public List<BaseModelPlan> baseModelPlans() {
        List<BaseModelPlan> out = new ArrayList<>();
        out.addAll(left.baseModelPlans());
        out.addAll(right.baseModelPlans());
        return Collections.unmodifiableList(out);
    }

    @Override
    public Set<QueryPlan> collectVisiblePlans() {
        // Join = self ∪ left.visible ∪ right.visible (each branch includes its own subtree).
        Set<QueryPlan> set = identityPlanSet();
        set.add(this);
        set.addAll(left.collectVisiblePlans());
        set.addAll(right.collectVisiblePlans());
        return set;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private QueryPlan left;
        private QueryPlan right;
        private JoinType type;
        private List<JoinOn> on;

        public Builder left(QueryPlan v) { this.left = v; return this; }
        public Builder right(QueryPlan v) { this.right = v; return this; }
        public Builder type(JoinType v) { this.type = v; return this; }
        /** Convenience: accept a case-insensitive token; normalised via
         *  {@link JoinType#fromString(String)}. */
        public Builder type(String v) {
            this.type = JoinType.fromString(v);
            return this;
        }
        public Builder on(List<JoinOn> v) { this.on = v; return this; }

        public JoinPlan build() { return new JoinPlan(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JoinPlan)) return false;
        JoinPlan p = (JoinPlan) o;
        return type == p.type
                && Objects.equals(left, p.left)
                && Objects.equals(right, p.right)
                && Objects.equals(on, p.on);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, type, on);
    }

    @Override
    public String toString() {
        return "JoinPlan{leftType=" + left.getClass().getSimpleName()
                + ", rightType=" + right.getClass().getSimpleName()
                + ", type=" + type
                + ", onSize=" + on.size() + '}';
    }
}
