package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
        this.on = List.copyOf(b.on);
    }

    public QueryPlan left() { return left; }
    public QueryPlan right() { return right; }
    public JoinType type() { return type; }
    public List<JoinOn> on() { return on; }

    @Override
    List<BaseModelPlan> baseModelPlans() {
        List<BaseModelPlan> out = new ArrayList<>();
        out.addAll(left.baseModelPlans());
        out.addAll(right.baseModelPlans());
        return Collections.unmodifiableList(out);
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
